/*
 * Copyright 2024 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.feature.lists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.ListsError
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.setRoles
import app.pachli.core.ui.loadAvatar
import app.pachli.feature.lists.databinding.FragmentAccountsInListBinding
import app.pachli.feature.lists.databinding.ItemAccountInListBinding
import com.bumptech.glide.Glide
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber

private typealias AccountInfo = Pair<TimelineAccount, Boolean>

/**
 * Display the members of a given list with a checkbox to add/remove existing,
 * accounts and search for followed accounts to add them to the list.
 */
@AndroidEntryPoint
class AccountsInListFragment : AppCompatDialogFragment() {
    private val viewModel: AccountsInListViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AccountsInListViewModel.Factory> { factory ->
                factory.create(
                    requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID),
                    requireArguments().getString(ARG_LIST_ID)!!,
                )
            }
        },
    )
    private val binding by viewBinding(FragmentAccountsInListBinding::bind)

    private lateinit var listName: String
    private val adapter = Adapter()
    private val searchAdapter = SearchAdapter()

    private val radius by unsafeLazy { resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp) }

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val animateAvatar by unsafeLazy { sharedPreferencesRepository.animateAvatars }
    private val animateEmojis by unsafeLazy { sharedPreferencesRepository.animateEmojis }

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, DR.style.AppDialogFragmentStyle)
        val args = requireArguments()
        pachliAccountId = args.getLong(ARG_PACHLI_ACCOUNT_ID)
        listName = args.getString(ARG_LIST_NAME)!!
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            // Stretch dialog to the window
            window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_accounts_in_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.accountsRecycler.layoutManager = LinearLayoutManager(view.context)
        binding.accountsRecycler.adapter = adapter

        binding.accountsSearchRecycler.layoutManager = LinearLayoutManager(view.context)
        binding.accountsSearchRecycler.adapter = searchAdapter
        (binding.accountsSearchRecycler.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.accountsInList.collect(::bindAccounts) }
                launch {
                    // Search results update whenever:
                    // a. There is a set of accounts in this list
                    // b. There is a new search result
                    viewModel.accountsInList
                        // Ignore updates where accounts are not loaded
                        .filter { it.mapBoth({ it is Accounts.Loaded }, { false }) }
                        .combine(viewModel.searchResults) { accountsInList, searchResults ->
                            Pair(
                                (accountsInList.get() as? Accounts.Loaded)?.accounts.orEmpty().toSet(),
                                searchResults,
                            )
                        }
                        .collectLatest { (accounts, searchResults) ->
                            bindSearchResults(accounts, searchResults)
                        }
                }

                launch {
                    viewModel.errors.collect {
                        handleError(it)
                    }
                }
            }
        }

        binding.searchView.isSubmitButtonEnabled = true
        binding.searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    viewModel.search(query.orEmpty())
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Close event is not sent so we use this instead
                    if (newText.isNullOrBlank()) {
                        viewModel.search("")
                    }
                    return true
                }
            },
        )
    }

    private fun bindAccounts(accounts: Result<Accounts, ListsError>) {
        accounts.onSuccess {
            binding.messageView.hide()
            if (it is Accounts.Loaded) adapter.submitList(it.accounts)
        }.onFailure {
            binding.messageView.show()
            handleError(it)
        }
    }

    private fun bindSearchResults(accountsInList: Set<TimelineAccount>, searchResults: Result<SearchResults, ApiError>) {
        searchResults.onSuccess { searchState ->
            when (searchState) {
                SearchResults.Empty -> {
                    searchAdapter.submitList(emptyList())
                    binding.accountsSearchRecycler.hide()
                    binding.accountsRecycler.show()
                }
                SearchResults.Loading -> { /* nothing */ }
                is SearchResults.Loaded -> {
                    val newList = searchState.accounts.map { account ->
                        account to accountsInList.contains(account)
                    }
                    searchAdapter.submitList(newList)
                    binding.accountsSearchRecycler.show()
                    binding.accountsRecycler.hide()
                }
            }
        }.onFailure {
            Timber.w(it.throwable, "Error searching for accounts in list")
            handleError(it)
        }
    }

    private fun handleError(error: PachliError) {
        binding.messageView.show()
        binding.messageView.setup(error) {
            binding.messageView.hide()
            viewModel.refresh()
        }
    }

    private fun onRemoveFromList(accountId: String) = viewModel.deleteAccountFromList(accountId)

    private fun onAddToList(account: TimelineAccount) = viewModel.addAccountToList(account)

    private object AccountDiffer : DiffUtil.ItemCallback<TimelineAccount>() {
        override fun areItemsTheSame(oldItem: TimelineAccount, newItem: TimelineAccount): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimelineAccount, newItem: TimelineAccount): Boolean {
            return oldItem == newItem
        }
    }

    inner class Adapter : ListAdapter<TimelineAccount, BindingHolder<ItemAccountInListBinding>>(
        AccountDiffer,
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountInListBinding> {
            val binding = ItemAccountInListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = BindingHolder(binding)

            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) {
                    onRemoveFromList(getItem(holder.bindingAdapterPosition).id)
                }
            }
            binding.checkBox.contentDescription =
                binding.root.context.getString(R.string.action_remove_from_list)

            return holder
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemAccountInListBinding>, position: Int) {
            val account = getItem(position)
            val glide = Glide.with(this@AccountsInListFragment)
            holder.binding.displayName.text = account.name.emojify(
                glide,
                account.emojis,
                holder.binding.displayName,
                animateEmojis,
            )
            holder.binding.username.text = binding.root.context.getString(DR.string.post_username_format, account.username)
            holder.binding.avatarBadge.visible(account.bot)
            holder.binding.checkBox.isChecked = true
            loadAvatar(glide, account.avatar, holder.binding.avatar, radius, animateAvatar)

            holder.binding.roleChipGroup.setRoles(account.roles)
        }
    }

    private object SearchDiffer : DiffUtil.ItemCallback<AccountInfo>() {
        override fun areItemsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem.first.id == newItem.first.id
        }

        override fun areContentsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem == newItem
        }
    }

    inner class SearchAdapter : ListAdapter<AccountInfo, BindingHolder<ItemAccountInListBinding>>(
        SearchDiffer,
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountInListBinding> {
            val binding = ItemAccountInListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = BindingHolder(binding)

            binding.checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                val (account, _) = getItem(holder.bindingAdapterPosition)

                if (isChecked) {
                    onAddToList(account)
                } else {
                    onRemoveFromList(account.id)
                }
            }

            return holder
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemAccountInListBinding>, position: Int) {
            val (account, inAList) = getItem(position)

            val glide = Glide.with(this@AccountsInListFragment)
            holder.binding.displayName.text = account.name.emojify(glide, account.emojis, holder.binding.displayName, animateEmojis)
            holder.binding.username.text = binding.root.context.getString(DR.string.post_username_format, account.username)
            loadAvatar(glide, account.avatar, holder.binding.avatar, radius, animateAvatar)
            holder.binding.avatarBadge.visible(account.bot)

            holder.binding.roleChipGroup.setRoles(account.roles)

            with(holder.binding.checkBox) {
                contentDescription = getString(
                    if (inAList) R.string.action_remove_from_list else R.string.action_add_to_list,
                )
                isChecked = inAList
            }
        }
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
        private const val ARG_LIST_ID = "app.pachli.ARG_LIST_ID"
        private const val ARG_LIST_NAME = "app.pachli.ARG_LIST_NAME"

        @JvmStatic
        fun newInstance(pachliAccountId: Long, listId: String, listName: String): AccountsInListFragment {
            val args = Bundle().apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
                putString(ARG_LIST_ID, listId)
                putString(ARG_LIST_NAME, listName)
            }
            return AccountsInListFragment().apply { arguments = args }
        }
    }
}
