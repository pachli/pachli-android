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

import java.time.Instant

/**
 * @property id The server ID of the notification.
 * @property createdAt The Instant the notification was created.
 * @property account
 */
sealed interface Notification {
    val id: String
    val createdAt: Instant
    val account: TimelineAccount

    /** Notification that references a [Status]. */
    sealed interface WithStatus : Notification {
        val status: app.pachli.core.model.Status
    }

    sealed interface WithCollection : Notification {
        val collection: Collection
    }

    /**
     * @property networkType The type of the notification returned by
     * the API.
     */
    data class Unknown(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        val networkType: String,
    ) : Notification

    /** [account] posted [status] mentioning the user. */
    data class Mention(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] boosted the user's [status]. */
    data class Reblog(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] favourited the user's [status]. */
    data class Favourite(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] followed the user. */
    data class Follow(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
    ) : Notification

    /** [account] requested to follow the user. */
    data class FollowRequest(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
    ) : Notification

    /** [account] quoted the user in [status]. */
    data class Quote(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] updated their quote of the user in [status]. */
    data class QuotedUpdate(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** The poll the user voted on or created in [status] has ended. */
    data class Poll(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] posted [status], the user has notifications enabled for [account]. */
    data class Status(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** [account] signed up to the server. */
    data class SignUp(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
    ) : Notification

    /** The user boosted [status], which has been modified. */
    data class Update(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val status: app.pachli.core.model.Status,
    ) : Notification, WithStatus

    /** A new [report] has been filed. */
    data class Report(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        val report: app.pachli.core.model.Report,
    ) : Notification

    /**
     * Follow relationships have been severed because of a moderation
     * or block event.
     */
    data class SeveredRelationships(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        val relationshipSeveranceEvent: RelationshipSeveranceEvent,
    ) : Notification

    /** Moderator took action against your account / sent an [accountWarning]. */
    data class ModerationWarning(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        val accountWarning: AccountWarning,
    ) : Notification

    /** User's account was added to a collection. */
    data class CollectionAdd(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val collection: Collection,
    ) : Notification, WithCollection

    /** Collection the user's account is in was updated. */
    data class CollectionUpdate(
        override val id: String,
        override val createdAt: Instant,
        override val account: TimelineAccount,
        override val collection: Collection,
    ) : Notification, WithCollection

    /**
     * Notification types.
     *
     * Notifications have a "type". Ordinarily this is the type of the class,
     * used in `is` checks, etc.
     *
     * However, sometimes it needs to be passed through Android intents or
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

        /** Added to a collection. */
        COLLECTION_ADD("collection_add"),

        /** Collection was updated. */
        COLLECTION_UPDATE("collection_update"),
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

    /**
     * The notification's [Type].
     *
     * Use sparingly, and prefer instance (`is`) checks against the notification's
     * type. This should only be used when the notification's type has to be sent
     * somewhere that doesn't support the [Notification] class (e.g., serialise
     * to the database, or in an intent).
     */
    val type: Type
        get() = when (this) {
            is CollectionAdd -> Type.COLLECTION_ADD
            is CollectionUpdate -> Type.COLLECTION_UPDATE
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
