/*
 * Copyright (c) 2025 Pachli Association
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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import javax.annotation.CheckReturnValue

/**
 * Recommended animator for a view when dragging starts.
 *
 * - Zooms the view by a factor of 1.02
 * - Sets the elevation to [dragElevation]
 *
 * @param view View to operate on.
 * @param dragElevation Elevation to set.
 * @param onStart Optional function to run when animation starts.
 * E.g., to set the [clipChildren][android.view.ViewGroup.clipChildren]
 * property on further up the view hierarchy.
 * @param onEnd Optional function to run when animation ends.
 *
 * @see androidx.recyclerview.widget.ItemTouchHelper.Callback.onSelectedChanged
 */
@CheckReturnValue
fun startDragAnimator(
    view: View,
    dragElevation: Float,
    onStart: ((animator: Animator) -> Unit)? = null,
    onEnd: ((animator: Animator) -> Unit)? = null,
) = AnimatorSet().apply {
    duration = 100
    interpolator = AccelerateDecelerateInterpolator()
    playTogether(
        scaleX(view, 1.02f),
        scaleY(view, 1.02f),
        translateZ(view, dragElevation),
    )

    onStart?.let { doOnStart { it(this) } }

    onEnd?.let { doOnEnd { it(this) } }
}

/**
 * Recommended animator for a view when dragging is cleared.
 *
 * Animates reversing the scale and elevation changes made by
 * [startDragAnimator].
 *
 * @param view View to operate on.
 * @param onStart Optional function to run when animation starts.
 * @param onEnd Optional function to run when animation ends.
 *
 * @see androidx.recyclerview.widget.ItemTouchHelper.Callback.clearView
 */
@CheckReturnValue
fun clearDragAnimator(
    view: View,
    onStart: ((animator: Animator) -> Unit)? = null,
    onEnd: ((animator: Animator) -> Unit)? = null,
) = AnimatorSet().apply {
    // 2x speed of startDragAnimator, for snappier behaviour.
    duration = 50
    interpolator = AccelerateDecelerateInterpolator()
    playTogether(
        scaleX(view, 1f),
        scaleY(view, 1f),
        translateZ(view, 0f),
    )

    onStart?.let { doOnStart { it(this) } }

    onEnd?.let { doOnEnd { it(this) } }
}

/**
 * @return [Animator] that changes the [scaleX][View.getScaleX] property
 * of [view] to [scaleTo].
 */
private fun scaleX(view: View, scaleTo: Float): Animator {
    return ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, scaleTo)
}

/**
 * @return [Animator] that changes the [scaleY][View.getScaleY] property
 * of [view] to [scaleTo].
 */
private fun scaleY(view: View, scaleTo: Float): Animator {
    return ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, scaleTo)
}

/**
 * @return [Animator] that changes the [translationZ][View.getTranslationZ]
 * property of [view] to [translateTo].
 */
private fun translateZ(view: View, translateTo: Float): Animator {
    return ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, view.translationZ, translateTo)
}
