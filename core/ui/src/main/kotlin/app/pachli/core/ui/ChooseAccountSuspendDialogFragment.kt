/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.designsystem.R as DR
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.ChooseAccountSuspendDialogFragment.Companion.newInstance
import app.pachli.core.ui.databinding.ItemAutocompleteAccountBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Do not create this fragment directly, use
 * [newInstance][ChooseAccountSuspendDialogFragment.Companion.newInstance].
 *
 * @see [ChooseAccountSuspendDialogFragment.Companion].
 */
@AndroidEntryPoint
open class ChooseAccountSuspendDialogFragment : AppCompatDialogFragment(), SuspendDialogResult<PachliAccount?> {
    /**
     * The account the user chose.
     *
     * If the user pressed Back this is null.
     */
    override var result: PachliAccount? = null

    private val viewModel: ChooseAccountViewModel by viewModels()

    protected lateinit var adapter: ChooseAccountAdapter

    /** Text to display in the dialog's title. */
    protected val title by unsafeLazy { requireArguments().getCharSequence(ARG_TITLE) }

    /** True if the active account should be included in the list. */
    private val includeActive by unsafeLazy { requireArguments().getBoolean(ARG_INCLUDE_ACTIVE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ChooseAccountAdapter(
            requireActivity(),
            viewModel.animateAvatars,
            viewModel.animateEmojis,
        )

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.accounts
                    .map { if (includeActive) it else it.filter { !it.entity.isActive } }
                    .filter { it.isNotEmpty() }
                    .collect { accounts ->
                        adapter.clear()
                        // TODO: Sort the accounts by displayname and then username
                        adapter.addAll(accounts)
                    }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireActivity())
            .setTitle(title)
            .setAdapter(adapter) { _: DialogInterface?, index: Int ->
                result = adapter.getItem(index)
                dismiss()
            }.create()
    }

    /**
     * A suspendable dialog showing the available [PachliAccount] accounts.
     *
     * Show the dialog using [SuspendDialogResult.await].
     *
     * Selecting an account immediately returns the selected [PachliAccount] in the
     * [SuspendDialogResult.result] property (there are no Ok or Cancel buttons).
     *
     * Pressing Back returns `null`.
     *
     * Do not create this fragment directly, use [newInstance].
     */
    companion object {
        private const val ARG_TITLE = "app.pachli.ARG_TITLE"
        private const val ARG_INCLUDE_ACTIVE = "app.pachli.ARG_INCLUDE_ACTIVE"

        /**
         * Creates the dialog.
         *
         * @param title Text to show as the dialog's title.
         * @param includeActive True if the active account should be included in the list.
         */
        fun newInstance(title: CharSequence?, includeActive: Boolean) = ChooseAccountSuspendDialogFragment().apply {
            arguments = Bundle().apply {
                putCharSequence(ARG_TITLE, title)
                putBoolean(ARG_INCLUDE_ACTIVE, includeActive)
            }
        }
    }
}

/** Viewmodel for [ChooseAccountSuspendDialogFragment]. */
@HiltViewModel
internal class ChooseAccountViewModel @Inject constructor(
    accountManager: AccountManager,
    sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    /** Available accounts. */
    val accounts = accountManager.pachliAccountsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    /** True if avatars should be animated. */
    val animateAvatars = sharedPreferencesRepository.animateAvatars

    /** True if emojis should be animated. */
    val animateEmojis = sharedPreferencesRepository.animateEmojis
}

/**
 * Adapter for [ChooseAccountSuspendDialogFragment].
 *
 * Displays each account with its avatar, name, and username.
 */
class ChooseAccountAdapter(
    context: Context,
    private val animateAvatars: Boolean,
    private val animateEmojis: Boolean,
) : ArrayAdapter<PachliAccount>(context, R.layout.item_autocomplete_account) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemAutocompleteAccountBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemAutocompleteAccountBinding.bind(convertView)
        }

        val account = getItem(position) ?: return binding.root

        binding.username.text = account.entity.fullName
        binding.displayName.text = account.entity.displayName.emojify(account.emojis, binding.displayName, animateEmojis)
        binding.avatarBadge.visibility = View.GONE // We never want to display the bot badge here

        val avatarRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_42dp)
        loadAvatar(account.entity.profilePictureUrl, binding.avatar, avatarRadius, animateAvatars)

        return binding.root
    }
}
