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

package app.pachli.core.data.repository

import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.SuggestionsError.DeleteSuggestionError
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result

sealed interface Suggestions {
    data object Loading : Suggestions
    data class Loaded(val suggestions: List<Suggestion>) : Suggestions
}

/** Errors that can be returned from this repository */
interface SuggestionsError : ApiError {
    @JvmInline
    value class GetSuggestionsError(private val error: ApiError) : SuggestionsError, ApiError by error

    @JvmInline
    value class DeleteSuggestionError(private val error: ApiError) : SuggestionsError, ApiError by error

    // TODO: Doesn't belong here (maybe)
    @JvmInline
    value class FollowAccountError(private val error: ApiError) : SuggestionsError, ApiError by error
}

interface SuggestionsRepository {
    // TODO: Document
    suspend fun getSuggestions(): Result<List<Suggestion>, SuggestionsError.GetSuggestionsError>

    /**
     * Remove a follow suggestion.
     *
     * @param accountId ID of the account to remove
     * @return Unit, or an error
     */
    suspend fun deleteSuggestion(accountId: String): Result<Unit, DeleteSuggestionError>
}