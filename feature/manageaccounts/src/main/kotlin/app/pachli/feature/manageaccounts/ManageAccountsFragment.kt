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

package app.pachli.feature.manageaccounts

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.navigation.IntentRouterActivityIntent
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.ui.AlertSuspendDialogFragment
import app.pachli.feature.manageaccounts.databinding.FragmentManageAccountsBinding
import com.bumptech.glide.Glide
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Function that receives a [UiAction] and acts on it. */
internal typealias onUiAction = (UiAction) -> Unit

/**
 * Show the user a list of suggested accounts, and allow them to dismiss or follow
 * the suggestion.
 */
@AndroidEntryPoint
class ManageAccountsFragment :
    Fragment(R.layout.fragment_manage_accounts),
    MenuProvider {
    private val viewModel: ManageAccountsViewModel by viewModels()

    private val binding by viewBinding(FragmentManageAccountsBinding::bind)

    private lateinit var pachliAccountAdapter: PachliAccountAdapter

    internal var talkBackWasEnabled = false

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emits them in to [uiAction]. */
    private val accept: onUiAction = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    private val glide by unsafeLazy { Glide.with(this) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        pachliAccountAdapter = PachliAccountAdapter(
            glide,
            animateAvatars = viewModel.uiState.value.animateAvatars,
            animateEmojis = viewModel.uiState.value.animateEmojis,
            showBotOverlay = viewModel.uiState.value.showBotOverlay,
            accept = accept,
        )

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = pachliAccountAdapter
            addItemDecoration(MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
            setAccessibilityDelegateCompat(PachliAccountAccessibilityDelegate(this, accept))
        }

        bind()
    }

    /** Binds data to the UI */
    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiState.collectLatest(::bindUiState) }
                launch { uiAction.throttleFirst().collect(::bindUiAction) }
                launch { viewModel.pachliAccounts.collectLatest(::bindPachliAccounts) }
            }
        }
    }

    /** Update the adapter if [UiState] information changes. */
    private fun bindUiState(uiState: UiState) {
        pachliAccountAdapter.animateEmojis = uiState.animateEmojis
        pachliAccountAdapter.animateAvatars = uiState.animateAvatars
        pachliAccountAdapter.showBotOverlay = uiState.showBotOverlay
    }

    /** Processes [UiAction]. */
    private fun bindUiAction(uiAction: UiAction) {
        when (uiAction) {
            UiAction.AddAccount -> addAccount()
            is UiAction.Logout -> logout(uiAction.pachliAccount)
            is UiAction.Switch -> switchAccount(uiAction.pachliAccount.id)
        }
    }

    /**
     * Routes to [LoginActivity] to add an account.
     */
    private fun addAccount() {
        startActivityWithDefaultTransition(
            LoginActivityIntent(requireActivity(), LoginMode.AdditionalLogin),
        )
    }

    /**
     * Confirms the user wants to log out [pachliAccount], then routes to
     * [IntentRouterActivity] to log out.
     */
    private fun logout(pachliAccount: PachliAccount) {
        lifecycleScope.launch {
            val button = AlertSuspendDialogFragment.newInstance(
                title = getString(
                    app.pachli.core.ui.R.string.title_logout_fmt,
                    pachliAccount.entity.fullName,
                ),
                message = getString(
                    app.pachli.core.ui.R.string.action_logout_confirm,
                    pachliAccount.entity.fullName,
                ),
                positiveText = getString(android.R.string.ok),
                negativeText = getString(android.R.string.cancel),
            ).await(parentFragmentManager)

            if (button == AlertDialog.BUTTON_POSITIVE) {
                val intent = IntentRouterActivityIntent.logout(requireContext(), pachliAccount.id)
                val options = Bundle().apply { putInt("android.activity.splashScreenStyle", 1) }
                startActivity(intent, options)
                requireActivity().finish()
            }
        }
    }

    /** Routes to [IntentRouterActivity] to switch to [pachliAccountId]. */
    private fun switchAccount(pachliAccountId: Long) {
        val intent = IntentRouterActivityIntent.startMainActivity(requireContext(), pachliAccountId)
        val options = Bundle().apply { putInt("android.activity.splashScreenStyle", 1) }
        startActivity(intent, options)
        requireActivity().finish()
    }

    /** Bind local accounts to the UI. */
    private fun bindPachliAccounts(result: List<PachliAccount>) {
        pachliAccountAdapter.submitList(result)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_manage_accounts, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_add_account -> {
                accept(UiAction.AddAccount)
                true
            }

            else -> false
        }
    }

    override fun onResume() {
        super.onResume()

        val a11yManager = ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)
        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        if (talkBackWasEnabled && !wasEnabled) {
            pachliAccountAdapter.notifyItemRangeChanged(0, pachliAccountAdapter.itemCount)
        }
    }

    companion object {
        fun newInstance(): ManageAccountsFragment {
            return ManageAccountsFragment()
        }
    }
}
