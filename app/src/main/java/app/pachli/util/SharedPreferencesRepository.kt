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

package app.pachli.util

import android.content.SharedPreferences
import app.pachli.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class SharedPreferencesRepository @Inject constructor(
    val sharedPreferences: SharedPreferences,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    /** Flow of keys that have been updated in the preferences */
    val changes = MutableSharedFlow<String>()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            key?.let { externalScope.launch { changes.emit(it) } }
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Forwards to [SharedPreferences.getBoolean] */
    fun getBoolean(key: String, defValue: Boolean) = sharedPreferences.getBoolean(key, defValue)
}
