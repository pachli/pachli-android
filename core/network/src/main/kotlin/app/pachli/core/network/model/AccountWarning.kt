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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Moderation warning against a particular account.
 *
 * @property id Server ID for the account warning.
 * @property action Action taken against the account.
 * @property text Message from the moderator to the target account.
 * @property createdAt When the event took place..
 */
@JsonClass(generateAdapter = true)
data class AccountWarning(
    val id: String,
    val action: Action = Action.UNKNOWN,
    val text: String,
    @Json(name = "created_at")
    val createdAt: Instant,
) {
    @HasDefault
    enum class Action {
        /** No action was taken, this is a simple warning. */
        @Json(name = "none")
        NONE,

        /** The account has been disabled. */
        @Json(name = "disable")
        DISABLE,

        /** Specific posts from the target account have been marked as sensitive. */
        @Json(name = "mark_statuses_as_sensitive")
        MARK_STATUSES_AS_SENSITIVE,

        /** Specific statuses from the target account have been deleted. */
        @Json(name = "delete_statuses")
        DELETE_STATUSES,

        /** The target account has been limited. */
        @Json(name = "silence")
        SILENCE,

        /** The target account has been suspended. */
        @Json(name = "suspend")
        SUSPEND,

        /** Unknown action. */
        @Default
        UNKNOWN,

        ;

        fun asModel() = when (this) {
            NONE -> app.pachli.core.model.AccountWarning.Action.NONE
            DISABLE -> app.pachli.core.model.AccountWarning.Action.DISABLE
            MARK_STATUSES_AS_SENSITIVE -> app.pachli.core.model.AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE
            DELETE_STATUSES -> app.pachli.core.model.AccountWarning.Action.DELETE_STATUSES
            SILENCE -> app.pachli.core.model.AccountWarning.Action.SILENCE
            SUSPEND -> app.pachli.core.model.AccountWarning.Action.SUSPEND
            UNKNOWN -> app.pachli.core.model.AccountWarning.Action.UNKNOWN
        }
    }

    @JsonClass(generateAdapter = true)
    data class Appeal(
        val text: String,
        val state: State = State.UNKNOWN,
    ) {
        @HasDefault
        enum class State {
            /** The appeal has been approved by a moderator. */
            @Json(name = "approved")
            APPROVED,

            /** The appeal has been rejected by a moderator. */
            @Json(name = "rejected")
            REJECTED,

            /** The appeal has been submitted, but neither approved nor rejected yet. */
            @Json(name = "pending")
            PENDING,

            @Default
            UNKNOWN,

            ;

            fun asModel() = when (this) {
                APPROVED -> app.pachli.core.model.AccountWarning.Appeal.State.APPROVED
                REJECTED -> app.pachli.core.model.AccountWarning.Appeal.State.REJECTED
                PENDING -> app.pachli.core.model.AccountWarning.Appeal.State.PENDING
                UNKNOWN -> app.pachli.core.model.AccountWarning.Appeal.State.UNKNOWN
            }
        }

        fun asModel() = app.pachli.core.model.AccountWarning.Appeal(
            text = text,
            state = state.asModel(),
        )
    }

    fun asModel() = app.pachli.core.model.AccountWarning(
        id = id,
        action = action.asModel(),
        text = text,
        createdAt = createdAt,
    )
}
