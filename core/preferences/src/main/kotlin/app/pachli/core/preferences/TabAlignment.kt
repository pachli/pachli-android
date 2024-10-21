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

enum class TabAlignment(
    override val displayResource: Int,
    override val value: String? = null,
) : PreferenceEnum {
    /**
     * Tabs take required width, align with start of writing direction
     * (i.e., left in LTR locales, right in RTL locales).
     */
    START(R.string.pref_tab_alignment_start),

    /**
     * Tabs expand to fill available width, if space.
     */
    JUSTIFY_IF_POSSIBLE(R.string.pref_tab_alignment_justify_if_possible),

    /**
     * Tabs take required width, align with end of writing direction
     * (i.e., left in LTR locales, right in RTL locales).
     */
    END(R.string.pref_tab_alignment_end),
}
