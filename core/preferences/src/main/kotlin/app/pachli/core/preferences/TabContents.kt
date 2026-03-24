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

/**
 * How to display the tab in the tab strip.
 */
enum class TabContents(
    override val displayResource: Int,
    override val value: String? = null,
) : PreferenceEnum {
    /** Display the tab with the icon only. */
    ICON_ONLY(R.string.pref_tab_contents_icon_only),

    /** Display the tab with the descriptive text. */
    TEXT_ONLY(R.string.pref_tab_contents_text_only),

    /** Display the tab with the icon first, then the descriptive text. */
    ICON_TEXT_INLINE(R.string.pref_tab_contents_icon_text_inline),

    /**
     * Display the tab as two lines, the icon on the first line, the
     * descriptive text on the second line.
     */
    ICON_TEXT_BELOW(R.string.pref_tab_contents_icon_text_below),
}
