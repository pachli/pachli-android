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

package app.pachli.feature.intentrouter

import android.app.Dialog
import android.app.NotificationManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.data.repository.SetActiveAccountError
import app.pachli.core.data.repository.get
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.domain.LogoutUseCase
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.IntentRouterActivityIntent
import app.pachli.core.navigation.IntentRouterActivityIntent.Payload
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.ui.AlertSuspendDialogFragment
import app.pachli.core.ui.ChooseAccountSuspendDialogFragment
import app.pachli.feature.intentrouter.FallibleUiAction.SetActiveAccount
import app.pachli.feature.intentrouter.IntentRouterViewModel.Companion.canHandleMimeType
import app.pachli.feature.intentrouter.databinding.DialogChooseAccountShowErrorBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * Parses the intent, determines the correct account to use, and routes the user
 * to the correct activity.
 *
 * If routing to MainActivity the active account is set and refreshed to ensure
 * the app has up-to-date data. Errors that occur during this process are shown
 * to the user with options for handling them.
 */
@AndroidEntryPoint
class IntentRouterActivity : BaseActivity() {
    @Inject
    lateinit var logout: LogoutUseCase

    private val viewModel: IntentRouterViewModel by viewModels()

    override fun requiresLogin() = false

    private var dismissSplashScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!dismissSplashScreen) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    // Get the first set of loaded accounts (as AccountEntity).
                    viewModel.accounts
                        .map { it.get() }
                        .filterNotNull()
                        .map { it.map { it.entity } }
                        .take(1)
                        .collect { bindAccount(savedInstanceState, it) }
                }

                launch {
                    viewModel.uiResult.collect {
                        it.onSuccess { bindUiSuccess(it) }.onFailure { bindUiError(it) }
                    }
                }
            }
        }
    }

    /**
     * Determine the account to act as and launch the appropriate activity.
     *
     * @param accounts The available accounts.
     */
    private suspend fun bindAccount(savedInstanceState: Bundle?, accounts: List<AccountEntity>) {
        // Only thing to do if there are no accounts is to prompt the user to login.
        if (accounts.isEmpty()) {
            val intent = LoginActivityIntent(this@IntentRouterActivity)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityWithDefaultTransition(intent)
            finish()
            return
        }

        if (savedInstanceState != null) return

        val pachliAccountId: Long = resolvePachliAccountId(intent.pachliAccountId, accounts) ?: run {
            // Requested account does not exist, ask the user to choose
            dismissSplashScreen = true
            val account = ChooseAccountSuspendDialogFragment
                .newInstance(getString(R.string.title_choose_account_dialog), true)
                .await(supportFragmentManager)
            if (account == null) {
                finish()
                return
            }
            account.id
        }

        // Determine the payload. If there is no payload then start MainActivity with
        // the appropriate account.
        val payload = IntentRouterActivityIntent.payload(intent)
            ?: Payload.MainActivity(MainActivityIntent.start(this, pachliAccountId))

        // Determine nextAccount
        // If nextAccount == null, delete account; start Login process
        // else; Mark nextAccount as active; delete previous account

        when (payload) {
            is Payload.Logout -> {
                val accountToLogout = accounts.find { it.id == pachliAccountId }
                if (accountToLogout == null) {
                    // can't happen
                    return
                }
                val nextAccount = accounts.firstOrNull { it.id != pachliAccountId }
                if (nextAccount == null) {
                    logout(accountToLogout)
                        .onSuccess {
                            val intent = LoginActivityIntent(this, LoginMode.Default).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivityWithDefaultTransition(intent)
                            finish()
                        }
                        .onFailure { }
                    return
                }
                viewModel.accept(
                    SetActiveAccount(
                        nextAccount.id,
                        Payload.MainActivity(MainActivityIntent.start(this, nextAccount.id)),
                        logoutAccount = accountToLogout,
                    ),
                )
            }
            is Payload.QuickTile -> {
                dismissSplashScreen = true
                val account = if (accounts.size == 1) {
                    accounts.first()
                } else {
                    ChooseAccountSuspendDialogFragment
                        .newInstance(getString(R.string.action_share_as), true)
                        .await(supportFragmentManager)?.entity
                }
                if (account == null) {
                    finish()
                    return
                }
                launchComposeActivityAndExit(account.id)
            }

            is Payload.NotificationCompose -> {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(payload.notificationTag, payload.notificationId)
                launchComposeActivityAndExit(pachliAccountId, payload.composeOptions)
            }

            Payload.ShareContent -> {
                dismissSplashScreen = true
                // If the intent contains data to share choose the account to share from
                // and start the composer.
                if (canHandleMimeType(intent.type)) {
                    val account = if (accounts.size == 1) {
                        accounts.first()
                    } else {
                        ChooseAccountSuspendDialogFragment
                            .newInstance(getString(R.string.action_share_as), true)
                            .await(supportFragmentManager)?.entity
                    }
                    account?.let { forwardToComposeActivityAndExit(account.id, intent) }
                } else {
                    AlertSuspendDialogFragment.newInstance(
                        title = getString(R.string.title_error_mime_type),
                        message = getString(R.string.error_mime_type_fmt, intent.type),
                        positiveText = null,
                    ).await(supportFragmentManager)
                    finish()
                }
                return
            }

            is Payload.MainActivity -> viewModel.accept(SetActiveAccount(pachliAccountId, payload))
        }
    }

    private suspend fun bindUiSuccess(success: UiSuccess) {
        when (success) {
            is UiSuccess.SetActiveAccount -> {
                success.action.logoutAccount?.let { accountToLogout ->
                    logout(accountToLogout)
                        .onFailure {
                            // TODO: Show an error dialog
                            // Maybe -- there's nothing useful the user can do here. The logout
                            // action isn't sensibly retryable.
                        }
                }

                viewModel.accept(
                    FallibleUiAction.RefreshAccount(
                        success.accountEntity,
                        success.action.payload,
                    ),
                )
            }

            is UiSuccess.RefreshAccount -> {
                val payload = success.action.payload
                val intent = payload.mainActivityIntent
                startActivityWithTransition(intent, TransitionKind.EXPLODE)
                finish()
            }
        }
    }

    private suspend fun bindUiError(uiError: UiError) {
        dismissSplashScreen = true

        // Every handler must either send a new UiAction or finish the activity.

        when (uiError) {
            is UiError.SetActiveAccount -> {
                when (uiError.cause) {
                    is SetActiveAccountError.AccountDoesNotExist -> {
                        // Special case AccountDoesNotExist, as that should never happen. If it does
                        // there's nothing to do except try and switch back to another account.
                        ChooseAccountWithErrorSuspendDialogFragment.newInstance(
                            getString(R.string.title_error_dialog),
                            uiError.fmt(this),
                        ).await(supportFragmentManager)?.let { account ->
                            viewModel.accept(uiError.action.copy(pachliAccountId = account.id))
                        } ?: finish()
                    }

                    is SetActiveAccountError.Api -> when (uiError.cause.apiError) {
                        // Special case invalid tokens. The user can be prompted to relogin.
                        // Cancelling chooses from other accounts, or finishes if there are none.
                        is ClientError.Unauthorized -> {
                            val accountCount = viewModel.accounts.value.get().orEmpty().size

                            val message = if (accountCount > 1) {
                                getString(
                                    R.string.client_error_unauthorized_multiple_fmt,
                                    uiError.fmt(this),
                                    getString(R.string.action_relogin),
                                    getString(android.R.string.cancel),
                                )
                            } else {
                                getString(
                                    R.string.client_error_unauthorized_fmt,
                                    uiError.fmt(this),
                                    getString(R.string.action_relogin),
                                )
                            }

                            val button = AlertSuspendDialogFragment.newInstance(
                                uiError.cause.wantedAccount.fullName,
                                message = message,
                                positiveText = getString(R.string.action_relogin),
                                negativeText = getString(android.R.string.cancel),
                            ).await(supportFragmentManager)

                            when (button) {
                                // OK? Let the user re-authenticate.
                                AlertDialog.BUTTON_POSITIVE -> {
                                    startActivityWithTransition(
                                        LoginActivityIntent(
                                            this,
                                            LoginMode.Reauthenticate(uiError.cause.wantedAccount.domain),
                                        ),
                                        TransitionKind.EXPLODE,
                                    )
                                    finish()
                                }

                                // Cancel? If this is the only account then finish. Otherwise
                                // show the available accounts and allow the user to choose
                                // another one.
                                AlertDialog.BUTTON_NEGATIVE -> {
                                    if (accountCount == 1) {
                                        finish()
                                        return
                                    }
                                    ChooseAccountSuspendDialogFragment.newInstance(
                                        getString(R.string.title_choose_account_dialog),
                                        true,
                                    ).await(supportFragmentManager)?.let { account ->
                                        viewModel.accept(uiError.action.copy(pachliAccountId = account.id))
                                    } ?: finish()
                                }
                            }
                        }

                        // Other API errors are retryable.
                        else -> {
                            ChooseAccountWithErrorSuspendDialogFragment.newInstance(
                                uiError.cause.wantedAccount.fullName,
                                uiError.fmt(this),
                            ).await(supportFragmentManager)?.let { account ->
                                viewModel.accept(uiError.action.copy(pachliAccountId = account.id))
                            } ?: finish()
                        }
                    }

                    // Database errors are not retryable. Display the error.
                    //
                    // If these occur it's a bug in Pachli, the database should never
                    // get to a bad state.
                    is SetActiveAccountError.Dao -> {
                        ChooseAccountWithErrorSuspendDialogFragment.newInstance(
                            uiError.cause.wantedAccount?.fullName ?: getString(R.string.title_error_dialog),
                            uiError.fmt(this),
                        ).await(supportFragmentManager)?.let { account ->
                            viewModel.accept(uiError.action.copy(pachliAccountId = account.id))
                        } ?: finish()
                    }

                    // Other errors are retryable.
                    is SetActiveAccountError.Unexpected -> {
                        ChooseAccountWithErrorSuspendDialogFragment.newInstance(
                            uiError.cause.wantedAccount.fullName,
                            uiError.fmt(this),
                        ).await(supportFragmentManager)?.let { account ->
                            viewModel.accept(uiError.action.copy(pachliAccountId = account.id))
                        } ?: finish()
                    }
                }
            }

            is UiError.RefreshAccount -> {
                // Whether or not to describe "Cancel" as "Choose another account" depends
                // on how many accounts exist.
                val accountCount = viewModel.accounts.value.get().orEmpty().size

                val button = AlertSuspendDialogFragment.newInstance(
                    uiError.cause.wantedAccount.fullName,
                    message = uiError.fmt(this),
                    positiveText = getString(app.pachli.core.ui.R.string.button_continue),
                    negativeText = getString(if (accountCount > 1) R.string.action_choose_another_account else android.R.string.cancel),
                    neutralText = getString(app.pachli.core.ui.R.string.action_retry),
                ).await(supportFragmentManager)

                when (button) {
                    AlertDialog.BUTTON_POSITIVE -> {
                        // Pretend the action succeeded.
                        bindUiSuccess(UiSuccess.RefreshAccount(uiError.action))
                    }

                    AlertDialog.BUTTON_NEGATIVE -> {
                        // Cancelled. If there are no more accounts then exit, otherwise
                        // show the account chooser, excluding the active account.
                        if (accountCount == 1) {
                            finish()
                            return
                        }

                        // Show account chooser, then make that account active.
                        ChooseAccountSuspendDialogFragment.newInstance(
                            getString(R.string.title_choose_account_dialog),
                            false,
                        ).await(supportFragmentManager)?.let { account ->
                            // Ignore the payload, as it's not going to be valid for the new
                            // account (e.g., if the user tapped on a Drafts notification for
                            // one account, opening drafts for the new account is not going to
                            // be helpful). Just start MainActivity for this account.
                            viewModel.accept(
                                SetActiveAccount(
                                    pachliAccountId = account.id,
                                    payload = Payload.MainActivity(MainActivityIntent.start(this, account.id)),
                                ),
                            )
                        } ?: finish()
                    }

                    AlertDialog.BUTTON_NEUTRAL -> {
                        // Refresh
                        viewModel.accept(uiError.action)
                    }
                }
            }
        }
    }

    /**
     * Resolves [pachliAccountId] to a valid Pachli account ID.
     *
     * If:
     *  - [pachliAccountId] is null, returns the active account ID, or null if
     *  no active account.
     *  - [pachliAccountId] is `-1`, returns the active account ID, or null if
     *  no active account.
     *  - [pachliAccountId] references an account that exists in the database,
     *  then that account ID (i.e., input == output).
     *  - Otherwise the account does not exist locally, returns null
     */
    private fun resolvePachliAccountId(pachliAccountId: Long?, accounts: List<AccountEntity>): Long? {
        if (pachliAccountId == null || pachliAccountId == -1L) {
            return accounts.find { it.isActive }?.id
        }

        return accounts.find { it.id == pachliAccountId }?.id
    }

    private fun launchComposeActivityAndExit(pachliAccountId: Long, composeOptions: ComposeActivityIntent.ComposeOptions? = null) {
        startActivity(
            ComposeActivityIntent(this, pachliAccountId, composeOptions).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    private fun forwardToComposeActivityAndExit(pachliAccountId: Long, intent: Intent, composeOptions: ComposeActivityIntent.ComposeOptions? = null) {
        val composeIntent = ComposeActivityIntent(this, pachliAccountId, composeOptions).apply {
            action = intent.action
            type = intent.type
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        composeIntent.pachliAccountId = pachliAccountId
        startActivity(composeIntent)
        finish()
    }
}

/** @see [ChooseAccountWithErrorSuspendDialogFragment.Companion]. */
@AndroidEntryPoint
class ChooseAccountWithErrorSuspendDialogFragment : ChooseAccountSuspendDialogFragment() {
    private val errorMsg by lazy { requireArguments().getCharSequence(ARG_ERROR_MSG) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogChooseAccountShowErrorBinding.inflate(layoutInflater, null, false)
        binding.message.text = errorMsg

        binding.tryAgain.text = getString(
            if (adapter.count == 1) {
                R.string.error_login_failed_hint
            } else {
                R.string.error_login_failed_hint_multiple
            },
        )

        return AlertDialog.Builder(requireActivity())
            .setTitle(title)
            .setAdapter(adapter) { _: DialogInterface?, index: Int ->
                result = adapter.getItem(index)
                dismiss()
            }
            .setView(binding.root)
            .create()
    }

    /**
     * A suspendable dialog showing the available
     * [PachliAccount][app.pachli.core.data.repository.PachliAccount] accounts, and
     * an error message below the list.
     *
     * @see [ChooseAccountSuspendDialogFragment.Companion].
     */
    companion object {
        private const val ARG_ERROR_MSG = "app.pachli.ARG_ERROR_MSG"

        /**
         * Creates the dialog.
         *
         * @param title Text to show as the dialog's title.
         * @param errorMsg Text to show as the error message.
         *
         * @see [ChooseAccountDialogFragment.newInstance].
         */
        fun newInstance(title: CharSequence?, errorMsg: CharSequence?): ChooseAccountWithErrorSuspendDialogFragment {
            val args = newInstance(title, true).arguments
            args?.putCharSequence(ARG_ERROR_MSG, errorMsg)
            return ChooseAccountWithErrorSuspendDialogFragment().apply {
                arguments = args
            }
        }
    }
}
