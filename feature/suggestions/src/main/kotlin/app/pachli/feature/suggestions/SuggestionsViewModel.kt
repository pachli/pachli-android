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
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.SuggestionsError.DeleteSuggestionError
import app.pachli.core.data.repository.SuggestionsError.FollowAccountError
import app.pachli.core.data.repository.SuggestionsRepository
import app.pachli.core.data.repository.mapLoaded
import app.pachli.core.domain.accounts.FollowAccountUseCase
import app.pachli.core.model.Relationship
import app.pachli.core.model.Suggestion
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.OperationCounter
import app.pachli.feature.suggestions.UiAction.GetSuggestions
import app.pachli.feature.suggestions.UiAction.SuggestionAction
import app.pachli.feature.suggestions.UiAction.SuggestionAction.AcceptSuggestion
import app.pachli.feature.suggestions.UiAction.SuggestionAction.DeleteSuggestion
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
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
    val showPronouns: Boolean = false,
    val linksToUnderline: Set<LinksToUnderline> = emptySet(),
    val renderMarkdown: Boolean = false,
) {
    companion object {
        fun from(statusDisplayOptions: StatusDisplayOptions) = UiState(
            animateEmojis = statusDisplayOptions.animateEmojis,
            animateAvatars = statusDisplayOptions.animateAvatars,
            showBotOverlay = statusDisplayOptions.showBotOverlay,
            showPronouns = statusDisplayOptions.pronounDisplay == PronounDisplay.EVERYWHERE,
            linksToUnderline = statusDisplayOptions.linksToUnderline,
            renderMarkdown = statusDisplayOptions.renderMarkdown,
        )
    }
}

/**
 * Data to show a [Suggestion].
 *
 * @property pachliAccountId
 * @property isEnabled If false the user should not be able to interact
 * with the suggestion.
 * @property suggestion The [Suggestion].
 */
internal data class SuggestionViewData(
    val pachliAccountId: Long,
    val isEnabled: Boolean = true,
    val suggestion: Suggestion,
)

/** States for the list of suggestions. */
internal typealias Suggestions = Loadable<List<SuggestionViewData>>

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
    private val followAccountUseCase: FollowAccountUseCase,
) : ViewModel(),
    ISuggestionsViewModel {
    private val uiAction = MutableSharedFlow<UiAction>()
    override val accept: (UiAction) -> Unit = { action -> viewModelScope.launch { uiAction.emit(action) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    override val uiResult = _uiResult.receiveAsFlow()

    private val operationCounter = OperationCounter()
    override val operationCount = operationCounter.count

    private val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    /**
     * Account IDs of suggestions that should be disabled in the UI.
     * E.g., because there is an active operation involving the account.
     */
    private var disabledSuggestions = MutableStateFlow<Set<String>>(setOf())

    /** Cache of suggestions fetched from the network. */
    // Modified depending on the user's actions (e.g., rejecting or accepting)
    // a suggestion, as refreshing on each network operation could return a
    // completely different list of suggestions, losing the user's place in
    // the list.
    private var _suggestions = MutableStateFlow<Result<Loadable<List<Suggestion>>, GetSuggestionsError>>(Ok(Loadable.Loading))

    override val suggestions = stateFlow(viewModelScope, Ok(Loadable.Loading)) {
        combine(pachliAccountId, _suggestions, disabledSuggestions) { pachliAccountId, suggestionsResult, disabled ->
            suggestionsResult.map {
                it.mapLoaded { suggestions ->
                    suggestions.map {
                        SuggestionViewData(
                            pachliAccountId = pachliAccountId,
                            suggestion = it,
                            isEnabled = !disabled.contains(it.account.id),
                        )
                    }
                }
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
            uiAction.filterIsInstance<GetSuggestions>().collect {
                pachliAccountId.emit(it.pachliAccountId)
            }
        }

        viewModelScope.launch {
            pachliAccountId.collect {
                operationCounter {
                    _suggestions.value = Ok(Loadable.Loading)
                    _suggestions.value = suggestionsRepository.getSuggestions().mapEither(
                        { Loadable.Loaded(it) },
                        { GetSuggestionsError(it) },
                    )
                }
            }
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
            is DeleteSuggestion -> deleteSuggestion(suggestionAction)
            is AcceptSuggestion -> acceptSuggestion(suggestionAction)
        }.onSuccess {
            // Remove this suggestion from _suggestions, updates the UI.
            _suggestions.update { suggestions ->
                suggestions.map { loadable ->
                    loadable.mapLoaded { suggestions ->
                        suggestions.filterNot { suggestion ->
                            suggestion.account.id == suggestionAction.suggestion.account.id
                        }
                    }
                }
            }
        }.mapEither(
            { UiSuccess.from(suggestionAction) },
            { UiError.make(it, suggestionAction) },
        )

        // Re-enable the suggestion.
        disabledSuggestions.update { it.minus(suggestionAction.suggestion.account.id) }
        _uiResult.send(result)
    }

    /** Delete a suggestion from the repository. */
    private suspend fun deleteSuggestion(action: DeleteSuggestion): Result<Unit, DeleteSuggestionError> = operationCounter {
        suggestionsRepository.deleteSuggestion(action.suggestion.account.id)
    }

    /**
     * Follow an account from a suggestion
     *
     * @param action [AcceptSuggestion] containing the account to follow.
     * @return Result with the new relationship, or an error.
     */
    private suspend fun acceptSuggestion(action: AcceptSuggestion): Result<Relationship, FollowAccountError> = operationCounter {
        followAccountUseCase(action.pachliAccountId, action.suggestion.account.id)
            .mapError { FollowAccountError(it) }
    }
}
