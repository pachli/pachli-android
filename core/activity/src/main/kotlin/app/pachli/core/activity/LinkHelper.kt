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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.preference.PreferenceManager
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.pachli.core.preferences.PrefKeys
import com.google.android.material.color.MaterialColors
import timber.log.Timber

/**
 * tries to open a link in a custom tab
 * falls back to browser if not possible
 *
 * @param uri the uri to open
 * @param context context
 */
fun openLinkInCustomTab(uri: Uri, context: Context) {
    val toolbarColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
    val navigationbarColor = MaterialColors.getColor(context, android.R.attr.navigationBarColor, Color.BLACK)
    val navigationbarDividerColor = MaterialColors.getColor(context, com.google.android.material.R.attr.dividerColor, Color.BLACK)
    val colorSchemeParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setNavigationBarColor(navigationbarColor)
        .setNavigationBarDividerColor(navigationbarDividerColor)
        .build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorSchemeParams)
        .setShowTitle(true)
        .build()

    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Activity was not found for intent %s", customTabsIntent)
        openLinkInBrowser(uri, context)
    }
}

/**
 * opens a link in the browser via Intent.ACTION_VIEW
 *
 * @param uri the uri to open
 * @param context context
 */
private fun openLinkInBrowser(uri: Uri?, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Activity was not found for intent, %s", intent)
    }
}

/**
 * Opens a link, depending on the settings, either in the browser or in a custom tab
 *
 * @receiver the Context to open the link from
 * @param url a string containing the url to open
 */
fun Context.openLink(url: String) {
    val uri = url.toUri().normalizeScheme()
    val useCustomTabs = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PrefKeys.CUSTOM_TABS, false)

    if (useCustomTabs) {
        openLinkInCustomTab(uri, this)
    } else {
        openLinkInBrowser(uri, this)
    }
}
