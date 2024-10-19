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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.repository.SuggestionsError.DeleteSuggestionError
import app.pachli.core.data.repository.SuggestionsError.FollowAccountError
import app.pachli.core.data.repository.SuggestionsError.GetSuggestionsError
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

@Singleton
class NetworkSuggestionsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val api: MastodonApi,
) : SuggestionsRepository {
    override suspend fun getSuggestions(): Result<List<Suggestion>, GetSuggestionsError> = binding {
        val now = Instant.now()
        api.getSuggestions(limit = 80)
            .map { response ->
                response.body
                    // Filter out accounts that haven't posted in the last LAST_STATUS_CUTOFF_DURATION.
                    .filter {
                        it.account.lastStatusAt?.let { lastStatusAt ->
                            Duration.between(lastStatusAt.toInstant(), now) < LAST_STATUS_CUTOFF_DURATION
                        } == true
                    }
                    .map { Suggestion.from(it) }
            }
            .mapError { GetSuggestionsError(it) }
            .bind()
    }

    override suspend fun deleteSuggestion(accountId: String): Result<Unit, DeleteSuggestionError> = binding {
        externalScope.async {
            api.deleteSuggestion(accountId).mapError { DeleteSuggestionError(it) }.bind()
        }.await()
    }

    override suspend fun followAccount(accountId: String): Result<Unit, FollowAccountError> = binding {
        externalScope.async {
            api.followSuggestedAccount(accountId).mapError { FollowAccountError(it) }.bind()
        }.await()
    }

    companion object {
        /**
         * Duration that specifies the maximum time in the past the account must have
         * posted a status for inclusion.
         */
        private val LAST_STATUS_CUTOFF_DURATION = Duration.ofDays(28)
    }
}
