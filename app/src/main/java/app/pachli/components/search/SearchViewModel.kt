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

package app.pachli.components.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.components.search.SearchOperator.DateOperator
import app.pachli.components.search.SearchOperator.FromOperator
import app.pachli.components.search.SearchOperator.HasEmbedOperator
import app.pachli.components.search.SearchOperator.HasLinkOperator
import app.pachli.components.search.SearchOperator.HasMediaOperator
import app.pachli.components.search.SearchOperator.HasPollOperator
import app.pachli.components.search.SearchOperator.IsReplyOperator
import app.pachli.components.search.SearchOperator.IsSensitiveOperator
import app.pachli.components.search.SearchOperator.LanguageOperator
import app.pachli.components.search.SearchOperator.WhereOperator
import app.pachli.components.search.adapter.SearchPagingSourceFactory
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_FROM
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.usecase.TimelineCases
import app.pachli.util.getInitialLanguages
import app.pachli.util.getLocaleList
import app.pachli.viewdata.StatusViewData
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.github.michaelbull.result.mapBoth
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val timelineCases: TimelineCases,
    private val accountManager: AccountManager,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    var currentQuery: String = ""
    var currentSearchFieldContent: String? = null

    val activeAccount: AccountEntity?
        get() = accountManager.activeAccount

    val mediaPreviewEnabled = activeAccount?.mediaPreviewEnabled ?: false
    private val alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
    private val alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

    private val _operatorViewData = MutableStateFlow(
        setOf(
            SearchOperatorViewData.from(HasMediaOperator()),
            SearchOperatorViewData.from(DateOperator()),
            SearchOperatorViewData.from(HasEmbedOperator()),
            SearchOperatorViewData.from(FromOperator()),
            SearchOperatorViewData.from(LanguageOperator()),
            SearchOperatorViewData.from(HasLinkOperator()),
            SearchOperatorViewData.from(HasPollOperator()),
            SearchOperatorViewData.from(IsReplyOperator()),
            SearchOperatorViewData.from(IsSensitiveOperator()),
            SearchOperatorViewData.from(WhereOperator()),
        ),
    )

    /**
     * Complete set of [SearchOperatorViewData].
     *
     * Items are never added or removed from this, only replaced with [replaceOperator].
     * An item can be retrieved by class using [getOperator]
     *
     * @see [replaceOperator]
     * @see [getOperator]
     */
    val operatorViewData = _operatorViewData.asStateFlow()

    val locales = accountManager.activeAccountFlow
        .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
        .map { getLocaleList(getInitialLanguages(activeAccount = it.data)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            getLocaleList(getInitialLanguages()),
        )

    val server = serverRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    /**
     * Set of operators the server supports.
     *
     * Empty set if the server does not support any operators.
     */
    val availableOperators = serverRepository.flow.map { result ->
        result.mapBoth(
            { server ->
                buildSet {
                    val constraint100 = ">=1.0.0".toConstraint()
                    val canHasMedia = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA, constraint100)
                    val canHasImage = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE, constraint100)
                    val canHasVideo = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO, constraint100)
                    val canHasAudio = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO, constraint100)
                    if (canHasMedia || canHasImage || canHasVideo || canHasAudio) {
                        add(HasMediaOperator())
                    }

                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE, constraint100)) {
                        add(DateOperator())
                    }

                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_FROM, constraint100)) {
                        add(FromOperator())
                    }

                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE, constraint100)) {
                        add(LanguageOperator())
                    }

                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK, constraint100)) {
                        add(HasLinkOperator())
                    }
                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED, constraint100)) {
                        add(HasEmbedOperator())
                    }

                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL, constraint100)) {
                        add(HasPollOperator())
                    }
                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY, constraint100)) {
                        add(IsReplyOperator())
                    }
                    if (server.can(ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE, constraint100)) {
                        add(IsSensitiveOperator())
                    }

                    val canInLibrary = server.can(ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY, constraint100)
                    val canInPublic = server.can(ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC, constraint100)
                    if (canInLibrary || canInPublic) add(WhereOperator())
                }
            },
            {
                emptySet()
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    private val loadedStatuses: MutableList<StatusViewData> = mutableListOf()

    private val statusesPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Status, loadedStatuses) {
        it.statuses.map { status ->
            StatusViewData.from(
                status,
                isShowingContent = alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
                isExpanded = alwaysOpenSpoiler,
                isCollapsed = true,
            )
        }.apply {
            loadedStatuses.addAll(this)
        }
    }
    private val accountsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Account) {
        it.accounts
    }
    private val hashtagsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Hashtag) {
        it.hashtags
    }

    val statusesFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = statusesPagingSourceFactory,
    ).flow.cachedIn(viewModelScope)

    val accountsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = accountsPagingSourceFactory,
    ).flow.cachedIn(viewModelScope)

    val hashtagsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = hashtagsPagingSourceFactory,
    ).flow.cachedIn(viewModelScope)

    /** @return The operator of type T. */
    inline fun <reified T : SearchOperator> getOperator() = operatorViewData.value.find { it.operator is T }?.operator as T?

    /**
     * Replaces the existing [SearchOperatorViewData] in [_operatorViewData]
     * with [viewData].
     */
    fun <T : SearchOperator> replaceOperator(viewData: SearchOperatorViewData<T>) = _operatorViewData.update { operators ->
        operators.find { it.javaClass == viewData.javaClass }?.let { operators - it + viewData } ?: operators
    }

    fun search() {
        val operatorQuery = _operatorViewData.value.mapNotNull { it.operator.query() }.joinToString(" ")
        currentQuery = if (operatorQuery.isNotBlank()) arrayOf(currentSearchFieldContent, operatorQuery).joinToString(" ") else currentSearchFieldContent ?: ""

        loadedStatuses.clear()
        statusesPagingSourceFactory.newSearch(currentQuery)
        accountsPagingSourceFactory.newSearch(currentQuery)
        hashtagsPagingSourceFactory.newSearch(currentQuery)
    }

    fun removeItem(statusViewData: StatusViewData) {
        viewModelScope.launch {
            if (timelineCases.delete(statusViewData.id).isSuccess) {
                if (loadedStatuses.remove(statusViewData)) {
                    statusesPagingSourceFactory.invalidate()
                }
            }
        }
    }

    fun expandedChange(statusViewData: StatusViewData, expanded: Boolean) {
        updateStatusViewData(statusViewData.copy(isExpanded = expanded))
    }

    fun reblog(statusViewData: StatusViewData, reblog: Boolean) {
        viewModelScope.launch {
            timelineCases.reblog(statusViewData.id, reblog).fold({
                updateStatus(
                    statusViewData.status.copy(
                        reblogged = reblog,
                        reblog = statusViewData.status.reblog?.copy(reblogged = reblog),
                    ),
                )
            }, { t ->
                Timber.d(t, "Failed to reblog status %s", statusViewData.id)
            })
        }
    }

    fun contentHiddenChange(statusViewData: StatusViewData, isShowing: Boolean) {
        updateStatusViewData(statusViewData.copy(isShowingContent = isShowing))
    }

    fun collapsedChange(statusViewData: StatusViewData, collapsed: Boolean) {
        updateStatusViewData(statusViewData.copy(isCollapsed = collapsed))
    }

    fun voteInPoll(statusViewData: StatusViewData, poll: Poll, choices: List<Int>) {
        val votedPoll = poll.votedCopy(choices)
        updateStatus(statusViewData.status.copy(poll = votedPoll))
        viewModelScope.launch {
            timelineCases.voteInPoll(statusViewData.id, votedPoll.id, choices)
                .onFailure { t -> Timber.d(t, "Failed to vote in poll: %s", statusViewData.id) }
        }
    }

    fun favorite(statusViewData: StatusViewData, isFavorited: Boolean) {
        updateStatus(statusViewData.status.copy(favourited = isFavorited))
        viewModelScope.launch {
            timelineCases.favourite(statusViewData.id, isFavorited)
        }
    }

    fun bookmark(statusViewData: StatusViewData, isBookmarked: Boolean) {
        updateStatus(statusViewData.status.copy(bookmarked = isBookmarked))
        viewModelScope.launch {
            timelineCases.bookmark(statusViewData.id, isBookmarked)
        }
    }

    fun muteAccount(accountId: String, notifications: Boolean, duration: Int?) {
        viewModelScope.launch {
            timelineCases.mute(accountId, notifications, duration)
        }
    }

    fun pinAccount(status: Status, isPin: Boolean) {
        viewModelScope.launch {
            timelineCases.pin(status.id, isPin)
        }
    }

    fun blockAccount(accountId: String) {
        viewModelScope.launch {
            timelineCases.block(accountId)
        }
    }

    fun deleteStatusAsync(id: String): Deferred<NetworkResult<DeletedStatus>> {
        return viewModelScope.async {
            timelineCases.delete(id)
        }
    }

    fun muteConversation(statusViewData: StatusViewData, mute: Boolean) {
        updateStatus(statusViewData.status.copy(muted = mute))
        viewModelScope.launch {
            timelineCases.muteConversation(statusViewData.id, mute)
        }
    }

    /** Searches for autocomplete suggestions. */
    suspend fun searchAccountAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        // "resolve" is false as, by definition, the server will only return statuses it
        // knows about, therefore the accounts that posted those statuses will definitely
        // be known by the server and there is no need to resolve them further.
        return mastodonApi.search(query = token, resolve = false, type = SearchType.Account.apiParameter, limit = 10)
            .fold(
                { it.accounts.map { ComposeAutoCompleteAdapter.AutocompleteResult.AccountResult(it) } },
                {
                    Timber.e(it, "Autocomplete search for %s failed.", token)
                    emptyList()
                },
            )
    }

    private fun updateStatusViewData(newStatusViewData: StatusViewData) {
        val idx = loadedStatuses.indexOfFirst { it.id == newStatusViewData.id }
        if (idx >= 0) {
            loadedStatuses[idx] = newStatusViewData
            statusesPagingSourceFactory.invalidate()
        }
    }

    private fun updateStatus(newStatus: Status) {
        val statusViewData = loadedStatuses.find { it.id == newStatus.id }
        if (statusViewData != null) {
            updateStatusViewData(statusViewData.copy(status = newStatus))
        }
    }

    companion object {
        private const val DEFAULT_LOAD_SIZE = 20
    }
}
