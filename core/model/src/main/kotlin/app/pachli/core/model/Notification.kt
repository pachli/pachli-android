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

package app.pachli.core.model

import java.util.Date

/**
 * @property id The server ID of the notification.
 * @property createdAt The date the notification was created.
 * @property account
 */
sealed interface Notification {
    val id: String
    val createdAt: Date
    val account: TimelineAccount

    sealed interface WithStatus : Notification {
        val status: app.pachli.core.model.Status
    }

    /**
     * @property networkType The type of the notification returned by
     * the API.
     */
    data class Unknown(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        val networkType: String,
    ) : Notification

    data class Mention(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Reblog(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Favourite(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Follow(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
    ) : Notification

    data class FollowRequest(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
    ) : Notification

    data class Quote(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class QuotedUpdate(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Poll(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Status(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class SignUp(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
    ) : Notification

    data class Update(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    data class Report(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        val report: app.pachli.core.model.Report,
    ) : Notification

    data class SeveredRelationships(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        val relationshipSeveranceEvent: RelationshipSeveranceEvent,
    ) : Notification

    data class ModerationWarning(
        override val id: String,
        override val createdAt: Date,
        override val account: TimelineAccount,
        val accountWarning: AccountWarning,
    ) : Notification

    /**
     * Notification types.
     *
     * Notifications have a "type". Ordinarily this is the type of the class,
     * used in `is` checks, etc.
     *
     * However, sometimes it needs to passed through Android intents or
     * persisted as a tag (e.g., for filtering).
     *
     * Order of the enums determines the order in the "Filter notifications"
     * dialog.
     */
    enum class Type(val presentation: String) {
        UNKNOWN("unknown"),

        /** Someone mentioned you */
        MENTION("mention"),

        /** Someone boosted one of your statuses */
        REBLOG("reblog"),

        /** Someone favourited one of your statuses */
        FAVOURITE("favourite"),

        /** Someone followed you */
        FOLLOW("follow"),

        /** Someone requested to follow you */
        FOLLOW_REQUEST("follow_request"),

        /** Someone quoted one of your posts. */
        QUOTE("quote"),

        /** A post you quoted has been updated. */
        QUOTED_UPDATE("quoted_updated"),

        /** A poll you have voted in or created has ended */
        POLL("poll"),

        /** Someone you enabled notifications for has posted a status */
        STATUS("status"),

        /** Someone signed up (optionally sent to admins) */
        SIGN_UP("admin.sign_up"),

        /** A status you reblogged has been updated */
        UPDATE("update"),

        /** A new report has been filed */
        REPORT("admin.report"),

        /** Some of your follow relationships have been severed as a result of a moderation or block event */
        SEVERED_RELATIONSHIPS("severed_relationships"),

        /** A moderator has taken action against your account or has sent you a warning. */
        MODERATION_WARNING("moderation_warning"),
        ;

        companion object {
            @JvmStatic
            fun byString(s: String) = entries.firstOrNull { s == it.presentation } ?: UNKNOWN

            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = entries.filter { it != UNKNOWN }
        }

        override fun toString(): String {
            return presentation
        }
    }

    val type: Type
        get() = when (this) {
            is Favourite -> Type.FAVOURITE
            is Follow -> Type.FOLLOW
            is FollowRequest -> Type.FOLLOW_REQUEST
            is Mention -> Type.MENTION
            is ModerationWarning -> Type.MODERATION_WARNING
            is Poll -> Type.POLL
            is Quote -> Type.QUOTE
            is QuotedUpdate -> Type.QUOTED_UPDATE
            is Reblog -> Type.REBLOG
            is Report -> Type.REPORT
            is SeveredRelationships -> Type.SEVERED_RELATIONSHIPS
            is SignUp -> Type.SIGN_UP
            is Status -> Type.STATUS
            is Unknown -> Type.UNKNOWN
            is Update -> Type.UPDATE
        }
}
