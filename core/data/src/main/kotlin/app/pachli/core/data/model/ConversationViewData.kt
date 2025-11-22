/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.data.model

import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.ConversationAccount
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Timeline

/**
 * Data necessary to show a conversation.
 *
 * Each conversation wraps the [StatusViewData] for the last status in the
 * conversation for display.
 *
 * @param pachliAccountId
 * @param localDomain The domain associated with [pachliAccountId].
 * @param conversationId Server ID of this conversation.
 * @param accounts Accounts participating in this conversation.
 * @param unread True if this conversation is marked unread.
 * @param lastStatus The most recent [StatusViewData] in this conversation.
 * @param accountFilterDecision The account filter decision for this conversation.
 * @param isConversationStarter True if [lastStatus] is starting the conversation.
 */
data class ConversationViewData(
    override val pachliAccountId: Long,
    val localDomain: String,
    val conversationId: String,
    val accounts: List<ConversationAccount>,
    val unread: Boolean,
    val lastStatus: StatusItemViewData,
    val accountFilterDecision: AccountFilterDecision? = null,
    val isConversationStarter: Boolean,
) : IStatusItemViewData by lastStatus {
    companion object {
        /**
         * Creates a [ConversationViewData].
         *
         * @param pachliAccount
         * @param conversationData
         * @param showSensitiveMedia
         * @param defaultIsExpanded Default value for the `isExpanded` property if not set.
         * @param accountFilterDecision
         */
        fun make(
            pachliAccount: PachliAccount,
            conversationData: ConversationData,
            showSensitiveMedia: Boolean,
            defaultIsExpanded: Boolean,
            contentFilterAction: FilterAction,
            quoteContentFilterAction: FilterAction?,
            accountFilterDecision: AccountFilterDecision?,
        ) = ConversationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.entity.domain,
            conversationId = conversationData.id,
            accounts = conversationData.accounts,
            unread = conversationData.unread,
            lastStatus = StatusItemViewData.from(
                pachliAccount = pachliAccount,
                conversationData.lastStatus,
                isExpanded = defaultIsExpanded,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
                quoteContentFilterAction = quoteContentFilterAction,
                showSensitiveMedia = showSensitiveMedia,
                filterContext = FilterContext.from(Timeline.Conversations),
            ),
            isConversationStarter = conversationData.isConversationStarter,
            accountFilterDecision = accountFilterDecision,
        )
    }
}
