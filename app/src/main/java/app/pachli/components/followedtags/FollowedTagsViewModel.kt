package app.pachli.components.followedtags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.components.search.SearchType
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class FollowedTagsViewModel @Inject constructor(
    private val api: MastodonApi,
) : ViewModel() {
    val tags: MutableList<HashTag> = mutableListOf()
    var nextKey: String? = null
    var currentSource: FollowedTagsPagingSource? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 100),
        remoteMediator = FollowedTagsRemoteMediator(api, this),
        pagingSourceFactory = {
            FollowedTagsPagingSource(
                viewModel = this,
            ).also { source ->
                currentSource = source
            }
        },
    ).flow.cachedIn(viewModelScope)

    suspend fun searchAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return api.search(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
            .fold({ searchResult ->
                searchResult.hashtags.map { ComposeAutoCompleteAdapter.AutocompleteResult.HashtagResult(it.name) }
            }, { e ->
                Timber.e(e, "Autocomplete search for %s failed.", token)
                emptyList()
            })
    }
}
