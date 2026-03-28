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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

val LocalPreferences: ProvidableCompositionLocal<PachliPreferences> = compositionLocalOf { PachliPreferences() }

/**
 * Provides current application preferences in [LocalPreferences].
 */
@Composable
fun PreferencesProvider(
    viewModel: PreferencesProviderViewModel = viewModel(),
    content: @Composable () -> Unit,
) {
    val preferences by viewModel.pachliPreferences.collectAsState()

    CompositionLocalProvider(LocalPreferences provides preferences, content)
}
