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

package app.pachli.core.data.repository

import androidx.preference.PreferenceDataStore
import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.preferences.PrefKeys
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class AccountPreferenceDataStore @Inject constructor(
    private val accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope,
) : PreferenceDataStore() {
    /** Flow of key/values that have been updated in the preferences */
    val changes = MutableSharedFlow<Pair<String, Boolean>>()

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val account = accountManager.activeAccount ?: return defValue

        return when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled
            else -> defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        val account = accountManager.activeAccount!!

        externalScope.launch {
            when (key) {
                PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> accountManager.setAlwaysShowSensitiveMedia(account.id, value)
                PrefKeys.ALWAYS_OPEN_SPOILER -> accountManager.setAlwaysOpenSpoiler(account.id, value)
                PrefKeys.MEDIA_PREVIEW_ENABLED -> accountManager.setMediaPreviewEnabled(account.id, value)
            }

            changes.emit(Pair(key, value))
        }
    }
}
