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

package app.pachli.feature.accountrouter

import android.app.NotificationManager
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
import app.pachli.core.navigation.AccountRouterActivityIntent
import app.pachli.core.navigation.AccountRouterActivityIntent.Payload
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.ui.extensions.await
import app.pachli.feature.accountrouter.AccountRouterViewModel.Companion.canHandleMimeType
import app.pachli.feature.accountrouter.FallibleUiAction.SetActiveAccount
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Determines the correct account to use, and routes the user to the correct
 * activity.
 */
@AndroidEntryPoint
class AccountRouterActivity : BaseActivity() {
    private val viewModel: AccountRouterViewModel by viewModels()

    override fun requiresLogin() = false

    private var dismissSplashScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_account_router)

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

    private fun bindAccount(savedInstanceState: Bundle?, accounts: List<AccountEntity>) {
        Timber.d("bindAccount: $accounts")

        // Only thing to do if there are no accounts is to prompt the user to login.
        if (accounts.isEmpty()) {
            val intent = LoginActivityIntent(this@AccountRouterActivity)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityWithDefaultTransition(intent)
            return
        }

        if (savedInstanceState != null) return

        val pachliAccountId: Long = resolvePachliAccountId(intent.pachliAccountId, accounts) ?: run {
            // Requested account does not exist, ask the user to choose (or maybe show an error
            // instead).
            // TODO: Dialog
            if (BuildConfig.DEBUG) throw RuntimeException("Could not resolve account with ID ${intent.pachliAccountId}")
            -1L
        }

        val payload = AccountRouterActivityIntent.payload(intent)
            ?: Payload.MainActivity(MainActivityIntent.start(this, pachliAccountId))

//        Timber.d("  payload: $payload")
        when (payload) {
            is Payload.QuickTile -> {
                showAccountChooserDialog(getString(R.string.action_share_as), true) { account ->
                    val requestedId = account.id
                    launchComposeActivityAndExit(requestedId)
                }
            }

            is Payload.NotificationCompose -> {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(payload.notificationTag, payload.notificationId)
                launchComposeActivityAndExit(intent.pachliAccountId, payload.composeOptions)
            }

            Payload.ShareContent -> {
                // If the intent contains data to share choose the account to share from
                // and start the composer.
                if (canHandleMimeType(intent.type)) {
                    // Determine the account to use.
                    showAccountChooserDialog(getString(R.string.action_share_as), true) { account ->
                        val requestedId = account.id
                        forwardToComposeActivityAndExit(requestedId, intent)
                    }
                }
                // TODO: Show an error if we can't handle the content
                return
            }

            is Payload.MainActivity -> viewModel.accept(SetActiveAccount(pachliAccountId, payload))
        }
    }

    private fun bindUiSuccess(success: UiSuccess) {
        Timber.d("bindUiSuccess: $success")

        when (success) {
            is UiSuccess.SetActiveAccount -> viewModel.accept(
                FallibleUiAction.RefreshAccount(
                    success.accountEntity,
                    success.action.payload,
                ),
            )

            is UiSuccess.RefreshAccount -> {
                val payload = success.action.payload
                val intent = payload.mainActivityIntent
                startActivityWithTransition(intent, TransitionKind.EXPLODE)
                finish()
            }
        }
    }

    private suspend fun bindUiError(uiError: UiError) {
//        Timber.d("bindUiError: uiError")
        when (uiError) {
            is UiError.SetActiveAccount -> {
                // Logging in failed. Show a dialog explaining what's happened.
                val builder = AlertDialog.Builder(this)
                    .setMessage(uiError.fmt(this))
                    .create()

                when (uiError.cause) {
                    is SetActiveAccountError.AccountDoesNotExist -> {
                        // Special case AccountDoesNotExist, as that should never happen. If it does
                        // there's nothing to do except try and switch back to the previous account.
                        val button = builder.await(android.R.string.ok)
                        if (button == AlertDialog.BUTTON_POSITIVE && uiError.cause.fallbackAccount != null) {
                            viewModel.accept(uiError.action.copy(pachliAccountId = uiError.cause.fallbackAccount!!.id))
                        }
                        return
                    }

                    is SetActiveAccountError.Api -> when (uiError.cause.apiError) {
                        // Special case invalid tokens. The user can be prompted to relogin. Cancelling
                        // switches to the fallback account, or finishes if there is none.
                        is ClientError.Unauthorized -> {
                            builder.setTitle(uiError.cause.wantedAccount.fullName)

                            val button = builder.await(R.string.action_relogin, android.R.string.cancel)
                            when (button) {
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

                                // TODO: Get rid of the "fallback" account idea. This should show
                                // the user a list of accounts and they can try again.
                                AlertDialog.BUTTON_NEGATIVE -> {
                                    uiError.cause.fallbackAccount?.run {
                                        viewModel.accept(SetActiveAccount(id, uiError.action.payload))
                                    } ?: finish()
                                }
                            }
                        }

                        // Other API errors are retryable.
                        else -> {
                            builder.setTitle(uiError.cause.wantedAccount.fullName)
                            val button = builder.await(app.pachli.core.ui.R.string.action_retry, android.R.string.cancel)
                            when (button) {
                                AlertDialog.BUTTON_POSITIVE -> viewModel.accept(uiError.action)
                                else -> {
                                    uiError.cause.fallbackAccount?.run {
                                        viewModel.accept(uiError.action)
                                    } ?: finish()
                                }
                            }
                        }
                    }

                    // Database errors are not retryable. Display the error, offer to
                    // switch back to the fall back account.
                    //
                    // If these occur it's a bug in Pachli, the database should never
                    // get to a bad state.
                    is SetActiveAccountError.Dao -> {
                        uiError.cause.wantedAccount?.let { builder.setTitle(it.fullName) }
                        val button = builder.await(android.R.string.ok)
                        if (button == AlertDialog.BUTTON_POSITIVE && uiError.cause.fallbackAccount != null) {
                            viewModel.accept(uiError.action.copy(pachliAccountId = uiError.cause.fallbackAccount!!.id))
                        }
                        return
                    }

                    // Other errors are retryable.
                    is SetActiveAccountError.Unexpected -> {
                        builder.setTitle(uiError.cause.wantedAccount.fullName)
                        val button = builder.await(app.pachli.core.ui.R.string.action_retry, android.R.string.cancel)
                        when (button) {
                            AlertDialog.BUTTON_POSITIVE -> viewModel.accept(uiError.action)
                            else -> {
                                uiError.cause.fallbackAccount?.let {
                                    viewModel.accept(uiError.action.copy(pachliAccountId = it.id))
                                } ?: finish()
                            }
                        }
                    }
                }
            }

            is UiError.RefreshAccount -> {
                // Dialog that explains refreshing failed, with retry option.
                val button = AlertDialog.Builder(this)
                    .setTitle(uiError.action.accountEntity.fullName)
                    .setMessage(uiError.fmt(this))
                    .create()
                    .await(app.pachli.core.ui.R.string.action_retry, android.R.string.cancel)
                if (button == AlertDialog.BUTTON_POSITIVE) viewModel.accept(uiError.action)
            }
        }
    }

    /**
     * Resolves [pachliAccountId] to an actual Pachli account ID.
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
