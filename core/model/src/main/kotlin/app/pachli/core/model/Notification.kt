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

// TODO: These should be different subclasses per type, so that each subclass can
// carry the non-null data that it needs.

data class Notification(
    val type: Type,
    val id: String,
    val createdAt: Date,
    val account: TimelineAccount,
    val status: Status?,
    val report: Report?,
    val relationshipSeveranceEvent: RelationshipSeveranceEvent? = null,
    val accountWarning: AccountWarning? = null,
) {

    /**
     * Order of the enums determines the order in the "Filter notifications"
     * dialog.
     *
     * From https://docs.joinmastodon.org/entities/Notification/#type.
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
