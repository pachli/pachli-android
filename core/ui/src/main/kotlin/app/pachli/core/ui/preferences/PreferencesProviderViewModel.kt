/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.ui.preferences

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.SharedPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Application preferences available to composables.
 *
 * @property theme Current application theme.
 */
@Immutable
data class PachliPreferences(
    val theme: AppTheme = AppTheme.AUTO_SYSTEM,
) {
    companion object {
        fun from(sharedPreferences: SharedPreferencesRepository) = PachliPreferences(
            theme = sharedPreferences.appTheme,
        )
    }
}

@HiltViewModel
class PreferencesProviderViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferencesRepository,
) : ViewModel() {
    val pachliPreferences = sharedPreferences.changes.map {
        PachliPreferences.from(sharedPreferences)
    }
        .onStart { emit(PachliPreferences.from(sharedPreferences)) }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            PachliPreferences.from(sharedPreferences),
        )
}
