/*
 * Copyright 2024 Pachli Association
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

package app.pachli.feature.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.SuggestionsError
import app.pachli.core.data.repository.SuggestionsRepository
import app.pachli.core.network.model.Account
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sketch of the thinking:
 *
 * UiState covers the UI **apart** from the list. Errors fetching the list
 * are handled differently, and it could (in theory here, but not in practice)
 * be fetched by other signals in the app.
 *
 * So flows out of the viewmodel are:
 *
 * - suggestions: Flow of [Result] to populate the list or show an error,
 *   as appropriate
 *
 * - uiSuccess
 *
 * - uiErrors
 *
 * - uiState
 *
 * UiSuccess and uiError carry the action that succeeded failed so the
 * UI can easily either retry the action (if it failed), or trigger a UI
 * update if it succeeded (e.g., by emitting a new action).
 *
 * This does mean you have three very similar data classes/objects, one
 * for the action, one for if it succeeded, and one for if it failed.
 *
 * Note / refactor: Unlike UiError in TimelineViewModel etc these errors
 * don't carry the string resource for the failure with them. That's a
 * UI thing, and out of the purview of the ViewModel. Instead, the fragment
 * has an extension method that converts the UiError to a string resource.
 *
 * TODO:
 * - uiErrors and uiSuccess -- could this be a single uiResult?
 * - What's the argument for not including them in the state? Transient
 *   data does not need to survive?
 */

data class UiState(
    val animateEmojis: Boolean = false,
    val animateAvatars: Boolean = false,
    val showBotOverlay: Boolean = false,
    val accept: (UiAction) -> Unit,
) {
    companion object {
        fun make(statusDisplayOptions: StatusDisplayOptions, accept: (UiAction) -> Unit) = UiState(
            animateEmojis = statusDisplayOptions.animateEmojis,
            animateAvatars = statusDisplayOptions.animateAvatars,
            showBotOverlay = statusDisplayOptions.showBotOverlay,
            accept = accept,
        )
    }
}

sealed interface UiAction

sealed interface NavigationAction : UiAction {
    data class ViewAccount(val accountId: String) : NavigationAction
    data class ViewHashtag(val hashtag: String) : NavigationAction
    data class ViewUrl(val url: String) : NavigationAction
}

// TODO: Maybe these are "SuggestionAction" (like "StatusAction" in TimelineViewModel)
sealed interface SuggestionAction : UiAction {
    data class DeleteSuggestion(val suggestion: Suggestion) : SuggestionAction
    data class FollowAccount(val account: Account) : SuggestionAction
}

data object GetSuggestions : UiAction

sealed interface UiSuccess {
    val action: UiAction

    data class DeleteSuggestion(override val action: UiAction) : UiSuccess
    data class FollowAccount(override val action: UiAction) : UiSuccess

    companion object {
        fun from(action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(action)
            is SuggestionAction.FollowAccount -> FollowAccount(action)
        }
    }
}

// These three wrap the error types from the repository so the Fragment
// doesn't import anything from the repository.

@JvmInline
value class DeleteSuggestionError(private val e: SuggestionsError.DeleteSuggestionError) : SuggestionsError by e

@JvmInline
value class GetSuggestionsError(private val e: SuggestionsError.GetSuggestionsError) : SuggestionsError by e

@JvmInline
value class FollowAccountError(private val e: SuggestionsError.FollowAccountError) : SuggestionsError by e

sealed interface UiError {
    val error: SuggestionsError
    val action: UiAction?

    data class DeleteSuggestion(
        override val error: DeleteSuggestionError,
        override val action: SuggestionAction.DeleteSuggestion,
    ) : UiError

    data class FollowAccount(
        override val error: FollowAccountError,
        override val action: SuggestionAction.FollowAccount,
    ) : UiError

    companion object {
        fun make(error: SuggestionsError, action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(DeleteSuggestionError(error as SuggestionsError.DeleteSuggestionError), action)
            is SuggestionAction.FollowAccount -> FollowAccount(FollowAccountError(error as SuggestionsError.FollowAccountError), action)
        }
    }
}

@HiltViewModel
internal class SuggestionsViewModel @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel() {
    private val _uiSuccess = Channel<UiSuccess>()
    val uiSuccess = _uiSuccess.receiveAsFlow()

    private val _uiErrors = Channel<UiError>()
    val uiErrors = _uiErrors.receiveAsFlow()

    private val _operationCount = MutableStateFlow(0)
    val operationCount = _operationCount.asStateFlow()

//    private val _suggestions = MutableSharedFlow<Result<List<Suggestion>, GetSuggestionsError>>()
//    val suggestions = _suggestions.asSharedFlow()

    val suggestions = flow {
        // TODO: If this is commented out it triggers a UI bug that needs
        // investigating; the list is present, as is the "nothing there"
        // errorphant.
        emit(getSuggestions())
        uiAction.filterIsInstance<GetSuggestions>().collect {
            // TODO: This should be "Loading" state, not an empty list.
            // It's an empty list at the moment because duplicate emissions of
            // the same data to a state flow are ignored, so if getSuggestions()
            // returns the same data as last time the new value is never
            // collected in the fragment.
            emit(Ok(emptyList()))
            emit(getSuggestions())
        }
    }.stateIn(
        scope = viewModelScope,
        // Lazily, as we expect the user to leave the suggestions for more than
        // the typical 5 seconds (e.g., to review someone's profile before deciding
        // whether to follow them). When the user finally backs out of suggestions
        // UI this will stop.
        //
        // TODO: Is the above true if the fragment is hosted as a tab?
        started = SharingStarted.Lazily,
        initialValue = Ok(emptyList()),
    )

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    val uiState = flow {
        statusDisplayOptionsRepository.flow.collectLatest {
            emit(UiState.make(it, accept))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.make(statusDisplayOptionsRepository.flow.value, accept),
    )

    init {
        viewModelScope.launch {
            uiAction.filterIsInstance<SuggestionAction>().collect { action ->
                val result = when (action) {
                    is SuggestionAction.DeleteSuggestion -> deleteSuggestion(action.suggestion)
                    is SuggestionAction.FollowAccount -> followAccount(action.account)
                }
                result.onFailure {
                    _uiErrors.send(UiError.make(it, action))
                }
                result.onSuccess {
                    _uiSuccess.send(UiSuccess.from(action))
                }
            }
        }

//        viewModelScope.launch {
//            uiAction.filterIsInstance<GetSuggestions>().collect { action ->
// //                _suggestions.emit(getSuggestions())
//                suggestions.emit(getSuggestions())
//            }
//        }
    }

    // version of getSuggestions2 that uses `operation` to remove some boilerplate.
    private suspend fun getSuggestions(): Result<List<Suggestion>, GetSuggestionsError> = operation {
        suggestionsRepository.getSuggestions().mapError { GetSuggestionsError(it) }
    }

    private suspend fun getSuggestions2(): Result<List<Suggestion>, GetSuggestionsError> {
        _operationCount.getAndUpdate { it + 1 }
        val result = suggestionsRepository.getSuggestions()
            .mapError { GetSuggestionsError(it) }
        _operationCount.getAndUpdate { it - 1 }
        return result
    }

    private suspend fun deleteSuggestion(suggestion: Suggestion): Result<Unit, SuggestionsError> {
        _operationCount.getAndUpdate { it + 1 }
//        val result = suggestionsRepository.deleteSuggestion(suggestion.account.id)
//            .mapError { DeleteSuggestionError(it) }
        Timber.d("Request to delete $suggestion")
        _operationCount.getAndUpdate { it - 1 }
//        return result
        return Ok(Unit)
    }

    private suspend fun followAccount(account: Account): Result<Unit, SuggestionsError> {
        _operationCount.getAndUpdate { it + 1 }
        Timber.d("Request to follow account with id: ${account.id}")
        _operationCount.getAndUpdate { it - 1 }
        return Ok(Unit)
//        return Err(SuggestionsError.FollowAccountError(ApiError.from(RuntimeException())))
    }

    /**
     * Runs [block] incrementing the network operation count before [block]
     * starts, decrementing it when [block] ends.
     *
     * ```kotlin
     * suspend fun foo(): SomeType = operation {
     *     some_network_operation()
     * }
     * ```
     *
     * @return Whatever [block] returned
     */
    private suspend fun <R> operation(block: suspend() -> R): R {
        _operationCount.getAndUpdate { it + 1 }
        val result = block.invoke()
        _operationCount.getAndUpdate { it - 1 }
        return result
    }
}
