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

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.SuggestionsError
import app.pachli.core.data.repository.SuggestionsError.DeleteSuggestionError
import app.pachli.core.data.repository.SuggestionsError.FollowAccountError
import app.pachli.feature.suggestions.UiAction.SuggestionAction

/** Actions the user can take from the UI. */
internal sealed interface UiAction {
    /** Get fresh suggestions. */
    data object GetSuggestions : UiAction

    /** Actions that navigate the user to another part of the app. */
    sealed interface NavigationAction : UiAction {
        data class ViewAccount(val accountId: String) : NavigationAction
        data class ViewHashtag(val hashtag: String) : NavigationAction
        data class ViewUrl(val url: String) : NavigationAction
    }

    /** Actions that operate on a suggestion. */
    sealed interface SuggestionAction : UiAction {
        val suggestion: Suggestion

        /** Delete the suggestion. */
        data class DeleteSuggestion(override val suggestion: Suggestion) : SuggestionAction

        /** Accept the suggestion and follow the user. */
        data class AcceptSuggestion(override val suggestion: Suggestion) : SuggestionAction
    }
}

/** Represents actions that succeeded. */
internal sealed interface UiSuccess {
    /** The successful action. */
    val action: SuggestionAction

    /** A successful [SuggestionAction.DeleteSuggestion]. */
    data class DeleteSuggestion(override val action: SuggestionAction.DeleteSuggestion) : UiSuccess

    /** A successful [SuggestionAction.AcceptSuggestion]. */
    data class AcceptSuggestion(override val action: SuggestionAction.AcceptSuggestion) : UiSuccess

    companion object {
        /** Create a [UiSuccess] from a [SuggestionAction]. */
        fun from(action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(action)
            is SuggestionAction.AcceptSuggestion -> AcceptSuggestion(action)
        }
    }
}

@JvmInline
value class GetSuggestionsError(val error: SuggestionsError.GetSuggestionsError) : PachliError by error

/**
 * Errors that can occur from actions the user takes in the UI.
 */
internal sealed class UiError(
    @StringRes override val resourceId: Int,
    open val action: SuggestionAction,
    override val cause: SuggestionsError,
    override val formatArgs: Array<out String>? = action.suggestion.account.displayName?.let { arrayOf(it) },
) : PachliError {

    /** A failed [SuggestionAction.DeleteSuggestion]. */
    data class DeleteSuggestion(
        override val action: SuggestionAction.DeleteSuggestion,
        override val cause: DeleteSuggestionError,
    ) : UiError(R.string.ui_error_delete_suggestion_fmt, action, cause)

    /** A failed [SuggestionAction.AcceptSuggestion]. */
    data class AcceptSuggestion(
        override val action: SuggestionAction.AcceptSuggestion,
        override val cause: FollowAccountError,
    ) : UiError(R.string.ui_error_follow_account_fmt, action, cause)

    companion object {
        /** Create a [UiError] from the [SuggestionAction] and [SuggestionsError]. */
        fun make(error: SuggestionsError, action: SuggestionAction) = when (action) {
            is SuggestionAction.DeleteSuggestion -> DeleteSuggestion(action, error as DeleteSuggestionError)
            is SuggestionAction.AcceptSuggestion -> AcceptSuggestion(action, error as FollowAccountError)
        }
    }
}
