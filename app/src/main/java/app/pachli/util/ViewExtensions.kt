/* Copyright 2017 Andrew Dawson
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

package app.pachli.util

import android.widget.TextView

/**
 * TextViews with an ancestor RecyclerView can forget that they are selectable. Toggling
 * calls to [TextView.setTextIsSelectable] fixes this.
 *
 * See [https://issuetracker.google.com/issues/37095917](https://issuetracker.google.com/issues/37095917)
 */
fun TextView.fixTextSelection() {
    setTextIsSelectable(false)
    post { setTextIsSelectable(true) }
}
