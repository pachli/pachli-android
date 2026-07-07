/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.model

import java.time.Instant

// Some network fields are ignored, as they are not used in the UI.
data class AccountWarning(
    val id: String,
    val action: Action,
    val text: String,
    val createdAt: Instant,

) {
    enum class Action {
        /** No action was taken, this is a simple warning. */
        NONE,

        /** The account has been disabled. */
        DISABLE,

        /** Specific posts from the target account have been marked as sensitive. */

        MARK_STATUSES_AS_SENSITIVE,

        /** Specific statuses from the target account have been deleted. */
        DELETE_STATUSES,

        /** The target account has been limited. */
        SILENCE,

        /** The target account has been suspended. */
        SUSPEND,
        UNKNOWN,
    }
}
