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
        ;

        companion object {
            @JvmStatic
            fun byString(s: String) = entries.firstOrNull { s == it.presentation } ?: UNKNOWN

            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = listOf(
                MENTION,
                REBLOG,
                FAVOURITE,
                FOLLOW,
                FOLLOW_REQUEST,
                POLL,
                STATUS,
                SIGN_UP,
                UPDATE,
                REPORT,
                SEVERED_RELATIONSHIPS,
            )
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

    // for Pleroma compatibility that uses Mention type
    fun rewriteToStatusTypeIfNeeded(accountId: String): Notification {
        if (type == Type.MENTION && status != null) {
            return if (status.mentions.any { it.id == accountId }) {
                this
            } else {
                copy(type = Type.STATUS)
            }
        }
        return this
    }
}
