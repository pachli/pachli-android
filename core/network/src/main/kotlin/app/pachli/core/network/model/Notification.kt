/*
 * Copyright 2017 Andrew Dawson
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
import java.util.Date

// TODO: These should be different subclasses per type, so that each subclass can
// carry the non-null data that it needs.
@JsonClass(generateAdapter = true)
data class Notification(
    val type: Type,
    val id: String,
    @Json(name = "created_at") val createdAt: Date,
    val account: TimelineAccount,
    val status: Status?,
    val report: Report?,
    @Json(name = "event")
    val relationshipSeveranceEvent: RelationshipSeveranceEvent? = null,
    @Json(name = "moderation_warning")
    val accountWarning: AccountWarning? = null,
) {

    /** From https://docs.joinmastodon.org/entities/Notification/#type */
    @JsonClass(generateAdapter = false)
    @HasDefault
    enum class Type(val presentation: String) {
        @Json(name = "unknown")
        @Default
        UNKNOWN("unknown"),

        /** Someone mentioned you */
        @Json(name = "mention")
        MENTION("mention"),

        /** Someone boosted one of your statuses */
        @Json(name = "reblog")
        REBLOG("reblog"),

        /** Someone favourited one of your statuses */
        @Json(name = "favourite")
        FAVOURITE("favourite"),

        /** Someone followed you */
        @Json(name = "follow")
        FOLLOW("follow"),

        /** Someone requested to follow you */
        @Json(name = "follow_request")
        FOLLOW_REQUEST("follow_request"),

        /** A poll you have voted in or created has ended */
        @Json(name = "poll")
        POLL("poll"),

        /** Someone you enabled notifications for has posted a status */
        @Json(name = "status")
        STATUS("status"),

        /** Someone signed up (optionally sent to admins) */
        @Json(name = "admin.sign_up")
        SIGN_UP("admin.sign_up"),

        /** A status you reblogged has been updated */
        @Json(name = "update")
        UPDATE("update"),

        /** A new report has been filed */
        @Json(name = "admin.report")
        REPORT("admin.report"),

        /** Some of your follow relationships have been severed as a result of a moderation or block event */
        @Json(name = "severed_relationships")
        SEVERED_RELATIONSHIPS("severed_relationships"),

        /** A moderator has taken action against your account or has sent you a warning. */
        @Json(name = "moderation_warning")
        MODERATION_WARNING("moderation_warning"),
        ;

        companion object {
            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = Type.entries.filter { it != UNKNOWN }
        }

        override fun toString(): String {
            return presentation
        }

        fun asModel(): app.pachli.core.model.Notification.Type = when (this) {
            UNKNOWN -> app.pachli.core.model.Notification.Type.UNKNOWN
            MENTION -> app.pachli.core.model.Notification.Type.MENTION
            REBLOG -> app.pachli.core.model.Notification.Type.REBLOG
            FAVOURITE -> app.pachli.core.model.Notification.Type.FAVOURITE
            FOLLOW -> app.pachli.core.model.Notification.Type.FOLLOW
            FOLLOW_REQUEST -> app.pachli.core.model.Notification.Type.FOLLOW_REQUEST
            POLL -> app.pachli.core.model.Notification.Type.POLL
            STATUS -> app.pachli.core.model.Notification.Type.STATUS
            SIGN_UP -> app.pachli.core.model.Notification.Type.SIGN_UP
            UPDATE -> app.pachli.core.model.Notification.Type.UPDATE
            REPORT -> app.pachli.core.model.Notification.Type.REPORT
            SEVERED_RELATIONSHIPS -> app.pachli.core.model.Notification.Type.SEVERED_RELATIONSHIPS
        }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Notification) {
            return false
        }
        val notification = other as Notification?
        return notification?.id == this.id
    }

    fun asModel() = app.pachli.core.model.Notification(
        type = type.asModel(),
        id = id,
        createdAt = createdAt,
        account = account.asModel(),
        status = status?.asModel(),
        report = report?.asModel(),
        relationshipSeveranceEvent = relationshipSeveranceEvent?.asModel(),
    )
}
