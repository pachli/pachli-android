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

package app.pachli.core.preferences

/** Behaviour when the user taps on a tab. */
enum class TabTapBehaviour(override val displayResource: Int, override val value: String? = null) :
    PreferenceEnum {
    /** Jump the user's position to the top, fetching the next page of content. */
    JUMP_TO_NEXT_PAGE(R.string.tab_tap_behaviour_jump_to_next_page),

    /** Fetch the newest page of content and jump to that. */
    JUMP_TO_NEWEST(R.string.tab_tap_behaviour_jump_to_newest),
}
