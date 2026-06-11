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

        /** Someone quoted one of your posts. */
        @Json(name = "quote")
        QUOTE("quote"),

        /** A post you quoted has been updated. */
        @Json(name = "quoted_update")
        QUOTED_UPDATE("quoted_update"),
        ;

        companion object {
            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = Type.entries.filter { it != UNKNOWN }

            @JvmStatic
            fun byString(s: String) = entries.firstOrNull { s == it.presentation } ?: UNKNOWN
        }

        override fun toString(): String {
            return presentation
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

    fun asModel(accountId: String): app.pachli.core.model.Notification {
        // Pleroma uses 'Mention' type for mentions and subscribed status updates.
        // Split out depending on whether the user is mentioned in the status.

        val type = if (type == Type.MENTION && status != null) {
            if (status.mentions.any { it.id == accountId }) this.type else Type.STATUS
        } else {
            this.type
        }

        return when (type) {
            Type.UNKNOWN -> app.pachli.core.model.Notification.Unknown(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                remoteType = type.presentation,
            )

            Type.MENTION -> app.pachli.core.model.Notification.Mention(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.REBLOG -> app.pachli.core.model.Notification.Reblog(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.FAVOURITE -> app.pachli.core.model.Notification.Favourite(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.FOLLOW -> app.pachli.core.model.Notification.Follow(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
            )

            Type.FOLLOW_REQUEST -> app.pachli.core.model.Notification.FollowRequest(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
            )

            Type.POLL -> app.pachli.core.model.Notification.Poll(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.STATUS -> app.pachli.core.model.Notification.Status(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.SIGN_UP -> app.pachli.core.model.Notification.SignUp(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
            )

            Type.UPDATE -> app.pachli.core.model.Notification.Update(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.REPORT -> app.pachli.core.model.Notification.Report(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                report = report!!.asModel(),
            )

            Type.SEVERED_RELATIONSHIPS -> app.pachli.core.model.Notification.SeveredRelationships(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                relationshipSeveranceEvent = relationshipSeveranceEvent!!.asModel(),
            )

            Type.MODERATION_WARNING -> app.pachli.core.model.Notification.ModerationWarning(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                accountWarning = accountWarning!!.asModel(),
            )

            Type.QUOTE -> app.pachli.core.model.Notification.Quote(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )

            Type.QUOTED_UPDATE -> app.pachli.core.model.Notification.QuotedUpdate(
                id = id,
                createdAt = createdAt,
                account = account.asModel(),
                status = status!!.asModel(),
            )
        }
    }
}

/**
 * @return The network type for this notification.
 */
val app.pachli.core.model.Notification.networkType: Notification.Type
    get() = when (this) {
        is app.pachli.core.model.Notification.Unknown -> Notification.Type.UNKNOWN
        is app.pachli.core.model.Notification.Mention -> Notification.Type.MENTION
        is app.pachli.core.model.Notification.Reblog -> Notification.Type.REBLOG
        is app.pachli.core.model.Notification.Favourite -> Notification.Type.FAVOURITE
        is app.pachli.core.model.Notification.Follow -> Notification.Type.FOLLOW
        is app.pachli.core.model.Notification.FollowRequest -> Notification.Type.FOLLOW_REQUEST
        is app.pachli.core.model.Notification.Poll -> Notification.Type.POLL
        is app.pachli.core.model.Notification.Status -> Notification.Type.STATUS
        is app.pachli.core.model.Notification.SignUp -> Notification.Type.SIGN_UP
        is app.pachli.core.model.Notification.Update -> Notification.Type.UPDATE
        is app.pachli.core.model.Notification.Report -> Notification.Type.REPORT
        is app.pachli.core.model.Notification.SeveredRelationships -> Notification.Type.SEVERED_RELATIONSHIPS
        is app.pachli.core.model.Notification.ModerationWarning -> Notification.Type.MODERATION_WARNING
        is app.pachli.core.model.Notification.Quote -> Notification.Type.QUOTE
        is app.pachli.core.model.Notification.QuotedUpdate -> Notification.Type.QUOTED_UPDATE
    }
