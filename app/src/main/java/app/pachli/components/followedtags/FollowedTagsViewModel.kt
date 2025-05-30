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
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import com.github.michaelbull.result.mapBoth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

@HiltViewModel
class FollowedTagsViewModel @Inject constructor(
    private val api: MastodonApi,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
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

    val showFabWhileScrolling = sharedPreferencesRepository.changes
        .filter { it == null || it == PrefKeys.FAB_HIDE }
        .map { !sharedPreferencesRepository.hideFabWhenScrolling }
        .onStart { emit(!sharedPreferencesRepository.hideFabWhenScrolling) }
        .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000))

    suspend fun searchAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return api.search(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
            .mapBoth(
                {
                    val searchResult = it.body
                    searchResult.hashtags.map {
                        ComposeAutoCompleteAdapter.AutocompleteResult.HashtagResult(
                            hashtag = it.name,
                            usage7d = it.history.sumOf { it.uses },
                        )
                    }.sortedByDescending { it.usage7d }
                },
                { e ->
                    Timber.e("Autocomplete search for %s failed: %s", token, e)
                    emptyList()
                },
            )
    }
}
