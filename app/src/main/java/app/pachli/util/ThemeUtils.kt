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

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.use
import app.pachli.core.preferences.AppTheme
import com.google.android.material.color.MaterialColors

/**
 * Provides runtime compatibility to obtain theme information and re-theme views, especially where
 * the ability to do so is not supported in resource files.
 */

fun getDimension(context: Context, @AttrRes attribute: Int): Int {
    return context.obtainStyledAttributes(intArrayOf(attribute)).use { array ->
        array.getDimensionPixelSize(0, -1)
    }
}

fun setDrawableTint(context: Context, drawable: Drawable, @AttrRes attribute: Int) {
    drawable.setColorFilter(
        MaterialColors.getColor(context, attribute, Color.BLACK),
        PorterDuff.Mode.SRC_IN,
    )
}

fun setAppNightMode(flavor: AppTheme) {
    when (flavor) {
        AppTheme.NIGHT, AppTheme.BLACK -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_YES,
        )
        AppTheme.DAY -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        AppTheme.AUTO -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_AUTO_TIME,
        )
        AppTheme.AUTO_SYSTEM -> AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
    }
}
