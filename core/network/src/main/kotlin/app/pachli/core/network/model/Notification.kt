/*
 * Copyright 2023 Pachli Association
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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.JsonAdapter

// TODO: These should be different subclasses per type, so that each subclass can
// carry the non-null data that it needs.
data class Notification(
    val type: Type,
    val id: String,
    val account: TimelineAccount,
    val status: Status?,
    val report: Report?,
) {

    /** From https://docs.joinmastodon.org/entities/Notification/#type */
    @JsonAdapter(NotificationTypeAdapter::class)
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

        /** A poll you have voted in or created has ended */
        POLL("poll"),

        /** Someone you enabled notifications for has posted a status */
        STATUS("status"),

        /** Someone signed up (optionally sent to admins) */
        SIGN_UP("admin.sign_up"),

        /** A status you interacted with has been updated */
        UPDATE("update"),

        /** A new report has been filed */
        REPORT("admin.report"),
        ;

        companion object {
            @JvmStatic
            fun byString(s: String): Type {
                values().forEach {
                    if (s == it.presentation) {
                        return it
                    }
                }
                return UNKNOWN
            }

            /** Notification types for UI display (omits UNKNOWN) */
            val visibleTypes = listOf(MENTION, REBLOG, FAVOURITE, FOLLOW, FOLLOW_REQUEST, POLL, STATUS, SIGN_UP, UPDATE, REPORT)
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

    class NotificationTypeAdapter : JsonDeserializer<Type> {

        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement,
            typeOfT: java.lang.reflect.Type,
            context: JsonDeserializationContext,
        ): Type {
            return Type.byString(json.asString)
        }
    }

    // for Pleroma compatibility that uses Mention type
    fun rewriteToStatusTypeIfNeeded(accountId: String): Notification {
        if (type == Type.MENTION && status != null) {
            return if (status.mentions.any {
                it.id == accountId
            }
            ) {
                this
            } else {
                copy(type = Type.STATUS)
            }
        }
        return this
    }
}
