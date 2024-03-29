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
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.components.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.Converters
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.usecase.TimelineCases
import app.pachli.util.EmptyPagingSource
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    private val converters: Converters,
    private val accountManager: AccountManager,
    private val api: MastodonApi,
    sharedPreferencesRepository: SharedPreferencesRepository,
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

    val showFabWhileScrolling = sharedPreferencesRepository.changes
        .filter { it == null || it == PrefKeys.FAB_HIDE }
        .map { !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false) }
        .onStart { emit(!sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false)) }
        .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000))

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun favourite(favourite: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            timelineCases.favourite(lastStatusId, favourite).fold({
                conversationsDao.setFavourited(
                    accountManager.activeAccount!!.id,
                    lastStatusId,
                    favourite,
                )
            }, { e ->
                Timber.w(e, "failed to favourite status")
            })
        }
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun bookmark(bookmark: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            timelineCases.bookmark(lastStatusId, bookmark).fold({
                conversationsDao.setBookmarked(
                    accountManager.activeAccount!!.id,
                    lastStatusId,
                    bookmark,
                )
            }, { e ->
                Timber.w(e, "failed to bookmark status")
            })
        }
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun voteInPoll(choices: List<Int>, lastStatusId: String, pollId: String) {
        viewModelScope.launch {
            timelineCases.voteInPoll(lastStatusId, pollId, choices)
                .fold({ poll ->
                    conversationsDao.setVoted(
                        accountManager.activeAccount!!.id,
                        lastStatusId,
                        converters.pollToJson(poll)!!,
                    )
                }, { e ->
                    Timber.w(e, "failed to vote in poll")
                })
        }
    }

    fun expandHiddenStatus(expanded: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            conversationsDao.setExpanded(
                accountManager.activeAccount!!.id,
                lastStatusId,
                expanded,
            )
        }
    }

    fun collapseLongStatus(collapsed: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            conversationsDao.setCollapsed(
                accountManager.activeAccount!!.id,
                lastStatusId,
                collapsed,
            )
        }
    }

    fun showContent(showingHiddenContent: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            conversationsDao.setShowingHiddenContent(
                accountManager.activeAccount!!.id,
                lastStatusId,
                showingHiddenContent,
            )
        }
    }

    fun remove(viewData: ConversationViewData) {
        viewModelScope.launch {
            try {
                api.deleteConversation(conversationId = viewData.id)

                conversationsDao.delete(
                    id = viewData.id,
                    accountId = accountManager.activeAccount!!.id,
                )
            } catch (e: Exception) {
                Timber.w(e, "failed to delete conversation")
            }
        }
    }

    fun muteConversation(muted: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            try {
                timelineCases.muteConversation(lastStatusId, muted)

                conversationsDao.setMuted(
                    accountManager.activeAccount!!.id,
                    lastStatusId,
                    muted,
                )
            } catch (e: Exception) {
                Timber.w(e, "failed to mute conversation")
            }
        }
    }
}
