/*
 * Copyright (c) 2026 Pachli Association
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

import app.pachli.core.common.PachliError
import app.pachli.core.data.repository.RelationshipsRepository.RelationshipError.GetRelationshipsError
import app.pachli.core.model.Relationship
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result

/** Operations that can be performed on this repository. */
interface RelationshipsRepository {
    /** Errors that can be returned from this repository. */
    sealed interface RelationshipError : PachliError {
        /** Fetching relationships failed. */
        @JvmInline
        value class GetRelationshipsError(private val error: ApiError) :
            RelationshipError,
            PachliError by error
    }

    /**
     * Fetches the relationships between the user and [accountIds].
     *
     * @param pachliAccountId
     * @param accountIds List of [account IDs][app.pachli.core.model.ITimelineAccount.accountId]
     */
    suspend fun getRelationships(
        pachliAccountId: Long,
        accountIds: List<String>,
    ): Result<List<Relationship>, GetRelationshipsError>
}
