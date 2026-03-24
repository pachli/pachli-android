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

package app.pachli.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.pachli.core.designsystem.theme.AppTypography
import app.pachli.core.designsystem.theme.PachliColors
import app.pachli.core.designsystem.theme.blackMaterialScheme
import app.pachli.core.designsystem.theme.blackPachliScheme
import app.pachli.core.designsystem.theme.darkMaterialScheme
import app.pachli.core.designsystem.theme.darkPachliScheme
import app.pachli.core.designsystem.theme.lightMaterialScheme
import app.pachli.core.designsystem.theme.lightPachliScheme
import app.pachli.core.preferences.AppTheme
import app.pachli.core.ui.preferences.LocalPreferences
import app.pachli.core.ui.preferences.PreferencesProvider

/**
 * Main Pachli theme.
 *
 * Composables in [content] have access to [LocalPreferences], [PachliColors], and
 * [MaterialTheme].
 *
 * @param systemInDarkTheme True if the device is currently using a dark theme.
 * @param dynamicColor True if dynamic colour is available.
 * @param content
 */
@Composable
fun PachliTheme(
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    PreferencesProvider {
//        val colorScheme = when {
//            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//                val context = LocalContext.current
//                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//            }
//
//            darkTheme -> darkScheme
//            else -> lightScheme
//        }

        val materialColorScheme = when (LocalPreferences.current.theme) {
            AppTheme.NIGHT -> darkMaterialScheme
            AppTheme.DAY -> lightMaterialScheme
            AppTheme.BLACK -> blackMaterialScheme
            AppTheme.AUTO -> if (systemInDarkTheme) darkMaterialScheme else lightMaterialScheme
            AppTheme.AUTO_SYSTEM -> if (systemInDarkTheme) darkMaterialScheme else lightMaterialScheme
        }

        val pachliColorScheme = when (LocalPreferences.current.theme) {
            AppTheme.NIGHT -> darkPachliScheme
            AppTheme.DAY -> lightPachliScheme
            AppTheme.BLACK -> blackPachliScheme
            AppTheme.AUTO -> if (systemInDarkTheme) darkPachliScheme else lightPachliScheme
            AppTheme.AUTO_SYSTEM -> if (systemInDarkTheme) darkPachliScheme else lightPachliScheme
        }

        CompositionLocalProvider(PachliColors provides pachliColorScheme) {
            MaterialTheme(
                colorScheme = materialColorScheme,
                typography = AppTypography,
                content = content,
            )
        }
    }
}

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun PreviewPachliTheme(
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val materialColorScheme = if (systemInDarkTheme) darkMaterialScheme else lightMaterialScheme
    val pachliColorScheme = if (systemInDarkTheme) darkPachliScheme else lightPachliScheme

    CompositionLocalProvider(PachliColors provides pachliColorScheme) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography,
            content = {
                Surface(color = colorScheme.background, content = content)
            },
        )
    }
}
