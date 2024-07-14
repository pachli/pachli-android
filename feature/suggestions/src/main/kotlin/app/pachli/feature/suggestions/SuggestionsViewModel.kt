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
import app.pachli.core.common.extensions.mapIfInstance
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.SuggestionsError.DeleteSuggestionError
import app.pachli.core.data.repository.SuggestionsError.FollowAccountError
import app.pachli.core.data.repository.SuggestionsRepository
import app.pachli.feature.suggestions.UiAction.GetSuggestions
import app.pachli.feature.suggestions.UiAction.SuggestionAction
import app.pachli.feature.suggestions.UiAction.SuggestionAction.AcceptSuggestion
import app.pachli.feature.suggestions.UiAction.SuggestionAction.DeleteSuggestion
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * High-level UI state, derived from [StatusDisplayOptions].
 *
 * @property animateEmojis True if emojis should be animated
 * @property animateAvatars True if avatars should be animated
 * @property showBotOverlay True if avatars for bot accounts should show an overlay
 */
internal data class UiState(
    val animateEmojis: Boolean = false,
    val animateAvatars: Boolean = false,
    val showBotOverlay: Boolean = false,
) {
    companion object {
        fun from(statusDisplayOptions: StatusDisplayOptions) = UiState(
            animateEmojis = statusDisplayOptions.animateEmojis,
            animateAvatars = statusDisplayOptions.animateAvatars,
            showBotOverlay = statusDisplayOptions.showBotOverlay,
        )
    }
}

/** Data to show a [Suggestion]. */
internal data class SuggestionViewData(
    /** If false the user should not be able to interact with the suggestion. */
    val isEnabled: Boolean = true,
    /** The suggestion. */
    val suggestion: Suggestion,
)

/** States for the list of suggestions. */
internal sealed interface Suggestions {
    /** Suggestions are being loaded. */
    data object Loading : Suggestions

    /** Loaded suggestions, in [suggestions] */
    data class Loaded(val suggestions: List<SuggestionViewData>) : Suggestions
}

/** Public interface for [SuggestionsViewModel]. */
internal interface ISuggestionsViewModel {
    /** Asynchronous receiver for [UiAction]. */
    val accept: (UiAction) -> Unit

    /**
     * Results from each action sent to [accept]. Results may not be returned
     * in the same order as the actions.
     */
    val uiResult: Flow<Result<UiSuccess, UiError>>

    /**
     * Count of active network operations. Every API call increments this on
     * start and decrements on finish.
     *
     * If this is non-zero the UI should show a "loading" indicator of some
     * sort.
     */
    val operationCount: Flow<Int>

    /** Suggestions to display, with associated error. */
    val suggestions: StateFlow<Result<Suggestions, GetSuggestionsError>>

    /** Additional UI state metadata. */
    val uiState: Flow<UiState>
}

@HiltViewModel
internal class SuggestionsViewModel @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel(),
    ISuggestionsViewModel {
    private val uiAction = MutableSharedFlow<UiAction>()
    override val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    override val uiResult = _uiResult.receiveAsFlow()

    private val _operationCount = MutableStateFlow(0)
    override val operationCount = _operationCount.asStateFlow()

    private val reload = MutableSharedFlow<Unit>(replay = 1)

    private var disabledSuggestions = MutableStateFlow<Set<String>>(setOf()) // mutableSetOf<String>()

    private var _suggestions = MutableStateFlow<Result<Suggestions, GetSuggestionsError>>(Ok(Suggestions.Loading))
    override val suggestions = stateFlow(viewModelScope, Ok(Suggestions.Loading)) {
        disabledSuggestions.combine(_suggestions) { disabled, suggestions ->
            // Mark any disabled suggestions.
            suggestions.mapIfInstance<_, _, Suggestions.Loaded> {
                it.copy(
                    suggestions = it.suggestions.map {
                        it.copy(isEnabled = !disabled.contains(it.suggestion.account.id))
                    },
                )
            }
        }.flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    override val uiState = stateFlow(viewModelScope, UiState.from(statusDisplayOptionsRepository.flow.value)) {
        statusDisplayOptionsRepository.flow.map { UiState.from(it) }
            .flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    init {
        viewModelScope.launch {
            uiAction.filterIsInstance<SuggestionAction>().collect {
                launch { onSuggestionAction(it) }
            }
        }

        viewModelScope.launch {
            reload.collect {
                _suggestions.emit(Ok(Suggestions.Loading))
                _suggestions.emit(getSuggestions())
            }
        }

        viewModelScope.launch {
            reload.emit(Unit)
            uiAction.filterIsInstance<GetSuggestions>().collect { reload.emit(Unit) }
        }
    }

    /**
     * Process each [SuggestionAction].
     *
     * Successful actions do not reload from the server, as there is no guarantee the
     * reloaded list would contain the same accounts in the same order, so reloading
     * risks the user losing their place.
     */
    private suspend fun onSuggestionAction(suggestionAction: SuggestionAction) {
        // Mark this suggestion as disabled for the duration of the operation.
        disabledSuggestions.update { it.plus(suggestionAction.suggestion.account.id) }

        // Process the suggestion, and handle the success/failure
        val result = when (suggestionAction) {
            is DeleteSuggestion -> deleteSuggestion(suggestionAction.suggestion)
            is AcceptSuggestion -> acceptSuggestion(suggestionAction.suggestion)
        }.mapEither(
            {
                // Remove this suggestion from the list.
                _suggestions.update { suggestions ->
                    suggestions.mapIfInstance<_, _, Suggestions.Loaded> {
                        it.copy(
                            suggestions = it.suggestions.filterNot {
                                it.suggestion.account.id == suggestionAction.suggestion.account.id
                            },
                        )
                    }
                }
                UiSuccess.from(suggestionAction)
            },
            { UiError.make(it, suggestionAction) },
        )

        // Re-enable the suggestion.
        disabledSuggestions.update { it.minus(suggestionAction.suggestion.account.id) }
        _uiResult.send(result)
    }

    /** Get fresh suggestions from the repository. */
    private suspend fun getSuggestions(): Result<Suggestions.Loaded, GetSuggestionsError> = operation {
        // Note: disabledSuggestions is *not* cleared here. Suppose the user has
        // dismissed a suggestion and the network operation has not completed yet.
        // They reload, and get a list of suggestions that includes the suggestion
        // they have just dismissed. In that case the suggestion should still be
        // disabled.
        suggestionsRepository.getSuggestions().mapEither(
            { Suggestions.Loaded(it.map { SuggestionViewData(suggestion = it) }) },
            { GetSuggestionsError(it) },
        )
    }

    /** Delete a suggestion from the repository. */
    private suspend fun deleteSuggestion(suggestion: Suggestion): Result<Unit, DeleteSuggestionError> = operation {
        suggestionsRepository.deleteSuggestion(suggestion.account.id)
    }

    /** Accept the suggestion and follow the account. */
    private suspend fun acceptSuggestion(suggestion: Suggestion): Result<Unit, FollowAccountError> = operation {
        suggestionsRepository.followAccount(suggestion.account.id)
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
    private suspend fun <R> operation(block: suspend () -> R): R {
        _operationCount.getAndUpdate { it + 1 }
        val result = block.invoke()
        _operationCount.getAndUpdate { it - 1 }
        return result
    }
}
