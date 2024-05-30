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

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.app.Activity.OVERRIDE_TRANSITION_OPEN
import android.content.Intent
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.BuildConfig

/**
 * Starts the activity in [intent]. Should only be called on subclasses of [BaseActivity].
 *
 * If the activity is a Pachli activity then [transitionKind] is included in the intent and
 * used as the open/close transition.
 *
 * See [BaseActivity.onCreate] for the other half of this code.
 */
fun Activity.startActivityWithTransition(intent: Intent, transitionKind: TransitionKind) {
    if (BuildConfig.DEBUG) {
        if (this !is BaseActivity) {
            throw IllegalStateException("startActivityWithTransition must be used with BaseActivity subclass")
        }
    }

    intent.putExtra(EXTRA_TRANSITION_KIND, transitionKind)
    startActivity(intent)

    if (canOverrideActivityTransitions()) {
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, transitionKind.openEnter, transitionKind.openExit)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(transitionKind.openEnter, transitionKind.openExit)
    }
}

/** See [Activity.startActivityWithTransition] */
fun Activity.startActivityWithDefaultTransition(intent: Intent) = startActivityWithTransition(intent, TransitionKind.DEFAULT)

/**
 * Overrides any "close" transition already set for this activity and
 * replaces them with [transitionKind].
 *
 * Call this after calling [Activity.finish]
 */
fun Activity.setCloseTransition(transitionKind: TransitionKind) {
    if (BuildConfig.DEBUG) {
        if (this !is BaseActivity) {
            throw IllegalStateException("setCloseTransition must be used with BaseActivity subclass")
        }
    }

    if (canOverrideActivityTransitions()) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, transitionKind.closeEnter, transitionKind.closeExit)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(transitionKind.closeEnter, transitionKind.closeExit)
    }
}

/**
 * @return True if the Android version supports [Activity.overrideActivityTransition],
 *   false if [Activity.overridePendingTransition] must be used.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun canOverrideActivityTransitions() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
