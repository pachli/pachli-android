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

package app.pachli.core.activity.extensions

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import app.pachli.core.activity.BaseActivity

fun Fragment.startActivityWithDefaultTransition(intent: Intent, options: Bundle? = null) {
    val activity = requireActivity() as? BaseActivity

    if (activity != null && intent.forPachliComponent) {
        activity.startActivityWithDefaultTransition(intent, options)
    } else {
        startActivity(intent, options)
    }
}

/**
 * Starts the activity in [intent].
 *
 * If the activity is a Pachli activity then [transitionKind] is included in the intent and
 * used as the open/close transition.
 *
 * See [BaseActivity.onCreate] for the other half of this code.
 */
fun Fragment.startActivityWithTransition(intent: Intent, transitionKind: TransitionKind, options: Bundle? = null) {
    val activity = requireActivity() as? BaseActivity

    if (activity != null && intent.forPachliComponent) {
        activity.startActivityWithTransition(intent, transitionKind, options)
    } else {
        startActivity(intent, options)
    }
}
