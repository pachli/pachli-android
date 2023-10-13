package app.pachli.components.followedtags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.components.search.SearchType
import app.pachli.entity.HashTag
import app.pachli.network.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

    fun searchAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return api.searchSync(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
            .fold({ searchResult ->
                searchResult.hashtags.map { ComposeAutoCompleteAdapter.AutocompleteResult.HashtagResult(it.name) }
            }, { e ->
                Log.e(TAG, "Autocomplete search for $token failed.", e)
                emptyList()
            },)
    }

    companion object {
        private const val TAG = "FollowedTagsViewModel"
    }
}
