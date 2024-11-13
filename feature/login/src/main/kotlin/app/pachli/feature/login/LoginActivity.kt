/*
 * Copyright 2017 Andrew Dawson
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

package app.pachli.feature.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.setCloseTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.activity.openLinkInCustomTab
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.getNonNullString
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.feature.login.databinding.ActivityLoginBinding
import at.connyduck.calladapter.networkresult.fold
import com.bumptech.glide.Glide
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import timber.log.Timber

/**
 * Main login page, the first thing that users see.
 *
 * Has prompt for instance and login button.
 */
@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    @Inject
    lateinit var mastodonApi: MastodonApi

    private val viewModel: LoginViewModel by viewModels()

    private val binding by viewBinding(ActivityLoginBinding::inflate)

    private lateinit var preferences: SharedPreferences

    private val oauthRedirectUri: String
        get() {
            val scheme = getString(R.string.oauth_scheme)
            val host = packageName
            return "$scheme://$host/"
        }

    private val doWebViewAuth = registerForActivityResult(OauthLogin()) { result ->
        when (result) {
            is LoginResult.Ok -> lifecycleScope.launch {
                fetchOauthToken(result.code)
            }
            is LoginResult.Err -> displayError(result.errorMessage)
            is LoginResult.Cancel -> setLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        val authenticationDomain = authenticationDomain()

        if (savedInstanceState == null &&
            BuildConfig.CUSTOM_INSTANCE.isNotBlank() &&
            !isAdditionalLogin() &&
            authenticationDomain == null
        ) {
            binding.domainEditText.setText(BuildConfig.CUSTOM_INSTANCE)
            binding.domainEditText.setSelection(BuildConfig.CUSTOM_INSTANCE.length)
        }

        authenticationDomain?.let { domain ->
            binding.domainEditText.setText(domain)
            binding.domainEditText.isEnabled = false
        }

        if (BuildConfig.CUSTOM_LOGO_URL.isNotBlank()) {
            Glide.with(binding.loginLogo)
                .load(BuildConfig.CUSTOM_LOGO_URL)
                .placeholder(null)
                .into(binding.loginLogo)
        }

        preferences = getSharedPreferences(
            getString(R.string.preferences_file_key),
            Context.MODE_PRIVATE,
        )

        binding.loginButton.setOnClickListener { onLoginClick(true) }

        binding.whatsAnInstanceTextView.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setMessage(R.string.dialog_whats_an_instance)
                .setPositiveButton(R.string.action_close, null)
                .show()
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            textView?.movementMethod = LinkMovementMethod.getInstance()
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(isAdditionalLogin() || authenticationDomain != null)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        bind()
    }

    /** Binds data to the UI. */
    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiResult.collect(::bindUiResult) }
            }
        }
    }

    /** Act on the result of UI actions. */
    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
        uiResult.onFailure { uiError ->
            when (uiError) {
                is UiError.VerifyAndAddAccount -> {
                    setLoading(false)
                    binding.domainTextInputLayout.error = uiError.fmt(this)
                    Timber.e(uiError.fmt(this))
                }
            }
        }

        uiResult.onSuccess { uiSuccess ->
            when (uiSuccess) {
                is UiSuccess.VerifyAndAddAccount -> {
                    val intent = MainActivityIntent(this, uiSuccess.accountId)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivityWithTransition(intent, TransitionKind.EXPLODE)
                    finishAffinity()
                    setCloseTransition(TransitionKind.EXPLODE)
                }
            }
        }
    }

    override fun requiresLogin(): Boolean {
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(R.string.action_browser_login)?.apply {
            setOnMenuItemClickListener {
                onLoginClick(false)
                true
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Obtain the oauth client credentials for this app. This is only necessary the first time the
     * app is run on a given server instance. So, after the first authentication, they are
     * saved in SharedPreferences and every subsequent run they are simply fetched from there.
     */
    private fun onLoginClick(openInWebView: Boolean) {
        binding.loginButton.isEnabled = false
        binding.domainTextInputLayout.error = null

        val domain = canonicalizeDomain(binding.domainEditText.text.toString())

        try {
            HttpUrl.Builder().host(domain).scheme("https").build()
        } catch (e: IllegalArgumentException) {
            setLoading(false)
            binding.domainTextInputLayout.error = getString(R.string.error_invalid_domain)
            return
        }

        if (shouldRickRoll(this, domain)) {
            rickRoll(this)
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            mastodonApi.authenticateApp(
                domain,
                getString(R.string.app_name),
                oauthRedirectUri,
                OAUTH_SCOPES,
                getString(R.string.pachli_website),
            ).fold(
                { credentials ->
                    // Before we open browser page we save the data.
                    // Even if we don't open other apps user may go to password manager or somewhere else
                    // and we will need to pick up the process where we left off.
                    // Alternatively we could pass it all as part of the intent and receive it back
                    // but it is a bit of a workaround.
                    preferences.edit()
                        .putString(DOMAIN, domain)
                        .putString(CLIENT_ID, credentials.clientId)
                        .putString(CLIENT_SECRET, credentials.clientSecret)
                        .apply()

                    redirectUserToAuthorizeAndLogin(domain, credentials.clientId, openInWebView)
                },
                { e ->
                    binding.loginButton.isEnabled = true
                    binding.domainTextInputLayout.error =
                        String.format(
                            getString(R.string.error_failed_app_registration_fmt),
                            e.getErrorString(this@LoginActivity),
                        )
                    setLoading(false)
                    Timber.e(e, "Error when creating/registing app")
                    return@launch
                },
            )
        }
    }

    private fun redirectUserToAuthorizeAndLogin(domain: String, clientId: String, openInWebView: Boolean) {
        // To authorize this app and log in it's necessary to redirect to the domain given,
        // login there, and the server will redirect back to the app with its response.
        val uri = HttpUrl.Builder()
            .scheme("https")
            .host(domain)
            .addPathSegments(MastodonApi.ENDPOINT_AUTHORIZE)
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", oauthRedirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", OAUTH_SCOPES)
            .build()
            .toString()
            .toUri()

        if (openInWebView) {
            doWebViewAuth.launch(LoginData(domain, uri, oauthRedirectUri.toUri()))
        } else {
            openLinkInCustomTab(uri, this)
        }
    }

    override fun onStart() {
        super.onStart()

        /* Check if we are resuming during authorization by seeing if the intent contains the
         * redirect that was given to the server. If so, its response is here! */
        val uri = intent.data

        if (uri?.toString()?.startsWith(oauthRedirectUri) == true) {
            // This should either have returned an authorization code or an error.
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            /* restore variables from SharedPreferences */
            val domain = preferences.getNonNullString(DOMAIN, "")
            val clientId = preferences.getNonNullString(CLIENT_ID, "")
            val clientSecret = preferences.getNonNullString(CLIENT_SECRET, "")

            if (code != null && domain.isNotEmpty() && clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                lifecycleScope.launch {
                    fetchOauthToken(code)
                }
            } else {
                displayError(error)
            }
        } else {
            // first show or user cancelled login
            setLoading(false)
        }
    }

    private fun displayError(error: String?) {
        // Authorization failed. Put the error response where the user can read it and they
        // can try again.
        setLoading(false)

        binding.domainTextInputLayout.error = if (error == null) {
            // This case means a junk response was received somehow.
            getString(R.string.error_authorization_unknown)
        } else {
            // Use error returned by the server or fall back to the generic message
            Timber.e("%s %s".format(getString(R.string.error_authorization_denied), error))
            error.ifBlank { getString(R.string.error_authorization_denied) }
        }
    }

    private suspend fun fetchOauthToken(code: String) {
        /* restore variables from SharedPreferences */
        val domain = preferences.getNonNullString(DOMAIN, "")
        val clientId = preferences.getNonNullString(CLIENT_ID, "")
        val clientSecret = preferences.getNonNullString(CLIENT_SECRET, "")

        setLoading(true)

        mastodonApi.fetchOAuthToken(
            domain,
            clientId,
            clientSecret,
            oauthRedirectUri,
            code,
            "authorization_code",
        ).fold(
            { accessToken ->
                viewModel.accept(
                    FallibleUiAction.VerifyAndAddAccount(
                        accessToken,
                        domain,
                        clientId,
                        clientSecret,
                        OAUTH_SCOPES,
                    ),
                )
            },
            { e ->
                setLoading(false)
                binding.domainTextInputLayout.error =
                    getString(R.string.error_retrieving_oauth_token)
                Timber.e(e, getString(R.string.error_retrieving_oauth_token))
            },
        )
    }

    private fun setLoading(loadingState: Boolean) {
        if (loadingState) {
            binding.loginLoadingLayout.visibility = View.VISIBLE
            binding.loginInputLayout.visibility = View.GONE
        } else {
            binding.loginLoadingLayout.visibility = View.GONE
            binding.loginInputLayout.visibility = View.VISIBLE
            binding.loginButton.isEnabled = true
        }
    }

    private fun isAdditionalLogin(): Boolean {
        return LoginActivityIntent.getLoginMode(intent) is LoginActivityIntent.LoginMode.AdditionalLogin
    }

    private fun authenticationDomain(): String? {
        return (LoginActivityIntent.getLoginMode(intent) as? LoginActivityIntent.LoginMode.Reauthenticate)?.domain
    }

    companion object {
        private const val OAUTH_SCOPES = "read write follow push"
        private const val DOMAIN = "domain"
        private const val CLIENT_ID = "clientId"
        private const val CLIENT_SECRET = "clientSecret"

        /** Make sure the user-entered text is just a fully-qualified domain name. */
        private fun canonicalizeDomain(domain: String): String {
            // Strip any schemes out.
            var s = domain.replaceFirst("http://", "")
            s = s.replaceFirst("https://", "")
            // If a username was included (e.g. username@example.com), just take what's after the '@'.
            val at = s.lastIndexOf('@')
            if (at != -1) {
                s = s.substring(at + 1)
            }
            return s.trim { it <= ' ' }
        }
    }
}
