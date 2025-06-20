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
    val actionableStatus: Status
        get() = reblog ?: this

    // Note: These are deliberately listed in order, most public to least public.
    // These are currently serialised to the database by the ordinal value, and
    // compared by ordinal value, so be extremely careful when adding anything
    // to this list.
    @HasDefault
    enum class Visibility {
        @Default
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

        fun asModel() = when (this) {
            UNKNOWN -> app.pachli.core.model.Status.Visibility.UNKNOWN
            PUBLIC -> app.pachli.core.model.Status.Visibility.PUBLIC
            UNLISTED -> app.pachli.core.model.Status.Visibility.UNLISTED
            PRIVATE -> app.pachli.core.model.Status.Visibility.PRIVATE
            DIRECT -> app.pachli.core.model.Status.Visibility.DIRECT
        }
    }

    fun asModel(): app.pachli.core.model.Status = app.pachli.core.model.Status(
        id = id,
        url = url,
        account = account.asModel(),
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        reblog = reblog?.asModel(),
        content = content,
        createdAt = createdAt,
        editedAt = editedAt,
        emojis = emojis.asModel(),
        reblogsCount = reblogsCount,
        favouritesCount = favouritesCount,
        repliesCount = repliesCount,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked,
        sensitive = sensitive,
        spoilerText = spoilerText,
        visibility = visibility.asModel(),
        attachments = attachments.asModel(),
        mentions = mentions.asModel(),
        tags = tags?.asModel(),
        application = application?.asModel(),
        pinned = pinned,
        muted = muted,
        poll = poll?.asModel(),
        card = card?.asModel(),
        language = language,
        filtered = filtered?.asModel(),
    )

    @JsonClass(generateAdapter = true)
    data class Mention(
        val id: String,
        val url: String,
        /** If this is remote then "[localUsername]@server", otherwise [localUsername]. */
        @Json(name = "acct") val username: String,
        /** The username, without the server part or leading "@". */
        @Json(name = "username") val localUsername: String,
    ) {
        fun asModel() = app.pachli.core.model.Status.Mention(
            id = id,
            url = url,
            username = username,
            localUsername = localUsername,
        )
    }

    @JsonClass(generateAdapter = true)
    data class Application(
        val name: String,
        val website: String?,
    ) {
        fun asModel() = app.pachli.core.model.Status.Application(
            name = name,
            website = website,
        )
    }
}

@JvmName("iterableStatusAsModel")
fun Iterable<Status>.asModel() = map { it.asModel() }

@JvmName("iterableStatusMentionAsModel")
fun Iterable<Status.Mention>.asModel() = map { it.asModel() }
