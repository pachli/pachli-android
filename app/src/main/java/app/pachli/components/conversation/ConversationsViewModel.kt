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
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.StatusRepository
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.model.ConversationData
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AccountFilterReason
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.network.ContentFilterModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = ConversationsViewModel.Factory::class)
class ConversationsViewModel @AssistedInject constructor(
    private val repository: ConversationsRepository,
    private val conversationsDao: ConversationsDao,
    private val accountManager: AccountManager,
    private val api: MastodonApi,
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val statusRepository: StatusRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {
    private val accountFlow = accountManager.getPachliAccountFlow(pachliAccountId)
        .filterNotNull()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val uiAction = MutableSharedFlow<UiAction>()
    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    val conversationFlow = accountFlow
        .flatMapLatest { pachliAccount ->
            val contentFilterModel = when (pachliAccount.contentFilters.version) {
                ContentFilterVersion.V2 -> ContentFilterModel(FilterContext.CONVERSATIONS)
                ContentFilterVersion.V1 -> ContentFilterModel(
                    FilterContext.CONVERSATIONS,
                    pachliAccount.contentFilters.contentFilters,
                )
            }

            repository.conversations(pachliAccount.id)
                .map { pagingData ->
                    pagingData
                        .map { conversation ->
                            val contentFilterAction =
                                conversation.viewData?.contentFilterAction
                                    ?: contentFilterModel.filterActionFor(conversation.lastStatus.status)

                            val accountFilterDecision = if (conversation.isConversationStarter) {
                                conversation.viewData?.accountFilterDecision
                                    ?: filterConversationByAccount(pachliAccount, conversation)
                            } else {
                                null
                            }

                            ConversationViewData.make(
                                pachliAccount,
                                conversation,
                                defaultIsExpanded = pachliAccount.entity.alwaysOpenSpoiler,
                                defaultIsShowingContent = (pachliAccount.entity.alwaysShowSensitiveMedia || !conversation.lastStatus.status.sensitive),
                                contentFilterAction = contentFilterAction,
                                accountFilterDecision = accountFilterDecision,
                            )
                        }
                        .filter { it.contentFilterAction != FilterAction.HIDE }
                        .filter { it.accountFilterDecision !is AccountFilterDecision.Hide }
                }
        }
        .cachedIn(viewModelScope)

    val showFabWhileScrolling = sharedPreferencesRepository.changes
        .filter { it == null || it == PrefKeys.FAB_HIDE }
        .map { !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false) }
        .onStart { emit(!sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false)) }
        .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000))

    init {
        viewModelScope.launch {
            uiAction.filterIsInstance<ConversationAction>().collect(::onConversationAction)
        }
    }

    private fun onConversationAction(conversationAction: ConversationAction) {
        when (conversationAction) {
            is ConversationAction.OverrideAccountFilter ->
                repository.setAccountFilterDecision(
                    conversationAction.pachliAccountId,
                    conversationAction.conversationId,
                    AccountFilterDecision.Override(conversationAction.accountFilterDecision),
                )
            is ConversationAction.ClearContentFilter ->
                repository.clearContentFilter(
                    conversationAction.pachliAccountId,
                    conversationAction.conversationId,
                )
        }
    }

    /**
     * Returns the [AccountFilterDecision] for [conversationData] based on the notification
     * filters in [accountWithFilters].
     *
     * @return The most severe [AccountFilterDecision], in order [Hide][AccountFilterDecision.Hide],
     * [Warn][AccountFilterDecision.Warn], or [None][AccountFilterDecision.None].
     */
    // TODO: This is very similar to the code in NotificationHelper.filterNotificationsByAccount.
    // Think about how the different account filters can be represented so this can be
    // generalised.
    private fun filterConversationByAccount(
        accountWithFilters: PachliAccount,
        conversationData: ConversationData,
    ): AccountFilterDecision {
        // Only the initial account posting in a conversation is tested.
        if (!conversationData.isConversationStarter) return AccountFilterDecision.None

        // The status to test against
        val status = conversationData.lastStatus.status

        // The account that wrote the last status
        val accountToTest = conversationData.lastStatus.account

        // Any conversations where we wrote the last status are not filtered.
        if (accountWithFilters.entity.accountId == accountToTest.serverId) return AccountFilterDecision.None

        val decisions = buildList {
            // Check the following relationship.
            if (accountWithFilters.entity.conversationAccountFilterNotFollowed != FilterAction.NONE) {
                if (accountWithFilters.following.none { it.serverId == accountToTest.serverId }) {
                    add(
                        AccountFilterDecision.make(
                            accountWithFilters.entity.conversationAccountFilterNotFollowed,
                            AccountFilterReason.NOT_FOLLOWING,
                        ),
                    )
                }
            }

            // Check the age of the account relative to the status.
            accountToTest.createdAt?.let { createdAt ->
                if (accountWithFilters.entity.conversationAccountFilterYounger30d != FilterAction.NONE) {
                    if (Duration.between(createdAt, Instant.ofEpochMilli(status.createdAt)) < Duration.ofDays(30)) {
                        add(
                            AccountFilterDecision.make(
                                accountWithFilters.entity.conversationAccountFilterYounger30d,
                                AccountFilterReason.YOUNGER_30D,
                            ),
                        )
                    }
                }
            }

            // Check limited status
            if (accountToTest.limited && accountWithFilters.entity.conversationAccountFilterLimitedByServer != FilterAction.NONE) {
                add(
                    AccountFilterDecision.make(
                        accountWithFilters.entity.notificationAccountFilterLimitedByServer,
                        AccountFilterReason.LIMITED_BY_SERVER,
                    ),
                )
            }
        }

        return decisions.firstOrNull { it is AccountFilterDecision.Hide }
            ?: decisions.firstOrNull { it is AccountFilterDecision.Warn }
            ?: AccountFilterDecision.None
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun favourite(favourite: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.favourite(pachliAccountId, lastStatusId, favourite)
        }
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun bookmark(bookmark: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.bookmark(pachliAccountId, lastStatusId, bookmark)
        }
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun muteConversation(muted: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.mute(pachliAccountId, lastStatusId, muted)
        }
    }

    /**
     * @param lastStatusId ID of the last status in the conversation
     */
    fun voteInPoll(choices: List<Int>, lastStatusId: String, pollId: String) {
        viewModelScope.launch {
            statusRepository.voteInPoll(pachliAccountId, lastStatusId, pollId, choices)
        }
    }

    fun expandHiddenStatus(pachliAccountId: Long, expanded: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.setExpanded(pachliAccountId, lastStatusId, expanded)
        }
    }

    fun collapseLongStatus(pachliAccountId: Long, collapsed: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.setContentCollapsed(pachliAccountId, lastStatusId, collapsed)
        }
    }

    fun showContent(pachliAccountId: Long, showingHiddenContent: Boolean, lastStatusId: String) {
        viewModelScope.launch {
            statusRepository.setContentShowing(pachliAccountId, lastStatusId, showingHiddenContent)
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
                currentCoroutineContext().ensureActive()
                Timber.w(e, "failed to delete conversation")
            }
        }
    }

    @AssistedFactory
    interface Factory {
        /** Creates [ConversationsViewModel] with [pachliAccountId] as the active account. */
        fun create(pachliAccountId: Long): ConversationsViewModel
    }
}
