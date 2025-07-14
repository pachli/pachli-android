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
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
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
 * Listener for [WindowInsetsCompat].
 *
 * @see View.applyWindowInsets
 * @see WindowInsetsAnimationCallback
 */
typealias InsetsListener = (WindowInsetsCompat) -> Unit

/**
 * Listens for window insets (system bars and displayCutout, IME if [withIme] is
 * true) and applies the discovered insets to the left, top, right, and bottom
 * edges of the view.
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
 * @param withIme True if the IME's insets should also be applied. If true
 * the view will also animate into the new position as the IME animates
 * into position.
 */
fun View.applyWindowInsets(
    left: InsetType? = null,
    top: InsetType? = null,
    right: InsetType? = null,
    bottom: InsetType? = null,
    consume: Boolean = this !is ViewPager2,
    withIme: Boolean = false,
) {
    // Store the view's initial margin and padding for use in the listener.
    val lp = layoutParams as? ViewGroup.MarginLayoutParams
    val initialMargins = Rect(
        lp?.leftMargin ?: 0,
        lp?.topMargin ?: 0,
        lp?.rightMargin ?: 0,
        lp?.bottomMargin ?: 0,
    )
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)

    // Listen for new insets and apply them by adding to the view's initial margin
    // or padding, optionally consuming them.
    val insetsListener: InsetsListener = { windowInsets ->
        var typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (withIme) typeMask = typeMask or WindowInsetsCompat.Type.ime()
        val systemInsets = windowInsets.getInsets(typeMask)

        val rect = Rect()
        rect.left = if (left == InsetType.PADDING) systemInsets.left else 0
        rect.top = if (top == InsetType.PADDING) systemInsets.top else 0
        rect.right = if (right == InsetType.PADDING) systemInsets.right else 0
        rect.bottom = if (bottom == InsetType.PADDING) systemInsets.bottom else 0

        this@applyWindowInsets.setPadding(
            initialPadding.left + rect.left,
            initialPadding.top + rect.top,
            initialPadding.right + rect.right,
            initialPadding.bottom + rect.bottom,
        )

        // A view might not be in a group that supports margins (e.g., a
        // RecyclerView in a SwipeRefreshLayout has no margins), so only
        // apply margins if the layout params are appropriate.
        if (layoutParams as? ViewGroup.MarginLayoutParams != null) {
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
        }
    }

    val callback = WindowInsetsAnimationCallback(consume, insetsListener)
    ViewCompat.setOnApplyWindowInsetsListener(this, callback)
    ViewCompat.setWindowInsetsAnimationCallback(this, callback)

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
    left = InsetType.PADDING,
    top = InsetType.PADDING,
    right = InsetType.PADDING,
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
 * left, right, and bottom edges.
 */
fun RecyclerView.applyDefaultWindowInsets() {
    applyWindowInsets(
        left = InsetType.PADDING,
        right = InsetType.PADDING,
        bottom = InsetType.PADDING,
    )
    clipToPadding = false
}

/**
 * Callback that applies insets whether they are from
 * [ViewCompat.setOnApplyWindowInsetsListener] or
 * [ViewCompat.setWindowInsetsAnimationCallback].
 */
private class WindowInsetsAnimationCallback(
    private val consume: Boolean,
    private val listener: InsetsListener,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP),
    OnApplyWindowInsetsListener {
    /** Insets to apply (if any) when the animation completes. */
    private var deferredInsets: WindowInsetsCompat? = null

    /** True if animation is in progress. */
    private var isAnimating = false

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        isAnimating = true
    }

    override fun onApplyWindowInsets(
        view: View,
        insets: WindowInsetsCompat,
    ): WindowInsetsCompat {
        if (isAnimating) {
            // If animating then this is called after [onPrepare] with the
            // end state insets. These insets can't be used now, but save
            // them for use in [onEnd].
            deferredInsets = insets
        } else {
            // Otherwise, clear and call the listener directly.
            deferredInsets = null
            listener(insets)
        }
        return if (consume) WindowInsetsCompat.CONSUMED else insets
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat>,
    ): WindowInsetsCompat {
        listener(insets)
        return if (consume) WindowInsetsCompat.CONSUMED else insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        // Animation has finished. If deferredInsets exist (because onApplyWindowInsets
        // was called while animating) then call the listener now to apply the final
        // insets.
        deferredInsets?.let { insets ->
            listener(insets)
            deferredInsets = null
        }
        isAnimating = false
    }
}
