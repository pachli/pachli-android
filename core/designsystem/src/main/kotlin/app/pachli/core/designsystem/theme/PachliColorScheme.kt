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

package app.pachli.core.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours Pachli needs that are not part of the Material Design
 * spec.
 *
 * @property backgroundFloating Background colour for floating windows
 * (e.g., dialogs). Equivalent to [android.R.attr.colorBackgroundFloating].
 * @property colorControlNormal Normal tint for icons used on controls
 * (e.g., in list items). Equivalent to [android.R.attr.colorControlNormal].
 */
data class PachliColorScheme(
    val backgroundFloating: Color,
    val colorControlNormal: Color,
)

@SuppressLint("CompositionLocalNaming")
val PachliColors = staticCompositionLocalOf {
    PachliColorScheme(
        backgroundFloating = Color.Unspecified,
        colorControlNormal = Color.Unspecified,
    )
}

val pachliColors: PachliColorScheme
    @Composable
    get() = PachliColors.current
