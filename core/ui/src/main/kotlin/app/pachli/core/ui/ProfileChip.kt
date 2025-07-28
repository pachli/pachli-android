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

package app.pachli.core.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.view.updatePadding
import app.pachli.core.designsystem.R as DR
import com.google.android.material.R
import com.google.android.material.chip.Chip

/**
 * A [Chip] for showing profile data.
 *
 * The chip is styled and sized appropriately, and functionality for clicking
 * the chip is disabled.
 *
 * The caller must still set the text and icon on the chip.
 *
 * Example:
 *
 * ```kotlin
 * val chip = ProfileChip(context).apply {
 *     text = "some text",
 *     setChipIconResource(R.drawable.some_drawable)
 * }
 * ```
 */
open class ProfileChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.chipStyle,
) : Chip(context, attrs, defStyleAttr) {
    init {
        isChipIconVisible = true
        setChipStrokeWidthResource(app.pachli.core.designsystem.R.dimen.profile_badge_stroke_width)
        setChipIconSizeResource(app.pachli.core.designsystem.R.dimen.profile_badge_icon_size)

        val textSize = context.obtainStyledAttributes(null, intArrayOf(DR.attr.status_text_small)).use {
            it.getDimension(0, -1f)
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

        // Badge isn't clickable, so disable all related behaviour.
        isClickable = false
        isFocusable = false
        setEnsureMinTouchTargetSize(false)

        // Reset some defaults
        setIconStartPaddingResource(app.pachli.core.designsystem.R.dimen.profile_badge_icon_start_padding)
        setIconEndPaddingResource(app.pachli.core.designsystem.R.dimen.profile_badge_icon_end_padding)
        setChipMinHeightResource(app.pachli.core.designsystem.R.dimen.profile_badge_min_height)
        minHeight = resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.profile_badge_min_height)
        updatePadding(top = 0, bottom = 0)
    }
}
