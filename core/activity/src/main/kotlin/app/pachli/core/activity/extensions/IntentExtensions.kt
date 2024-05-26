/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.activity.extensions

import android.content.Intent
import androidx.annotation.AnimRes
import app.pachli.core.designsystem.R as DR

const val EXTRA_TRANSITION_KIND_NAME = "transition_kind_name"

/**
 * The type of transition and animation resources to use when opening and closing
 * an activity.
 *
 * @property openEnter When opening an activity, a resource ID of the animation resource to
 *     use for the incoming activity. Use 0 for no animation.
 * @property openExit When opening an activity, a resource ID of the animation resource to
 *     use for the outgoing activity. Use 0 for no animation
 * @property closeEnter When closing an activity, a resource ID of the animation resource to
 *     use for the incoming activity. Use 0 for no animation.
 * @property closeExit When closing an activity, a resource ID of the animation resource to
 *     use for the outgoing activity. Use 0 for no animation
 */
enum class TransitionKind(
    @AnimRes val openEnter: Int,
    @AnimRes val openExit: Int,
    @AnimRes val closeEnter: Int,
    @AnimRes val closeExit: Int,
) {
    /** Default transition */
    DEFAULT(DR.anim.activity_open_enter, DR.anim.activity_open_exit, DR.anim.activity_close_enter, DR.anim.activity_close_exit),

    /**
     * Slide from the user's "end" perspective (right side for LTR text, left side for RTL text).
     * Use when a spatial relationship makes sense, such as transitioning from a single status
     * to the thread that contains that status.
     */
    SLIDE_FROM_END(DR.anim.slide_from_end, DR.anim.slide_to_start, DR.anim.slide_from_start, DR.anim.slide_to_end),

    /**
     * Explode out from the centre of the screen. Use to indicate a significant change in
     * application state (e.g., changing accounts).
     */
    EXPLODE(DR.anim.explode, DR.anim.activity_open_exit, 0, 0),
}

/** @return The [TransitionKind] included in this intent, or null */
fun Intent.getTransitionKind(): TransitionKind? {
    return getStringExtra(EXTRA_TRANSITION_KIND_NAME)?.let { TransitionKind.valueOf(it) }
}
