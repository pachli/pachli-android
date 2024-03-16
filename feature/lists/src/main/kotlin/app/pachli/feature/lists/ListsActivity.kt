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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.Lists
import app.pachli.core.data.repository.ListsRepository.Companion.compareByListTitle
import app.pachli.core.navigation.StatusListActivityIntent
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.NetworkError
import app.pachli.core.ui.extensions.await
import app.pachli.feature.lists.databinding.ActivityListsBinding
import app.pachli.feature.lists.databinding.DialogListBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shows all the user's lists, with UI to perform CRUD operations on the lists.
 */
@AndroidEntryPoint
class ListsActivity : BaseActivity(), MenuProvider {
    private val viewModel: ListsViewModel by viewModels()

    private val binding by viewBinding(ActivityListsBinding::inflate)

    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_lists)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.listsRecycler.adapter = adapter
        binding.listsRecycler.layoutManager = LinearLayoutManager(this)
        binding.listsRecycler.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.refresh() }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.addListButton.setOnClickListener {
            lifecycleScope.launch { showListNameDialog(null) }
        }

        addMenuProvider(this)

        lifecycleScope.launch {
            viewModel.lists.collectLatest(::bind)
        }

        lifecycleScope.launch {
            viewModel.errors.collect { error ->
                when (error) {
                    is Error.Create -> showMessage(getString(R.string.error_create_list_fmt, error.title, error.throwable.message))
                    is Error.Delete -> showMessage(getString(R.string.error_delete_list_fmt, error.title, error.throwable.message))
                    is Error.Update -> showMessage(getString(R.string.error_rename_list_fmt, error.title, error.throwable.message))
                }
            }
        }

        lifecycleScope.launch {
            viewModel.operationCount.collectLatest {
                if (it == 0) binding.progressIndicator.hide() else binding.progressIndicator.show()
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_lists, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                refreshContent()
                true
            }

            else -> false
        }
    }

    private fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        viewModel.refresh()
    }

    private suspend fun showListNameDialog(list: MastoList?) {
        val builder = AlertDialog.Builder(this)
        val binding = DialogListBinding.inflate(LayoutInflater.from(builder.context))
        val dialog = builder.setView(binding.root).create()

        // Ensure the soft keyboard opens when the name field has focus
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.setOnShowListener {
            binding.nameText.let { editText ->
                editText.doOnTextChanged { s, _, _, _ ->
                    dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = s?.isNotBlank() == true
                }
                editText.setText(list?.title)
                editText.text?.let { editText.setSelection(it.length) }
            }

            list?.let { list ->
                list.exclusive?.let {
                    binding.exclusiveCheckbox.isChecked = it
                } ?: binding.exclusiveCheckbox.hide()
            }

            binding.repliesPolicyGroup.check(list?.repliesPolicy?.resourceId() ?: UserListRepliesPolicy.LIST.resourceId())

            binding.nameText.requestFocus()
        }

        val result = dialog.await(
            list?.let { R.string.action_rename_list } ?: R.string.action_create_list,
            android.R.string.cancel,
        )

        if (result == AlertDialog.BUTTON_POSITIVE) {
            onPickedDialogName(
                binding.nameText.text.toString(),
                list?.id,
                binding.exclusiveCheckbox.isChecked,
                UserListRepliesPolicy.Companion.from(binding.repliesPolicyGroup.checkedRadioButtonId),
            )
        }
    }

    private suspend fun showListDeleteDialog(list: MastoList) {
        val result = AlertDialog.Builder(this)
            .setMessage(getString(R.string.dialog_delete_list_warning, list.title))
            .create()
            .await(R.string.action_delete_list, android.R.string.cancel)

        if (result == AlertDialog.BUTTON_POSITIVE) viewModel.deleteList(list.id, list.title)
    }

    private fun bind(state: Result<Lists, ApiError>) {
        state.onFailure {
            binding.messageView.show()
            binding.swipeRefreshLayout.isRefreshing = false

            if (it is NetworkError) {
                binding.messageView.setup(app.pachli.core.ui.R.drawable.errorphant_offline, app.pachli.core.ui.R.string.error_network) {
                    viewModel.refresh()
                }
            } else {
                binding.messageView.setup(app.pachli.core.ui.R.drawable.errorphant_error, app.pachli.core.ui.R.string.error_generic) {
                    viewModel.refresh()
                }
            }
        }

        state.onSuccess { lists ->
            when (lists) {
                is Lists.Loaded -> {
                    adapter.submitList(lists.lists.sortedWith(compareByListTitle))
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (lists.lists.isEmpty()) {
                        binding.messageView.show()
                        binding.messageView.setup(
                            app.pachli.core.ui.R.drawable.elephant_friend_empty,
                            app.pachli.core.ui.R.string.message_empty,
                            null,
                        )
                    } else {
                        binding.messageView.hide()
                    }
                }

                Lists.Loading -> {
                    binding.messageView.hide()
                    binding.swipeRefreshLayout.isRefreshing = true
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_INDEFINITE,
        ).show()
    }

    private fun onListSelected(listId: String, listTitle: String) {
        startActivityWithSlideInAnimation(
            StatusListActivityIntent.list(this, listId, listTitle),
        )
    }

    private fun openListSettings(list: MastoList) {
        AccountsInListFragment.newInstance(list.id, list.title).show(supportFragmentManager, null)
    }

    private fun onMore(list: MastoList, view: View) {
        PopupMenu(view.context, view).apply {
            inflate(R.menu.list_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.list_edit -> openListSettings(list)
                    R.id.list_update -> lifecycleScope.launch { showListNameDialog(list) }
                    R.id.list_delete -> lifecycleScope.launch { showListDeleteDialog(list) }
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private object ListsDiffer : DiffUtil.ItemCallback<MastoList>() {
        override fun areItemsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ListsAdapter :
        ListAdapter<MastoList, ListsAdapter.ListViewHolder>(ListsDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            return LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
                .let(this::ListViewHolder)
                .apply {
                    val iconColor = MaterialColors.getColor(nameTextView, android.R.attr.textColorTertiary)
                    val context = nameTextView.context
                    val icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_list).apply {
                        sizeDp = 20
                        colorInt = iconColor
                    }

                    nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                }
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            holder.nameTextView.text = getItem(position).title
        }

        private inner class ListViewHolder(view: View) :
            RecyclerView.ViewHolder(view),
            View.OnClickListener {
            val nameTextView: TextView = view.findViewById(R.id.list_name_textview)
            val moreButton: ImageButton = view.findViewById(R.id.editListButton)

            init {
                view.setOnClickListener(this)
                moreButton.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (v == itemView) {
                    val list = getItem(bindingAdapterPosition)
                    onListSelected(list.id, list.title)
                } else {
                    onMore(getItem(bindingAdapterPosition), v)
                }
            }
        }
    }

    private fun onPickedDialogName(name: String, listId: String?, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) {
        if (listId == null) {
            viewModel.createNewList(name, exclusive, repliesPolicy)
        } else {
            viewModel.updateList(listId, name, exclusive, repliesPolicy)
        }
    }

    /** @return The resource ID of the radio button for this replies policy */
    private fun UserListRepliesPolicy.resourceId() = when (this) {
        UserListRepliesPolicy.FOLLOWED -> R.id.repliesPolicyFollowed
        UserListRepliesPolicy.LIST -> R.id.repliesPolicyList
        UserListRepliesPolicy.NONE -> R.id.repliesPolicyNone
    }

    /**
     * @return A [UserListRepliesPolicy] corresponding to [resourceId], which must
     *     be one of the resource IDs for a replies policy radio button
     * @throws IllegalStateException if an unrecognised [resourceId] is used
     */
    private fun UserListRepliesPolicy.Companion.from(resourceId: Int): UserListRepliesPolicy {
        return when (resourceId) {
            R.id.repliesPolicyFollowed -> UserListRepliesPolicy.FOLLOWED
            R.id.repliesPolicyList -> UserListRepliesPolicy.LIST
            R.id.repliesPolicyNone -> UserListRepliesPolicy.NONE
            else -> throw IllegalStateException("unknown resource id")
        }
    }
}
