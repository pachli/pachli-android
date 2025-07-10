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

package app.pachli.core.ui.extensions

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Defines the operation to be performed with the inset.
 *
 * @see View.applyWindowInsets
 */
enum class InsetType {
    /** Apply inset dimensions on this edge as padding. */
    PADDING,

    /** Apply inset dimensions on this edge as margin. */
    MARGIN,
}

/**
 * Listens for window insets (system bars and displayCutout) and applies the
 * discovered insets to the left, top, right, and bottom edges of the view.
 *
 * The parameters control how each inset is applied. If null the inset is not
 * applied to the that edge. Otherwise the inset is applied by either extending
 * the [padding][InsetType.PADDING] or [margin][InsetType.MARGIN] of the view.
 *
 * @param left
 * @param top
 * @param right
 * @param bottom
 */
fun View.applyWindowInsets(
    left: InsetType? = null,
    top: InsetType? = null,
    right: InsetType? = null,
    bottom: InsetType? = null,
) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams

    val initialMargins = Rect(
        lp?.leftMargin ?: 0,
        lp?.topMargin ?: 0,
        lp?.rightMargin ?: 0,
        lp?.bottomMargin ?: 0,
    )

    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)

    val rect = Rect()

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val systemInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )

        rect.left = if (left == InsetType.PADDING) systemInsets.left else 0
        rect.top = if (top == InsetType.PADDING) systemInsets.top else 0
        rect.right = if (right == InsetType.PADDING) systemInsets.right else 0
        rect.bottom = if (bottom == InsetType.PADDING) systemInsets.bottom else 0

        v.setPadding(
            initialPadding.left + rect.left,
            initialPadding.top + rect.top,
            initialPadding.right + rect.right,
            initialPadding.bottom + rect.bottom,
        )

        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            rect.left = if (left == InsetType.MARGIN) systemInsets.left else 0
            rect.top = if (top == InsetType.MARGIN) systemInsets.top else 0
            rect.right = if (right == InsetType.MARGIN) systemInsets.right else 0
            rect.bottom = if (bottom == InsetType.MARGIN) systemInsets.bottom else 0

            leftMargin = initialMargins.left + rect.left
            topMargin = initialMargins.top + rect.top
            rightMargin = initialMargins.right + rect.right
            bottomMargin = initialMargins.bottom + rect.bottom
        }

        return@setOnApplyWindowInsetsListener WindowInsetsCompat.CONSUMED
    }
}
