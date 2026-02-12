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
import app.pachli.core.model.Status.Application
import app.pachli.core.model.Status.Mention
import app.pachli.core.model.Status.Quote
import app.pachli.core.model.Status.QuoteApproval
import app.pachli.core.model.Status.Visibility
import com.squareup.moshi.JsonClass
import java.util.Date

interface IStatus {
    val statusId: String

    // not present if it's reblog
    val url: String?
    val account: TimelineAccount
    val inReplyToId: String?
    val inReplyToAccountId: String?
    val reblog: Status?
    val content: CharSequence
    val createdAt: Date
    val editedAt: Date?
    val emojis: List<Emoji>
    val reblogsCount: Int
    val favouritesCount: Int
    val repliesCount: Int
    val quotesCount: Int
    val reblogged: Boolean
    val favourited: Boolean
    val bookmarked: Boolean
    val sensitive: Boolean
    val spoilerText: String
    val visibility: Visibility
    val attachments: List<Attachment>
    val mentions: List<Mention>
    val tags: List<HashTag>?
    val application: Application?
    val pinned: Boolean?
    val muted: Boolean?
    val poll: Poll?
    val card: Card?
    val quote: Quote?
    val quoteApproval: QuoteApproval
    val language: String?
    val filtered: List<FilterResult>?
}

/**
 * @property reblogged True if the current user reblogged this status.
 */
data class Status(
    override val statusId: String,
    // not present if it's reblog
    override val url: String?,
    override val account: TimelineAccount,
    override val inReplyToId: String?,
    override val inReplyToAccountId: String?,
    override val reblog: Status?,
    override val content: String,
    override val createdAt: Date,
    override val editedAt: Date?,
    override val emojis: List<Emoji>,
    override val reblogsCount: Int,
    override val favouritesCount: Int,
    override val repliesCount: Int,
    override val quotesCount: Int,
    override val reblogged: Boolean = false,
    override val favourited: Boolean = false,
    override val bookmarked: Boolean = false,
    override val sensitive: Boolean,
    override val spoilerText: String,
    override val visibility: Visibility,
    override val attachments: List<Attachment>,
    override val mentions: List<Mention>,
    override val tags: List<HashTag>?,
    override val application: Application?,
    override val pinned: Boolean?,
    override val muted: Boolean?,
    override val poll: Poll?,
    override val card: Card?,
    override val quote: Quote?,
    override val quoteApproval: QuoteApproval,
    override val language: String?,
    override val filtered: List<FilterResult>?,
) : IStatus {
    val actionableId: String
        get() = reblog?.statusId ?: statusId

    val actionableStatus: Status
        get() = reblog ?: this

    val isSelfReply: Boolean
        get() = inReplyToAccountId != null && inReplyToAccountId == account.id

    // Note: These are deliberately listed in order, most public to least public.
    // These are currently serialised to the database by the ordinal value, and
    // compared by ordinal value, so be extremely careful when adding anything
    // to this list.
    enum class Visibility {
        UNKNOWN,

        /** Visible to everyone, shown in public timelines. */
        PUBLIC,

        /** Visible to public, but not included in public timelines. */
        UNLISTED,

        /** Visible to followers only, and to any mentioned users. */
        PRIVATE,

        /** Visible only to mentioned users. */
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

        /**
         * @return True if statuses with this visibility can be reblogged, otherwise
         * false.
         */
        val allowsReblog: Boolean
            get() = when (this) {
                PUBLIC, UNLISTED -> true
                PRIVATE, DIRECT, UNKNOWN -> false
            }

        /**
         * @return True if statuses with this visibility can be quoted, otherwise
         * false
         */
        val allowsQuote: Boolean
            get() = when (this) {
                PUBLIC, UNLISTED, PRIVATE -> true
                UNKNOWN, DIRECT -> false
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

    /**
     * @return True if this status can be reblogged based on the visibility.
     * Only statuses with [Visibility.PUBLIC] or [Visibility.UNLISTED] can be
     * reblogged.
     *
     * @see [Status.Visibility.allowsReblog]
     */
    fun rebloggingAllowed() = visibility.allowsReblog

    fun isPinned(): Boolean {
        return pinned ?: false
    }

    @JsonClass(generateAdapter = true)
    data class Mention(
        val id: String,
        val url: String,
        /** If this is remote then "[localUsername]@server", otherwise [localUsername]. */
        val username: String,
        /** The username, without the server part or leading "@". */
        val localUsername: String,
    )

    @JsonClass(generateAdapter = true)
    data class Application(
        val name: String,
        val website: String?,
    )

    /** The quote's acceptance state. */
    enum class QuoteState {
        /**
         * The quote state is not recognised, and will not be displayed.
         */
        UNKNOWN,

        /**
         * The quote has not been acknowledged by the quoted account yet, and
         * requires authorization before being displayed.
         */
        PENDING,

        /**
         * The quote has been accepted and can be displayed. This is one of the
         * few cases where status is non-null.
         */
        ACCEPTED,

        /**
         * The quote has been explicitly rejected by the quoted account, and
         * cannot be displayed.
         */
        REJECTED,

        /**
         * The quote has been previously accepted, but is now revoked, and
         * thus cannot be displayed.
         */
        REVOKED,

        /**
         * The quote has been approved, but the quoted post itself has now
         * been deleted.
         */
        DELETED,

        /**
         * The quote has been approved, but cannot be displayed because the
         * user is not authorized to see it.
         */
        UNAUTHORIZED,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has blocked the account being quoted. This is one of the
         * few cases where status is non-null.
         */
        BLOCKED_ACCOUNT,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has blocked the domain of the account being quoted.
         * This is one of the few cases where status is non-null.
         */
        BLOCKED_DOMAIN,

        /**
         * The quote has been approved, but should not be displayed because
         * the user has muted the the account being quoted. This is one of
         * the few cases where status is non-null.
         */
        MUTED_ACCOUNT,
    }

    sealed interface Quote {
        /** The quote's acceptance state. */
        val state: QuoteState

        /**
         * Full quote.
         *
         * @property state
         * @property status The quoted status.
         */
        data class FullQuote(
            override val state: QuoteState,
            val status: Status,
        ) : Quote {
            val statusId: String
                get() = status.actionableId
        }

        /**
         * Shallow quote.
         *
         * @property state
         * @property statusId ID of the quoted status.
         */
        data class ShallowQuote(
            override val state: QuoteState,
            val statusId: String,
        ) : Quote

        /**
         * Hidden quote; the existence is disclosed to the user, but they can't
         * click through it.
         *
         * @property state Why the quote is hidden.
         */
        data class HiddenQuote(
            override val state: QuoteState,
        ) : Quote
    }

    /**
     * @property automatic Accounts that are automatically approved to quote.
     * @property manual Accounts that require manual approval to quote.
     * @property currentUser Approval policy for the authenticated user.
     */
    // JSON adapter for database serialisation.
    @JsonClass(generateAdapter = true)
    data class QuoteApproval(
        val automatic: List<QuoteApprovalAutomatic> = emptyList(),
        val manual: List<QuoteApprovalManual> = emptyList(),
        val currentUser: QuoteApprovalCurrentUser = QuoteApprovalCurrentUser.UNKNOWN,
    ) {
        /** Possible values for [QuoteApproval.automatic]. */
        enum class QuoteApprovalAutomatic {
            UNSUPPORTED_POLICY,

            /** All accounts are automatically approved to quote. */
            PUBLIC,

            /** Followers are automatically approved to quote. */
            FOLLOWERS,

            /** Following accounts are automatically approved to quote. */
            FOLLOWING,
        }

        /** Possible values for [QuoteApproval.manual]. */
        enum class QuoteApprovalManual {
            UNSUPPORTED_POLICY,

            /** Public accounts require manual approval to quote. */
            PUBLIC,

            /** Follower accounts require manual approval to quote. */
            FOLLOWERS,

            /** Following accounts require manual approval to quote. */
            FOLLOWING,
        }

        /** Possible values for [QuoteApproval.currentUser]. */
        enum class QuoteApprovalCurrentUser {
            UNKNOWN,

            /** The current user is automatically approved to quote. */
            AUTOMATIC,

            /** The current user requires manual approval to quote. */
            MANUAL,

            /** The current user cannot quote this post. */
            DENIED,
        }
    }

    companion object {
        const val MAX_MEDIA_ATTACHMENTS = 4
    }
}
