/* Copyright 2022 Tusky Contributors
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
import app.pachli.core.database.model.ConversationAccountEntity
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.FilterAction

/**
 * Data necessary to show a conversation.
 *
 * Each conversation wraps the [StatusViewData] for the last status in the
 * conversation for display.
 */
data class ConversationViewData(
    val id: String,
    val order: Int,
    val accounts: List<ConversationAccountEntity>,
    val unread: Boolean,
    val lastStatus: StatusViewData,
) : IStatusViewData by lastStatus {
    companion object {
        fun from(pachliAccountId: Long, conversationData: ConversationData) = ConversationViewData(
            id = conversationData.id,
            order = conversationData.order,
            accounts = conversationData.accounts,
            unread = conversationData.unread,
            lastStatus = StatusViewData.from(
                pachliAccountId,
                conversationData.lastStatus,
                isExpanded = true,
                isShowingContent = true,
                isDetailed = false,
                contentFilterAction = FilterAction.NONE,
            ),
        )
    }
}
