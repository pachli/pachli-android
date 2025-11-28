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
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import timber.log.Timber

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
    @DefaultIfNull
    @Json(name = "quotes_count") val quotesCount: Int = 0,
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
    val quote: Quote? = null,
    @Json(name = "quote_approval") val quoteApproval: QuoteApproval? = null,
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

        fun asModel() = when (this) {
            UNKNOWN -> app.pachli.core.model.Status.Visibility.UNKNOWN
            PUBLIC -> app.pachli.core.model.Status.Visibility.PUBLIC
            UNLISTED -> app.pachli.core.model.Status.Visibility.UNLISTED
            PRIVATE -> app.pachli.core.model.Status.Visibility.PRIVATE
            DIRECT -> app.pachli.core.model.Status.Visibility.DIRECT
        }
    }

    fun asModel(): app.pachli.core.model.Status = app.pachli.core.model.Status(
        statusId = id,
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
        quotesCount = quotesCount,
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
        quote = quote?.asModel(),
        quoteApproval = quoteApproval?.asModel() ?: app.pachli.core.model.Status.QuoteApproval(),
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

    @HasDefault
    enum class QuoteState {
        @Default
        UNKNOWN,

        /**
         * The quote has not been acknowledged by the quoted account yet, and
         * requires authorization before being displayed.
         */
        @Json(name = "pending")
        PENDING,

        /**
         * The quote has been accepted and can be displayed. This is one of the
         * few cases where status is non-null.
         */
        @Json(name = "accepted")
        ACCEPTED,

        /**
         * The quote has been explicitly rejected by the quoted account, and
         * cannot be displayed.
         */
        @Json(name = "rejected")
        REJECTED,

        /**
         * The quote has been previously accepted, but is now revoked, and
         * thus cannot be displayed.
         */
        @Json(name = "revoked")
        REVOKED,

        /**
         * The quote has been approved, but the quoted post itself has now
         * been deleted.
         */
        @Json(name = "deleted")
        DELETED,

        /**
         * The quote has been approved, but cannot be displayed because the
         * user is not authorized to see it.
         */
        @Json(name = "unauthorized")
        UNAUTHORIZED,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has blocked the account being quoted. This is one of the
         * few cases where status is non-null.
         */
        @Json(name = "blocked_account")
        BLOCKED_ACCOUNT,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has blocked the domain of the account being quoted.
         * This is one of the few cases where status is non-null.
         */
        @Json(name = "blocked_domain")
        BLOCKED_DOMAIN,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has muted the account being quoted. This is one of
         * the few cases where status is non-null.
         */
        @Json(name = "muted_account")
        MUTED_ACCOUNT,

        ;

        fun asModel() = when (this) {
            UNKNOWN -> app.pachli.core.model.Status.QuoteState.UNKNOWN
            PENDING -> app.pachli.core.model.Status.QuoteState.PENDING
            ACCEPTED -> app.pachli.core.model.Status.QuoteState.ACCEPTED
            REJECTED -> app.pachli.core.model.Status.QuoteState.REJECTED
            REVOKED -> app.pachli.core.model.Status.QuoteState.REVOKED
            DELETED -> app.pachli.core.model.Status.QuoteState.DELETED
            UNAUTHORIZED -> app.pachli.core.model.Status.QuoteState.UNAUTHORIZED
            BLOCKED_ACCOUNT -> app.pachli.core.model.Status.QuoteState.BLOCKED_ACCOUNT
            BLOCKED_DOMAIN -> app.pachli.core.model.Status.QuoteState.BLOCKED_DOMAIN
            MUTED_ACCOUNT -> app.pachli.core.model.Status.QuoteState.MUTED_ACCOUNT
        }
    }

    @JsonClass(generateAdapter = true)
    data class Quote(
        val state: QuoteState,
        @Json(name = "quoted_status") val quotedStatus: Status? = null,
        @Json(name = "quoted_status_id") val quotedStatusId: String? = null,
    ) {
        fun asModel(): app.pachli.core.model.Status.Quote? {
            if (quotedStatus != null) {
                return app.pachli.core.model.Status.Quote.FullQuote(
                    state = state.asModel(),
                    status = quotedStatus.asModel(),
                )
            }

            if (quotedStatusId != null) {
                return app.pachli.core.model.Status.Quote.ShallowQuote(
                    state = state.asModel(),
                    statusId = quotedStatusId,
                )
            }

            Timber.e("Could not convert network Quote to model Quote: $this")
            return null
        }
    }

    @JsonClass(generateAdapter = true)
    data class QuoteApproval(
        @DefaultIfNull
        val automatic: List<QuoteApprovalAutomatic> = emptyList(),

        @DefaultIfNull
        val manual: List<QuoteApprovalManual> = emptyList(),

        @DefaultIfNull
        @Json(name = "current_user")
        val currentUser: QuoteApprovalCurrentUser = QuoteApprovalCurrentUser.UNKNOWN,
    ) {
        /** Possible values for [QuoteApproval.automatic]. */
        @HasDefault
        enum class QuoteApprovalAutomatic {
            @Default
            @Json(name = "unsupported_policy")
            UNSUPPORTED_POLICY,

            @Json(name = "public")
            PUBLIC,

            @Json(name = "followers")
            FOLLOWERS,

            @Json(name = "following")
            FOLLOWING,

            ;

            fun asModel() = when (this) {
                UNSUPPORTED_POLICY -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalAutomatic.UNSUPPORTED_POLICY
                PUBLIC -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalAutomatic.PUBLIC
                FOLLOWERS -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalAutomatic.FOLLOWERS
                FOLLOWING -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalAutomatic.FOLLOWING
            }
        }

        /** Possible values for [QuoteApproval.manual]. */
        @HasDefault
        enum class QuoteApprovalManual {
            @Default
            @Json(name = "unsupported_policy")
            UNSUPPORTED_POLICY,

            @Json(name = "public")
            PUBLIC,

            @Json(name = "followers")
            FOLLOWERS,

            @Json(name = "following")
            FOLLOWING,

            ;

            fun asModel() = when (this) {
                UNSUPPORTED_POLICY -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalManual.UNSUPPORTED_POLICY
                PUBLIC -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalManual.PUBLIC
                FOLLOWERS -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalManual.FOLLOWERS
                FOLLOWING -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalManual.FOLLOWING
            }
        }

        /** Possible values for [QuoteApproval.currentUser]. */
        @HasDefault
        enum class QuoteApprovalCurrentUser {
            @Default
            @Json(name = "unknown")
            UNKNOWN,

            @Json(name = "automatic")
            AUTOMATIC,

            @Json(name = "manual")
            MANUAL,

            @Json(name = "denied")
            DENIED,

            ;

            fun asModel() = when (this) {
                UNKNOWN -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalCurrentUser.UNKNOWN
                AUTOMATIC -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalCurrentUser.AUTOMATIC
                MANUAL -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalCurrentUser.MANUAL
                DENIED -> app.pachli.core.model.Status.QuoteApproval.QuoteApprovalCurrentUser.DENIED
            }
        }

        fun asModel() = app.pachli.core.model.Status.QuoteApproval(
            automatic = automatic.map { it.asModel() },
            manual = manual.map { it.asModel() },
            currentUser = currentUser.asModel(),
        )
    }
}

@JvmName("iterableStatusAsModel")
fun Iterable<Status>.asModel() = map { it.asModel() }

@JvmName("iterableStatusMentionAsModel")
fun Iterable<Status.Mention>.asModel() = map { it.asModel() }
