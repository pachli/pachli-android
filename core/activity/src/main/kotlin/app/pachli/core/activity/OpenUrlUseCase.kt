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

package app.pachli.core.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.pachli.core.preferences.SharedPreferencesRepository
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import timber.log.Timber

/**
 * Open's a URL, defaulting to respecting the user's preference for whether or not
 * to use Custom Tabs.
 *
 * @see [OpenUrlUseCase.invoke]
 */
// Can't launch other activities with an ApplicationContext so this is scoped to
// each activity and receives that activity's context.
@ActivityScoped
class OpenUrlUseCase @Inject constructor(
    @ActivityContext val context: Context,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) {
    /**
     * Open [url], possibly in a Custom Tab depending on [useCustomTab].
     *
     * @param url URL to open.
     * @param useCustomTab If true use a Custom Tab. Default value is the
     * from the user's preferences.
     */
    operator fun invoke(url: String, useCustomTab: Boolean = sharedPreferencesRepository.useCustomTab) {
        invoke(url.toUri().normalizeScheme(), useCustomTab)
    }

    /**
     * Open [uri], possibly in a Custom Tab depending on [useCustomTab].
     *
     * @param uri URI to open.
     * @param useCustomTab If true use a Custom Tab. Default value is the
     * from the user's preferences.
     */
    operator fun invoke(uri: Uri, useCustomTab: Boolean = sharedPreferencesRepository.useCustomTab) {
        if (useCustomTab) {
            openLinkInCustomTab(uri)
        } else {
            openLinkInBrowser(uri)
        }
    }

    /**
     * Tries to open a link in a custom tab, falling back to browser if not possible.
     *
     * @param uri URI to open.
     */
    private fun openLinkInCustomTab(uri: Uri) {
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
            openLinkInBrowser(uri)
        }
    }

    /**
     * Opens a link in the browser via Intent.ACTION_VIEW
     *
     * @param uri URI to open.
     */
    private fun openLinkInBrowser(uri: Uri?) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity was not found for intent, %s", intent)
        }
    }
}
