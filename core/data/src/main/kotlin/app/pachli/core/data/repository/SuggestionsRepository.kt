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
import app.pachli.core.data.repository.SuggestionsError.FollowAccountError
import app.pachli.core.data.repository.SuggestionsError.GetSuggestionsError
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result

/** Errors that can be returned from this repository. */
sealed interface SuggestionsError {
    val cause: ApiError

    @JvmInline
    value class GetSuggestionsError(override val cause: ApiError) : SuggestionsError

    @JvmInline
    value class DeleteSuggestionError(override val cause: ApiError) : SuggestionsError

    // TODO: Doesn't belong here. When there's a repository for the user's account
    // this should move there.
    @JvmInline
    value class FollowAccountError(override val cause: ApiError) : SuggestionsError
}

interface SuggestionsRepository {
    // TODO: Document
    suspend fun getSuggestions(): Result<List<Suggestion>, GetSuggestionsError>

    /**
     * Remove a follow suggestion.
     *
     * @param accountId ID of the account to remove
     * @return Unit, or an error
     */
    suspend fun deleteSuggestion(accountId: String): Result<Unit, DeleteSuggestionError>

    /**
     * Follow an account from a suggestion
     *
     * @param accountId ID of the account to follow
     * @return Unit, or an error
     */
    suspend fun followAccount(accountId: String): Result<Unit, FollowAccountError>
}
