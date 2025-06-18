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

import app.pachli.core.common.extensions.getOrElse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Status(
    val id: String,
    // not present if it's reblog
    val url: String?,
    val account: TimelineAccount,
    @Json(name = "in_reply_to_id") val inReplyToId: String?,
    @Json(name = "in_reply_to_account_id") val inReplyToAccountId: String?,
    val reblog: Status?,
    val content: String,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "edited_at") val editedAt: Date?,
    val emojis: List<Emoji>,
    @Json(name = "reblogs_count") val reblogsCount: Int,
    @Json(name = "favourites_count") val favouritesCount: Int,
    @Json(name = "replies_count") val repliesCount: Int,
    val reblogged: Boolean = false,
    val favourited: Boolean = false,
    val bookmarked: Boolean = false,
    val sensitive: Boolean,
    @Json(name = "spoiler_text") val spoilerText: String,
    val visibility: Visibility,
    @Json(name = "media_attachments") val attachments: List<Attachment>,
    val mentions: List<Mention>,
    val tags: List<HashTag>?,
    val application: Application?,
    val pinned: Boolean?,
    val muted: Boolean?,
    val poll: Poll?,
    val card: Card?,
    val language: String?,
    val filtered: List<FilterResult>?,
) {

    val actionableId: String
        get() = reblog?.id ?: id

    val actionableStatus: Status
        get() = reblog ?: this

    // Note: These are deliberately listed in order, most public to least public.
    // These are currently serialised to the database by the ordinal value, and
    // compared by ordinal value, so be extremely careful when adding anything
    // to this list.
    enum class Visibility {
        UNKNOWN,

        /** Visible to everyone, shown in public timelines. */
        @Json(name = "public")
        PUBLIC,

        /* Visible to public, but not included in public timelines. */
        @Json(name = "unlisted")
        UNLISTED,

        /* Visible to followers only, and to any mentioned users. */
        @Json(name = "private")
        PRIVATE,

        /* Visible only to mentioned users. */
        @Json(name = "direct")
        DIRECT,
        ;

        fun serverString(): String {
            return when (this) {
                PUBLIC -> "public"
                UNLISTED -> "unlisted"
                PRIVATE -> "private"
                DIRECT -> "direct"
                UNKNOWN -> "unknown"
            }
        }

        companion object {
            @JvmStatic
            fun getOrUnknown(index: Int) = Enum.getOrElse<Visibility>(index) { UNKNOWN }

            @JvmStatic
            fun byString(s: String): Visibility {
                return when (s) {
                    "public" -> PUBLIC
                    "unlisted" -> UNLISTED
                    "private" -> PRIVATE
                    "direct" -> DIRECT
                    "unknown" -> UNKNOWN
                    else -> UNKNOWN
                }
            }
        }
    }

    fun rebloggingAllowed(): Boolean {
        return (visibility != Visibility.DIRECT && visibility != Visibility.UNKNOWN)
    }

    fun isPinned(): Boolean {
        return pinned ?: false
    }

    @JsonClass(generateAdapter = true)
    data class Mention(
        val id: String,
        val url: String,
        /** If this is remote then "[localUsername]@server", otherwise [localUsername]. */
        @Json(name = "acct") val username: String,
        /** The username, without the server part or leading "@". */
        @Json(name = "username") val localUsername: String,
    )

    @JsonClass(generateAdapter = true)
    data class Application(
        val name: String,
        val website: String?,
    )

    companion object {
        const val MAX_MEDIA_ATTACHMENTS = 4
        const val MAX_POLL_OPTIONS = 4
    }
}
