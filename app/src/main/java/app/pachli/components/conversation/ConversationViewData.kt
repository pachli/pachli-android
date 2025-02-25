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

package app.pachli.components.conversation

import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.database.model.ConversationAccount
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction

/**
 * Data necessary to show a conversation.
 *
 * Each conversation wraps the [StatusViewData] for the last status in the
 * conversation for display.
 *
 * @param pachliAccountId
 * @param localDomain The domain associated with [pachliAccountId].
 * @param id Server ID of this conversation.
 * @param accounts Accounts participating in this conversation.
 * @param unread True if this conversation is marked unread.
 * @param lastStatus The most recent [StatusViewData] in this conversation.
 * @param accountFilterDecision The account filter decision for this conversation.
 * @param isConversationStarter True if [lastStatus] is starting the conversation.
 */
data class ConversationViewData(
    override val pachliAccountId: Long,
    val localDomain: String,
    val id: String,
    val accounts: List<ConversationAccount>,
    val unread: Boolean,
    val lastStatus: StatusViewData,
    val accountFilterDecision: AccountFilterDecision? = null,
    val isConversationStarter: Boolean,
) : IStatusViewData by lastStatus {
    companion object {
        /**
         * Creates a [ConversationViewData].
         *
         * @param pachliAccount
         * @param conversationData
         * @param defaultIsExpanded Default value for the `isExpanded` property if not set.
         * @param defaultIsShowingContent Default value for the `isShowingContent` property if not set.
         * @param accountFilterDecision
         */
        fun make(
            pachliAccount: PachliAccount,
            conversationData: ConversationData,
            defaultIsExpanded: Boolean,
            defaultIsShowingContent: Boolean,
            contentFilterAction: FilterAction,
            accountFilterDecision: AccountFilterDecision?,
        ) = ConversationViewData(
            pachliAccountId = pachliAccount.id,
            localDomain = pachliAccount.entity.domain,
            id = conversationData.id,
            accounts = conversationData.accounts,
            unread = conversationData.unread,
            lastStatus = StatusViewData.from(
                pachliAccountId = pachliAccount.id,
                conversationData.lastStatus,
                isExpanded = defaultIsExpanded,
                isShowingContent = defaultIsShowingContent,
                isDetailed = false,
                contentFilterAction = contentFilterAction,
            ),
            isConversationStarter = conversationData.isConversationStarter,
            accountFilterDecision = accountFilterDecision,
        )
    }
}
