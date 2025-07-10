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

package app.pachli.core.ui.appbar

import android.view.View
import app.pachli.core.ui.appbar.FadeChildScrollEffect.END_DEAD_ZONE
import app.pachli.core.ui.appbar.FadeChildScrollEffect.START_DEAD_ZONE
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs

/**
 * Updates an [AppBarLayout] child to fade (change opacity) relative to the
 * [AppBarLayout]'s scroll offset, from fully opaque (no scroll offset) to
 * full transparent.
 *
 * The opacity starts to change after the [AppBarLayout] has scrolled at least
 * [START_DEAD_ZONE] pixels, and the child becomes fully transparent [END_DEAD_ZONE]
 * pixels before the scroll effect completes.
 *
 * Example:
 *
 * ```kotlin
 * binding.toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
 *     scrollEffect = FadeChildScrollEffect
 * }
 * ```
 */
object FadeChildScrollEffect : AppBarLayout.ChildScrollEffect() {
    const val START_DEAD_ZONE = 10
    const val END_DEAD_ZONE = 10

    override fun onOffsetChanged(appBarLayout: AppBarLayout, child: View, offset: Float) {
        val totalScrollRange = appBarLayout.totalScrollRange

        val absOffset = abs(offset)
        val opacity = when {
            absOffset <= START_DEAD_ZONE -> 1f
            absOffset >= totalScrollRange - END_DEAD_ZONE -> 0f
            else -> {
                val effectiveRange = totalScrollRange - START_DEAD_ZONE - END_DEAD_ZONE
                val effectiveOffset = absOffset - START_DEAD_ZONE - END_DEAD_ZONE
                val pctOffset = effectiveOffset / effectiveRange
                1 - pctOffset
            }
        }

        child.alpha = opacity
    }
}
