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

package app.pachli.core.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * [RecyclerView.OnScrollListener] for [FloatingActionButton].
 *
 * If [showActionButtonWhileScrolling] is true then the action button is always
 * shown, otherwise it is hidden while the [RecyclerView] is scrolling and shown
 * when it returns to [SCROLL_STATE_IDLE].
 *
 * @param button The [FloatingActionButton] to control
 * @property showActionButtonWhileScrolling True if [button] should be shown
 *     while scrolling, false otherwise
 *
 */
class ActionButtonScrollListener(val button: FloatingActionButton) : RecyclerView.OnScrollListener() {
    var showActionButtonWhileScrolling: Boolean = false
        set(value) {
            field = value
            if (value && button.isOrWillBeHidden) button.show()
        }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (showActionButtonWhileScrolling) {
            if (button.isOrWillBeHidden) button.show()
            return
        }

        when (newState) {
            SCROLL_STATE_IDLE -> button.show()
            else -> button.hide()
        }
    }
}
