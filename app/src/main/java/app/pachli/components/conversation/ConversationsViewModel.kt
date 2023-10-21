/* Copyright 2021 Tusky Contributors
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.components.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.database.AccountManager
import app.pachli.usecase.TimelineCases
import app.pachli.util.EmptyPagingSource
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    private val accountManager: AccountManager,
    private val api: MastodonApi,
) : ViewModel() {

    @OptIn(ExperimentalPagingApi::class)
    val conversationFlow = Pager(
        config = PagingConfig(pageSize = 30),
        remoteMediator = ConversationsRemoteMediator(
            api,
            transactionProvider,
            conversationsDao,
            accountManager,
        ),
        pagingSourceFactory = {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                conversationsDao.conversationsForAccount(activeAccount.id)
            }
        },
    )
        .flow
        .map { pagingData ->
            pagingData.map { conversation -> ConversationViewData.from(conversation) }
        }
        .cachedIn(viewModelScope)

    fun favourite(favourite: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            timelineCases.favourite(conversation.lastStatus.id, favourite).fold({
                val newConversation = conversation.toConversationEntity(
                    accountId = accountManager.activeAccount!!.id,
                    favourited = favourite,
                )

                saveConversationToDb(newConversation)
            }, { e ->
                Log.w(TAG, "failed to favourite status", e)
            },)
        }
    }

    fun bookmark(bookmark: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            timelineCases.bookmark(conversation.lastStatus.id, bookmark).fold({
                val newConversation = conversation.toConversationEntity(
                    accountId = accountManager.activeAccount!!.id,
                    bookmarked = bookmark,
                )

                saveConversationToDb(newConversation)
            }, { e ->
                Log.w(TAG, "failed to bookmark status", e)
            },)
        }
    }

    fun voteInPoll(choices: List<Int>, conversation: ConversationViewData) {
        viewModelScope.launch {
            timelineCases.voteInPoll(conversation.lastStatus.id, conversation.lastStatus.status.poll?.id!!, choices)
                .fold({ poll ->
                    val newConversation = conversation.toConversationEntity(
                        accountId = accountManager.activeAccount!!.id,
                        poll = poll,
                    )

                    saveConversationToDb(newConversation)
                }, { e ->
                    Log.w(TAG, "failed to vote in poll", e)
                },)
        }
    }

    fun expandHiddenStatus(expanded: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toConversationEntity(
                accountId = accountManager.activeAccount!!.id,
                expanded = expanded,
            )
            saveConversationToDb(newConversation)
        }
    }

    fun collapseLongStatus(collapsed: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toConversationEntity(
                accountId = accountManager.activeAccount!!.id,
                collapsed = collapsed,
            )
            saveConversationToDb(newConversation)
        }
    }

    fun showContent(showing: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toConversationEntity(
                accountId = accountManager.activeAccount!!.id,
                showingHiddenContent = showing,
            )
            saveConversationToDb(newConversation)
        }
    }

    fun remove(conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                api.deleteConversation(conversationId = conversation.id)

                conversationsDao.delete(
                    id = conversation.id,
                    accountId = accountManager.activeAccount!!.id,
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to delete conversation", e)
            }
        }
    }

    fun muteConversation(conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                timelineCases.muteConversation(
                    conversation.lastStatus.id,
                    !(conversation.lastStatus.status.muted ?: false),
                )

                val newConversation = conversation.toConversationEntity(
                    accountId = accountManager.activeAccount!!.id,
                    muted = !(conversation.lastStatus.status.muted ?: false),
                )

                conversationsDao.insert(newConversation)
            } catch (e: Exception) {
                Log.w(TAG, "failed to mute conversation", e)
            }
        }
    }

    private suspend fun saveConversationToDb(conversation: ConversationEntity) {
        conversationsDao.insert(conversation)
    }

    companion object {
        private const val TAG = "ConversationsViewModel"
    }
}
