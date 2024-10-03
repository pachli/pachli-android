/*
 * Copyright 2023 Pachli Association
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
package app.pachli.core.activity

import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import app.pachli.core.activity.extensions.canOverrideActivityTransitions
import app.pachli.core.activity.extensions.getTransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.EmbeddedFontFamily
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors.fromApplication
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.properties.Delegates
import timber.log.Timber

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity(), MenuProvider {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private var requesters: HashMap<Int, PermissionRequester> = HashMap()

    /** The most recent theme ID set with [setTheme]. */
    @get:StyleRes
    private var activeThemeId by Delegates.notNull<Int>()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SharedPreferencesRepositoryEntryPoint {
        fun sharedPreferencesRepository(): SharedPreferencesRepository
    }

    override fun setTheme(@StyleRes themeId: Int) {
        activeThemeId = themeId
        super.setTheme(themeId)
        if (BuildConfig.DEBUG) {
            val name = resources.getResourceEntryName(themeId)
            Timber.d("Setting theme: %s", name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (canOverrideActivityTransitions()) {
            intent.getTransitionKind()?.let {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, it.openEnter, it.openExit)
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, it.closeEnter, it.closeExit)
            }
        }

        // Set the theme from preferences
        val theme = sharedPreferencesRepository.appTheme
        Timber.d("activeTheme: %s", theme)
        if (theme == AppTheme.BLACK) {
            setTheme(DR.style.Theme_Pachli_Black)
        } else if (activeThemeId == DR.style.SplashTheme) {
            setTheme(DR.style.Theme_Pachli)
        }

        // Set the task description, the theme would turn it blue
        val appName = getString(R.string.app_name)
        val appIcon = BitmapFactory.decodeResource(resources, DR.mipmap.ic_launcher)
        val recentsBackgroundColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.BLACK,
        )
        setTaskDescription(TaskDescription(appName, appIcon, recentsBackgroundColor))

        // Set status text size
        val style =
            textStyle(sharedPreferencesRepository.getString(PrefKeys.STATUS_TEXT_SIZE, "medium")!!)
        getTheme().applyStyle(style, true)

        // Set application font family
        val fontFamily =
            EmbeddedFontFamily.from(sharedPreferencesRepository.getString(PrefKeys.FONT_FAMILY, "default"))
        if (fontFamily !== EmbeddedFontFamily.DEFAULT) {
            getTheme().applyStyle(fontFamily.style, true)
        }
        if (requiresLogin()) {
            redirectIfNotLoggedIn()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPreferencesRepository = fromApplication(
            newBase.applicationContext,
            SharedPreferencesRepositoryEntryPoint::class.java,
        ).sharedPreferencesRepository()

        // Scale text in the UI from PrefKeys.UI_TEXT_SCALE_RATIO
        val uiScaleRatio = sharedPreferencesRepository.getFloat(PrefKeys.UI_TEXT_SCALE_RATIO, 100f)
        val configuration = newBase.resources.configuration

        // Adjust `fontScale` in the configuration.
        //
        // You can't repeatedly adjust the `fontScale` in `newBase` because that will contain the
        // result of previous adjustments. E.g., going from 100% to 80% to 100% does not return
        // you to the original 100%, it leaves it at 80%.
        //
        // Instead, calculate the new scale from the application context. This is unaffected by
        // changes to the base context. It does contain contain any changes to the font scale from
        // "Settings > Display > Font size" in the device settings, so scaling performed here
        // is in addition to any scaling in the device settings.
        val appConfiguration = newBase.applicationContext.resources.configuration

        // This only adjusts the fonts, anything measured in `dp` is unaffected by this.
        // You can try to adjust `densityDpi` as shown in the commented out code below. This
        // works, to a point. However, dialogs do not react well to this. Beyond a certain
        // scale (~ 120%) the right hand edge of the dialog will clip off the right of the
        // screen.
        //
        // So for now, just adjust the font scale
        //
        // val displayMetrics = appContext.resources.displayMetrics
        // configuration.densityDpi = ((displayMetrics.densityDpi * uiScaleRatio).toInt())
        configuration.fontScale = appConfiguration.fontScale * uiScaleRatio / 100f
        val fontScaleContext = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(fontScaleContext)
    }

    protected open fun requiresLogin(): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()

        if (!canOverrideActivityTransitions()) {
            intent.getTransitionKind()?.let {
                @Suppress("DEPRECATION")
                overridePendingTransition(it.closeEnter, it.closeExit)
            }
        }
    }

    private fun redirectIfNotLoggedIn() {
        val account = accountManager.activeAccount
        if (account == null) {
            val intent = LoginActivityIntent(this)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityWithDefaultTransition(intent)
            finish()
        }
    }

    protected fun showErrorDialog(
        anyView: View,
        @StringRes descriptionId: Int,
        @StringRes actionId: Int,
        listener: View.OnClickListener,
    ) {
        val bar = Snackbar.make(anyView, getString(descriptionId), Snackbar.LENGTH_SHORT)
        bar.setAction(actionId, listener)
        bar.show()
    }

    fun showAccountChooserDialog(
        dialogTitle: CharSequence?,
        showActiveAccount: Boolean,
        listener: AccountSelectionListener,
    ) {
        val accounts = accountManager.getAllAccountsOrderedByActive().toMutableList()
        val activeAccount = accountManager.activeAccount!!
        when (accounts.size) {
            1 -> {
                listener.onAccountSelected(activeAccount)
                return
            }

            2 -> {
                if (!showActiveAccount) {
                    for (account in accounts) {
                        if (activeAccount !== account) {
                            listener.onAccountSelected(account)
                            return
                        }
                    }
                }
            }
        }
        if (!showActiveAccount) {
            accounts.remove(activeAccount)
        }
        val adapter = AccountSelectionAdapter(this)
        adapter.addAll(accounts)
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setAdapter(adapter) { _: DialogInterface?, index: Int ->
                listener.onAccountSelected(
                    accounts[index],
                )
            }
            .show()
    }

    val openAsText: String?
        get() {
            val accounts = accountManager.getAllAccountsOrderedByActive()
            return when (accounts.size) {
                0, 1 -> null
                2 -> {
                    for (account in accounts) {
                        if (account !== accountManager.activeAccount) {
                            return String.format(getString(R.string.action_open_as), account.fullName)
                        }
                    }
                    null
                }
                else -> String.format(getString(R.string.action_open_as), "…")
            }
        }

    fun openAsAccount(url: String, account: AccountEntity) {
        accountManager.setActiveAccount(account.id)
        val intent = MainActivityIntent.redirect(this, account.id, url)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val requester = requesters.remove(requestCode)
        requester?.onRequestPermissionsResult(permissions, grantResults)
    }

    fun requestPermissions(permissions: Array<String>, requester: PermissionRequester) {
        val permissionsToRequest = ArrayList<String>()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isEmpty()) {
            val permissionsAlreadyGranted = IntArray(permissions.size)
            requester.onRequestPermissionsResult(permissions, permissionsAlreadyGranted)
            return
        }
        val newKey = requesters.size
        if (newKey != REQUESTER_NONE) {
            requesters[newKey] = requester
        }
        val permissionsCopy = arrayOfNulls<String>(permissionsToRequest.size)
        permissionsToRequest.toArray(permissionsCopy)
        ActivityCompat.requestPermissions(this, permissionsCopy, newKey)
    }

    @CallSuper
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (BuildConfig.FLAVOR_color != "orange") return
        menuInflater.inflate(R.menu.activity_base, menu)
    }

    @CallSuper
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (BuildConfig.FLAVOR_color != "orange") return false
        return when (menuItem.itemId) {
            R.id.action_crash_report -> {
                triggerCrashReport()
                true
            }
            else -> false
        }
    }

    companion object {
        private const val REQUESTER_NONE = Int.MAX_VALUE

        @StyleRes
        private fun textStyle(name: String): Int {
            return when (name) {
                "smallest" -> DR.style.TextSizeSmallest
                "small" -> DR.style.TextSizeSmall
                "medium" -> DR.style.TextSizeMedium
                "large" -> DR.style.TextSizeLarge
                "largest" -> DR.style.TextSizeLargest
                else -> DR.style.TextSizeMedium
            }
        }
    }
}
