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

import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.SuggestionsError
import app.pachli.feature.suggestions.UiAction.SuggestionAction

/** Actions the user can take from the user interface. */
internal sealed interface UiAction {
    /** Get fresh suggestions. */
    data object GetSuggestions : UiAction

    /** Actions that navigate the user to another part of the app. */
    sealed interface NavigationAction : UiAction {
        data class ViewAccount(val accountId: String) : NavigationAction
        data class ViewHashtag(val hashtag: String) : NavigationAction
        data class ViewUrl(val url: String) : NavigationAction
    }

    /** Actions that operate on a suggestion */
    sealed interface SuggestionAction : UiAction {
        val suggestion: Suggestion

        /** Delete the suggestion. */
        data class DeleteSuggestion(override val suggestion: Suggestion) : SuggestionAction

        /** Accept the suggestion and follow the user. */
        data class AcceptSuggestion(override val suggestion: Suggestion) : SuggestionAction
    }
}

internal sealed interface UiSuccess {
    val action: SuggestionAction

    data class DeleteSuggestion(override val action: SuggestionAction.DeleteSuggestion) : UiSuccess
    data class AcceptSuggestion(override val action: SuggestionAction.AcceptSuggestion) : UiSuccess

    companion object {
        fun from(action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(action)
            is SuggestionAction.AcceptSuggestion -> AcceptSuggestion(action)
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
value class AcceptSuggestionError(private val e: SuggestionsError.FollowAccountError) : SuggestionsError by e

internal sealed interface UiError {
    val error: SuggestionsError
    val action: UiAction

    data class DeleteSuggestion(
        override val error: DeleteSuggestionError,
        override val action: SuggestionAction.DeleteSuggestion,
    ) : UiError

    data class AcceptSuggestion(
        override val error: AcceptSuggestionError,
        override val action: SuggestionAction.AcceptSuggestion,
    ) : UiError

    companion object {
        fun make(error: SuggestionsError, action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(DeleteSuggestionError(error as SuggestionsError.DeleteSuggestionError), action)
            is SuggestionAction.AcceptSuggestion -> AcceptSuggestion(AcceptSuggestionError(error as SuggestionsError.FollowAccountError), action)
        }
    }
}
