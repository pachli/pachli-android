/*
 * Copyright 2022 Tusky Contributors
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.ViewGroupCompat
import androidx.lifecycle.lifecycleScope
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.versionName
import app.pachli.core.navigation.LoginWebViewActivityIntent
import app.pachli.core.network.util.localTrustManager
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.feature.login.databinding.ActivityLoginWebviewBinding
import dagger.hilt.android.AndroidEntryPoint
import java.security.cert.X509Certificate
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/** Contract for starting [LoginWebViewActivity]. */
class OauthLogin : ActivityResultContract<LoginData, LoginResult>() {
    override fun createIntent(context: Context, input: LoginData): Intent {
        val intent = LoginWebViewActivityIntent(context)
        intent.putExtra(EXTRA_DATA, input)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): LoginResult {
        // Can happen automatically on up or back press
        return if (resultCode == Activity.RESULT_CANCELED) {
            LoginResult.Cancel
        } else {
            intent?.let {
                IntentCompat.getParcelableExtra(it, EXTRA_RESULT, LoginResult::class.java)
            } ?: LoginResult.Error("failed parsing LoginWebViewActivity result")
        }
    }

    companion object {
        private const val EXTRA_RESULT = "app.pachli.EXTRA_RESULT"
        private const val EXTRA_DATA = "app.pachli.EXTRA_DATA"

        fun parseData(intent: Intent): LoginData {
            return IntentCompat.getParcelableExtra(intent, EXTRA_DATA, LoginData::class.java)!!
        }

        fun makeResultIntent(result: LoginResult): Intent {
            val intent = Intent()
            intent.putExtra(EXTRA_RESULT, result)
            return intent
        }
    }
}

@Parcelize
data class LoginData(
    val domain: String,
    val url: Uri,
    val oauthRedirectUrl: Uri,
) : Parcelable

sealed interface LoginResult : Parcelable {
    /**
     * Login succeeded, with result code [code].
     */
    @Parcelize
    data class Code(val code: String) : LoginResult

    /**
     * Login failed, with [errorMessage].
     */
    @Parcelize
    data class Error(val errorMessage: String) : LoginResult

    @Parcelize
    data object Cancel : LoginResult
}

/** Activity to do Oauth process using WebView. */
@AndroidEntryPoint
class LoginWebViewActivity : BaseActivity() {
    private val binding by viewBinding(ActivityLoginWebviewBinding::inflate)

    private val viewModel: LoginWebViewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.appBar.applyDefaultWindowInsets()
        binding.webviewWrapper.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
            bottom = InsetType.PADDING,
            withIme = true,
        )

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val data = OauthLogin.parseData(intent)

        setContentView(binding.root)

        setSupportActionBar(binding.loginToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        setTitle(R.string.title_login)

        val webView = binding.loginWebView
        webView.settings.allowContentAccess = false
        webView.settings.allowFileAccess = false
        webView.settings.databaseEnabled = false
        webView.settings.displayZoomControls = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        // JavaScript needs to be enabled because otherwise 2FA does not work in some instances
        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString += " Pachli/${versionName(this)}"

        val oauthUrl = data.oauthRedirectUrl

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.loginProgress.hide()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                Timber.d("Failed to load %s: %d %s", data.url, error.errorCode, error.description)
                sendResult(LoginResult.Error(getString(R.string.error_could_not_load_login_page)))
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            /* overriding this deprecated method is necessary for it to work on api levels < 24 */
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, urlString: String?): Boolean {
                val url = urlString?.toUri() ?: return false
                return shouldOverrideUrlLoading(url)
            }

            fun shouldOverrideUrlLoading(url: Uri): Boolean {
                return if (url.scheme == oauthUrl.scheme && url.host == oauthUrl.host) {
                    val error = url.getQueryParameter("error")
                    if (error != null) {
                        sendResult(LoginResult.Error(error))
                    } else {
                        val code = url.getQueryParameter("code").orEmpty()
                        sendResult(LoginResult.Code(code))
                    }
                    true
                } else {
                    false
                }
            }

            @SuppressLint("DiscouragedPrivateApi", "WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // An SSL error might be because the user is connecting to a server using
                // Let's Encrypt certificates on an Android 7 device. If that's the case
                // check if the server is trusted using the local trust manager, which
                // includes the Let's Encrypt certificates.
                if (error?.primaryError != SslError.SSL_UNTRUSTED) return super.onReceivedSslError(view, handler, error)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return super.onReceivedSslError(view, handler, error)

                // Based on https://www.guardsquare.com/blog/how-to-securely-implement-tls-certificate-checking-in-android-apps
                // but uses the OkHttp HandshakeCertificates type.
                try {
                    val certField = error.certificate.javaClass.getDeclaredField("mX509Certificate")
                    certField.isAccessible = true
                    val cert = certField.get(error.certificate) as X509Certificate
                    localTrustManager(this@LoginWebViewActivity).checkServerTrusted(arrayOf(cert), "generic")
                    handler?.proceed()
                } catch (_: Exception) {
                    super.onReceivedSslError(view, handler, error)
                }
            }
        }

        webView.setBackgroundColor(Color.TRANSPARENT)

        if (savedInstanceState == null) {
            webView.loadUrl(data.url.toString())
        } else {
            webView.restoreState(savedInstanceState)
        }

        binding.loginRules.text = getString(R.string.instance_rule_info, data.domain)

        viewModel.init(data.domain)

        lifecycleScope.launch {
            viewModel.instanceRules.collect { instanceRules ->
                binding.loginRules.visible(instanceRules.isNotEmpty())
                binding.loginRules.setOnClickListener {
                    AlertDialog.Builder(this@LoginWebViewActivity)
                        .setTitle(getString(R.string.instance_rule_title, data.domain))
                        .setMessage(
                            instanceRules.joinToString(separator = "\n\n") { "• $it" },
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.loginWebView.saveState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) {
            // We don't want to keep user session in WebView, we just want our own accessToken
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
        super.onDestroy()
    }

    override fun requiresLogin() = false

    private fun sendResult(result: LoginResult) {
        setResult(RESULT_OK, OauthLogin.makeResultIntent(result))
        finish()
    }
}
