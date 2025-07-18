/* Copyright 2023 Tusky Contributors
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

package app.pachli.components.trending.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.model.FilterContext
import app.pachli.core.model.TrendingTag
import app.pachli.core.model.end
import app.pachli.core.model.start
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.viewdata.TrendingViewData
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class TrendingTagsViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
) : ViewModel() {
    enum class LoadingState {
        INITIAL,
        LOADING,
        REFRESHING,
        LOADED,
        ERROR_NETWORK,
        ERROR_OTHER,
    }

    data class TrendingTagsUiState(
        val trendingViewData: List<TrendingViewData>,
        val loadingState: LoadingState,
    )

    val uiState: Flow<TrendingTagsUiState> get() = _uiState
    private val _uiState = MutableStateFlow(TrendingTagsUiState(listOf(), LoadingState.INITIAL))

    private val contentFilters = flow {
        accountManager.activePachliAccountFlow.filterNotNull()
            .distinctUntilChangedBy { it.contentFilters }
            .map { it.contentFilters }
            .collect(::emit)
    }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    init {
        viewModelScope.launch {
            contentFilters.collect { invalidate() }
        }
    }

    /**
     * Invalidate the current list of trending tags and fetch a new list.
     *
     * A tag is excluded if it is filtered by the user on their home timeline.
     */
    fun invalidate(refresh: Boolean = false) = viewModelScope.launch {
        if (refresh) {
            _uiState.value = TrendingTagsUiState(emptyList(), LoadingState.REFRESHING)
        } else {
            _uiState.value = TrendingTagsUiState(emptyList(), LoadingState.LOADING)
        }

        val contentFilters = contentFilters.replayCache.last()

        mastodonApi.trendingTags(limit = LIMIT_TRENDING_HASHTAGS)
            .map { it.body.asModel() }
            .onSuccess { tagResponse ->
                val firstTag = tagResponse.firstOrNull()
                _uiState.value = if (firstTag == null) {
                    TrendingTagsUiState(emptyList(), LoadingState.LOADED)
                } else {
                    val homeFilters = contentFilters.contentFilters.filter { filter ->
                        filter.contexts.contains(FilterContext.HOME)
                    }
                    val tags = tagResponse
                        .filter { tag ->
                            homeFilters.none { filter ->
                                filter.keywords.any { keyword -> keyword.keyword.equals(tag.name, ignoreCase = true) }
                            }
                        }
                        .sortedByDescending { tag -> tag.history.sumOf { it.uses.toLongOrNull() ?: 0 } }
                        .toTrendingViewDataTag()

                    val header = TrendingViewData.Header(firstTag.start(), firstTag.end())
                    TrendingTagsUiState(listOf(header) + tags, LoadingState.LOADED)
                }
            }
            .onFailure { error ->
                Timber.w("failed loading trending tags: %s", error)
                if (error.throwable is IOException) {
                    _uiState.value = TrendingTagsUiState(emptyList(), LoadingState.ERROR_NETWORK)
                } else {
                    _uiState.value = TrendingTagsUiState(emptyList(), LoadingState.ERROR_OTHER)
                }
            }
    }

    private fun List<TrendingTag>.toTrendingViewDataTag(): List<TrendingViewData.Tag> {
        val maxTrendingValue = flatMap { tag -> tag.history }
            .mapNotNull { it.uses.toLongOrNull() }
            .maxOrNull() ?: 1

        return map { TrendingViewData.Tag.from(it, maxTrendingValue) }
    }

    companion object {
        /**
         * How many trending hashtags to fetch. These are not paged, so fetch the
         * documented (https://docs.joinmastodon.org/methods/trends/#query-parameters)
         * maximum.
         */
        const val LIMIT_TRENDING_HASHTAGS = 20
    }
}
