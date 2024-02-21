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

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import app.pachli.core.common.extensions.getOrNull
import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import app.pachli.core.network.parseAsMastodonHtml
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
    val reblogged: Boolean,
    val favourited: Boolean,
    val bookmarked: Boolean,
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

    @HasDefault
    enum class Visibility {
        @Default
        UNKNOWN,

        @Json(name = "public")
        PUBLIC,

        @Json(name = "unlisted")
        UNLISTED,

        @Json(name = "private")
        PRIVATE,

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
            fun getOrUnknown(index: Int) = Enum.getOrNull<Visibility>(index) ?: UNKNOWN

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

    fun toDeletedStatus(): DeletedStatus {
        return DeletedStatus(
            text = getEditableText(),
            inReplyToId = inReplyToId,
            spoilerText = spoilerText,
            visibility = visibility,
            sensitive = sensitive,
            attachments = attachments,
            poll = poll,
            createdAt = createdAt,
            language = language,
        )
    }

    private fun getEditableText(): String {
        val contentSpanned = content.parseAsMastodonHtml()
        val builder = SpannableStringBuilder(content.parseAsMastodonHtml())
        for (span in contentSpanned.getSpans(0, content.length, URLSpan::class.java)) {
            val url = span.url
            for ((_, url1, username) in mentions) {
                if (url == url1) {
                    val start = builder.getSpanStart(span)
                    val end = builder.getSpanEnd(span)
                    if (start >= 0 && end >= 0) {
                        builder.replace(start, end, "@$username")
                    }
                    break
                }
            }
        }
        return builder.toString()
    }

    @JsonClass(generateAdapter = true)
    data class Mention(
        val id: String,
        val url: String,
        @Json(name = "acct") val username: String,
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
