package app.pachli.components.followedtags

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.components.search.SearchType
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.hashtags.HashtagsRepository
import app.pachli.core.model.Hashtag
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.OperationCounter
import app.pachli.feature.intentrouter.on
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapEither
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

internal sealed interface UiAction

internal sealed interface InfallibleUiAction : UiAction {
    data class SetPachliAccountId(val pachliAccountId: Long) : InfallibleUiAction
}

internal sealed interface FallibleUiAction : UiAction {
    /** Reload hashtags from server. */
    data class Reload(val pachliAccountId: Long) : FallibleUiAction

    /** Follow [tagName]. */
    data class FollowHashtag(
        val pachliAccountId: Long,
        val tagName: String,
    ) : FallibleUiAction

    /** Unfollow [tagName]. */
    data class UnfollowHashtag(
        val pachliAccountId: Long,
        val tagName: String,
    ) : FallibleUiAction
}

internal sealed interface UiSuccess {
    val action: UiAction

    data class FollowHashtag(
        override val action: FallibleUiAction.FollowHashtag,
        val hashtag: Hashtag,
    ) : UiSuccess

    data class UnfollowHashtag(
        override val action: FallibleUiAction.UnfollowHashtag,
        val hashtag: Hashtag,
    ) : UiSuccess
}

internal sealed class UiError(
    open val action: FallibleUiAction,
    @StringRes override val resourceId: Int,
    override val formatArgs: Array<out Any>? = null,
) : PachliError {
    data class FollowHashtag(
        override val action: FallibleUiAction.FollowHashtag,
        override val cause: PachliError,
    ) : UiError(action, R.string.error_following_hashtag_format, arrayOf(action.tagName))

    data class UnfollowHashtag(
        override val action: FallibleUiAction.UnfollowHashtag,
        override val cause: PachliError,
    ) : UiError(action, R.string.error_unfollowing_hashtag_format, arrayOf(action.tagName))
}

@HiltViewModel
class FollowedTagsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hashtagsRepository: HashtagsRepository,
    private val api: MastodonApi,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    private val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    private val uiAction = MutableSharedFlow<UiAction>()

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    internal val uiResult = _uiResult.receiveAsFlow()

    /** Accept UI actions. */
    internal val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    val followedHashtags = pachliAccountId.flatMapLatest {
        hashtagsRepository.getFollowedHashtags(it).map { Loadable.Loaded(it) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Loadable.Loading,
    )

    val showFabWhileScrolling = sharedPreferencesRepository.changes
        .filter { it == null || it == PrefKeys.FAB_HIDE }
        .map { !sharedPreferencesRepository.hideFabWhenScrolling }
        .onStart { emit(!sharedPreferencesRepository.hideFabWhenScrolling) }
        .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000))

    private val operationCounter = OperationCounter()

    /** True if the progress indicator should be shown. */
    val showProgress = combine(operationCounter.count, followedHashtags) { ops, loadable ->
        ops > 0 || loadable is Loadable.Loading
    }

    init {
        viewModelScope.launch {
            uiAction.throttleFirst(500.milliseconds).collect {
                when (it) {
                    is InfallibleUiAction.SetPachliAccountId -> pachliAccountId.emit(it.pachliAccountId)
                    is FallibleUiAction.Reload -> {
                        operationCounter {
                            hashtagsRepository.refreshFollowedHashtags(it.pachliAccountId)
                        }
                    }
                    is FallibleUiAction.FollowHashtag -> followHashtag(it)
                    is FallibleUiAction.UnfollowHashtag -> unfollowHashtag(it)
                }
            }
        }
    }

    private suspend fun followHashtag(action: FallibleUiAction.FollowHashtag) {
        operationCounter {
            hashtagsRepository.followHashtag(
                action.pachliAccountId,
                action.tagName,
            )
        }.mapEither(
            { UiSuccess.FollowHashtag(action, it) },
            { UiError.FollowHashtag(action, it) },
        ).on { _uiResult.send(it) }
    }

    private suspend fun unfollowHashtag(action: FallibleUiAction.UnfollowHashtag) {
        operationCounter {
            hashtagsRepository.unfollowHashtag(
                action.pachliAccountId,
                action.tagName,
            )
        }.mapEither(
            { UiSuccess.UnfollowHashtag(action, it) },
            { UiError.UnfollowHashtag(action, it) },
        ).on { _uiResult.send(it) }
    }

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
