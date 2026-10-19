/*
 * Copyright (c) 2026 Pachli Association
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

/** Whether the user should be prompted to confirm boosts. */
enum class ConfirmReblogs(override val displayResource: Int, override val value: String?) : PreferenceEnum {
    /** Never prompt for confirmation. */
    NEVER(R.string.pref_confirm_reblog_never, null),

    /** Prompt if the to-be-boosted-post has one or more uncaptioned attachments. */
    UNCAPTIONED(R.string.pref_confirm_reblog_uncaptioned, null),

    /** Always prompt for confirmation. */
    ALWAYS(R.string.pref_confirm_reblog_always, null),
}
