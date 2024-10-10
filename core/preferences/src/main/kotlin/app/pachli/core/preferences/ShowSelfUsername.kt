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

/** When to show the account's username in the title. */
enum class ShowSelfUsername(override val displayResource: Int, override val value: String? = null) :
    PreferenceEnum {
    /** Always show the username. */
    ALWAYS(R.string.pref_show_self_username_always, "always"),

    /** Show the username if more than one account is logged in. */
    DISAMBIGUATE(R.string.pref_show_self_username_disambiguate, "disambiguate"),

    /** Never show the username. */
    NEVER(R.string.pref_show_self_username_never, "never"),
}
