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
    val collection: Collection? = null,
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

        /** Added to a collection. */
        @Json(name = "added_to_collection")
        COLLECTION_ADD("added_to_collection"),

        /** Collection was updated. */
        @Json(name = "collection_update")
        COLLECTION_UPDATE("collection_update"),

        ;

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

    /**
     * Fallibly convert a [network Notification][Notification] to a
     * [Notification][app.pachli.core.model.Notification].
     *
     * Fails and returns null if the notification could not be converted.
     * This happens if the network notification is missing mandatory
     * information, for example a Favourite notification without an
     * associated [status][app.pachli.core.model.Notification.Favourite.status].
     *
     * See https://github.com/tuskyapp/Tusky/issues/2252.
     *
     * @param accountId Server ID of the account notifications are being
     * fetched for.
     */
    fun asModel(accountId: String): app.pachli.core.model.Notification? {
        // Pleroma uses 'Mention' type for both mentions and subscribed status
        // updates. Adjust the type depending on whether the user is mentioned
        // in the status.
        val type = if (type == Type.MENTION && status != null) {
            if (status.mentions.any { it.id == accountId }) this.type else Type.STATUS
        } else {
            this.type
        }

        return when (type) {
            Type.UNKNOWN -> app.pachli.core.model.Notification.Unknown(
                id = id,
                createdAt = createdAt.toInstant(),
                account = account.asModel(),
                // Note: This collapses everything to "unknown" because the
                // model uses the enum. Should keep this as a string here
                // and convert to the internal model Type later.
                networkType = type.presentation,
            )

            Type.COLLECTION_ADD -> collection?.let {
                app.pachli.core.model.Notification.CollectionAdd(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    collection = collection.asModel(),
                )
            }

            Type.COLLECTION_UPDATE -> collection?.let {
                app.pachli.core.model.Notification.CollectionUpdate(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    collection = collection.asModel(),
                )
            }

            Type.MENTION -> status?.let {
                app.pachli.core.model.Notification.Mention(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.REBLOG -> status?.let {
                app.pachli.core.model.Notification.Reblog(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.FAVOURITE -> status?.let {
                app.pachli.core.model.Notification.Favourite(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.FOLLOW -> app.pachli.core.model.Notification.Follow(
                id = id,
                createdAt = createdAt.toInstant(),
                account = account.asModel(),
            )

            Type.FOLLOW_REQUEST -> app.pachli.core.model.Notification.FollowRequest(
                id = id,
                createdAt = createdAt.toInstant(),
                account = account.asModel(),
            )

            Type.POLL -> status?.let {
                app.pachli.core.model.Notification.Poll(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.STATUS -> status?.let {
                app.pachli.core.model.Notification.Status(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.SIGN_UP -> app.pachli.core.model.Notification.SignUp(
                id = id,
                createdAt = createdAt.toInstant(),
                account = account.asModel(),
            )

            Type.UPDATE -> status?.let {
                app.pachli.core.model.Notification.Update(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.REPORT -> report?.let {
                app.pachli.core.model.Notification.Report(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    report = report.asModel(),
                )
            }

            Type.SEVERED_RELATIONSHIPS -> relationshipSeveranceEvent?.let {
                app.pachli.core.model.Notification.SeveredRelationships(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    relationshipSeveranceEvent = relationshipSeveranceEvent.asModel(),
                )
            }

            Type.MODERATION_WARNING -> accountWarning?.let {
                app.pachli.core.model.Notification.ModerationWarning(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    accountWarning = accountWarning.asModel(),
                )
            }

            Type.QUOTE -> status?.let {
                app.pachli.core.model.Notification.Quote(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }

            Type.QUOTED_UPDATE -> status?.let {
                app.pachli.core.model.Notification.QuotedUpdate(
                    id = id,
                    createdAt = createdAt.toInstant(),
                    account = account.asModel(),
                    status = status.asModel(),
                )
            }
        }
    }
}

/** @return The network type for this notification. */
fun app.pachli.core.model.Notification.Type.asNetworkModel() = when (this) {
    app.pachli.core.model.Notification.Type.UNKNOWN -> Notification.Type.UNKNOWN
    app.pachli.core.model.Notification.Type.MENTION -> Notification.Type.MENTION
    app.pachli.core.model.Notification.Type.REBLOG -> Notification.Type.REBLOG
    app.pachli.core.model.Notification.Type.FAVOURITE -> Notification.Type.FAVOURITE
    app.pachli.core.model.Notification.Type.FOLLOW -> Notification.Type.FOLLOW
    app.pachli.core.model.Notification.Type.FOLLOW_REQUEST -> Notification.Type.FOLLOW_REQUEST
    app.pachli.core.model.Notification.Type.POLL -> Notification.Type.POLL
    app.pachli.core.model.Notification.Type.STATUS -> Notification.Type.STATUS
    app.pachli.core.model.Notification.Type.SIGN_UP -> Notification.Type.SIGN_UP
    app.pachli.core.model.Notification.Type.UPDATE -> Notification.Type.UPDATE
    app.pachli.core.model.Notification.Type.REPORT -> Notification.Type.REPORT
    app.pachli.core.model.Notification.Type.SEVERED_RELATIONSHIPS -> Notification.Type.SEVERED_RELATIONSHIPS
    app.pachli.core.model.Notification.Type.MODERATION_WARNING -> Notification.Type.MODERATION_WARNING
    app.pachli.core.model.Notification.Type.QUOTE -> Notification.Type.QUOTE
    app.pachli.core.model.Notification.Type.QUOTED_UPDATE -> Notification.Type.QUOTED_UPDATE
    app.pachli.core.model.Notification.Type.COLLECTION_ADD -> Notification.Type.COLLECTION_ADD
    app.pachli.core.model.Notification.Type.COLLECTION_UPDATE -> Notification.Type.COLLECTION_UPDATE
}

/**
 * Converts [this] to [model Notification][app.pachli.core.model.Notification]s,
 * discarding any failed conversions (so may produce an emtpy list).
 *
 * @param accountId See [Notification.asModel].
 */
fun Iterable<Notification>.asModel(accountId: String) = mapNotNull { it.asModel(accountId) }

fun Iterable<app.pachli.core.model.Notification.Type>.asNetworkModel() = map { it.asNetworkModel() }
