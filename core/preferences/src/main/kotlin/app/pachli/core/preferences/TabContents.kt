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

enum class TabContents(
    override val displayResource: Int,
    override val value: String? = null,
) : PreferenceEnum {
    ICON_ONLY(R.string.pref_tab_contents_icon_only),
    TEXT_ONLY(R.string.pref_tab_contents_text_only),
    ICON_TEXT_INLINE(R.string.pref_tab_contents_icon_text_inline),
    ICON_TEXT_BELOW(R.string.pref_tab_contents_icon_text_below),
}
