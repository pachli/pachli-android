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
import android.util.Log
import app.pachli.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of [SharedPreferences] that exposes all changes to the
 * preferences through the [changes] flow.
 *
 * @param sharedPreferences instance to delegate to
 * @param externalScope [CoroutineScope] to use when emitting in to [changes]
 */
@Singleton
class SharedPreferencesRepository @Inject constructor(
    val sharedPreferences: SharedPreferences,
    @ApplicationScope private val externalScope: CoroutineScope,
) : SharedPreferences by sharedPreferences {
    /**
     *  Flow of keys that have been updated/deleted in the preferences.
     *
     *  Null means that preferences were cleared.
     */
    val changes = MutableSharedFlow<String?>()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            externalScope.launch { changes.emit(key) }
        }

    init {
        Log.d("SharedPreferencesRepository", "Being created")
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
