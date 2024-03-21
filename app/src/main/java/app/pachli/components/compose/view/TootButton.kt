/* Copyright 2018 Conny Duck
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

package app.pachli.components.compose.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import app.pachli.R
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.Status
import app.pachli.util.iconDrawable
import com.google.android.material.button.MaterialButton

class TootButton
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialButton(context, attrs, defStyleAttr) {

    private val smallStyle: Boolean = context.resources.getBoolean(DR.bool.show_small_toot_button)

    init {
        if (smallStyle) {
            setIconResource(R.drawable.ic_send_24dp)
        } else {
            setText(R.string.action_send)
            iconGravity = ICON_GRAVITY_TEXT_START
        }
        val padding = resources.getDimensionPixelSize(DR.dimen.toot_button_horizontal_padding)
        setPadding(padding, 0, padding, 0)
    }

    fun setStatusVisibility(view: View, visibility: Status.Visibility) {
        if (!smallStyle) {
            icon = visibility.iconDrawable(view)

            when (visibility) {
                Status.Visibility.UNKNOWN -> { /* do nothing */ }
                Status.Visibility.PUBLIC -> setText(R.string.action_send_public)
                Status.Visibility.UNLISTED -> setText(R.string.action_send)
                Status.Visibility.PRIVATE -> setText(R.string.action_send)
                Status.Visibility.DIRECT -> setText(R.string.action_send)
            }
        }
    }
}
