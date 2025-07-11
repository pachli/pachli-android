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
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

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
 * By default, insets are consumed if this view is **not** a [ViewPager2] to
 * ensure that insets are available to ViewPager2 children (e.g., a
 * recylerview in a ViewPager2), otherwise insets **are** consumed. Change this
 * with the [consume] parameter.
 *
 * @param left
 * @param top
 * @param right
 * @param bottom
 * @param consume True if the insets should be consumed.
 */
fun View.applyWindowInsets(
    left: InsetType? = null,
    top: InsetType? = null,
    right: InsetType? = null,
    bottom: InsetType? = null,
    consume: Boolean = this !is ViewPager2,
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

        return@setOnApplyWindowInsetsListener if (consume) WindowInsetsCompat.CONSUMED else windowInsets
    }

    // Work around https://issuetracker.google.com/issues/145617093, insets may be
    // dispatched before ViewPager2 has added pages to the view hierarchy so the
    // insets are never applied.
    if (this is ViewPager2) requestApplyInsetsWhenAttached()
}

/**
 * Ensures insets are applied even if the view is not currently attached.
 *
 * If the view is attached the insets are applied. If the view is not attached
 * install a [View.OnAttachStateChangeListener] and apply the insets after
 * the view is attached.
 *
 * See [issuetracker/145617093](https://issuetracker.google.com/issues/145617093)
 */
private fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(ApplyInsetsOnViewAttachedToWindow)
    }
}

/** Listener that applies insets after the view is attached to the window. */
object ApplyInsetsOnViewAttachedToWindow : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View) {
        v.removeOnAttachStateChangeListener(this)
        v.requestApplyInsets()
    }

    override fun onViewDetachedFromWindow(v: View) = Unit
}

fun Toolbar.addScrollEffect(scrollEffect: AppBarLayout.ChildScrollEffect) {
    updateLayoutParams<AppBarLayout.LayoutParams> { this.scrollEffect = scrollEffect }
}

/**
 * Applies window insets to [AppBarLayout], adding extra margin to the left
 * and right edges, and extra padding to the top edge.
 */
fun AppBarLayout.applyDefaultWindowInsets() = applyWindowInsets(
    left = InsetType.MARGIN,
    top = InsetType.PADDING,
    right = InsetType.MARGIN,
)

/**
 * Applies window insets to [FloatingActionButton], adding extra margin
 * on the right and bottom edges.
 */
fun FloatingActionButton.applyDefaultWindowInsets() = applyWindowInsets(
    right = InsetType.MARGIN,
    bottom = InsetType.MARGIN,
)

/**
 * Applies window insets to [ViewPager2], adding extra margin on the
 * left and right edges.
 */
fun ViewPager2.applyDefaultWindowInsets() = applyWindowInsets(
    left = InsetType.MARGIN,
    right = InsetType.MARGIN,
)

/**
 * Applies window insets to [RecyclerView], adding extra padding on
 * bottom edge.
 */
fun RecyclerView.applyDefaultWindowInsets() {
    applyWindowInsets(bottom = InsetType.PADDING)
    clipToPadding = false
}
