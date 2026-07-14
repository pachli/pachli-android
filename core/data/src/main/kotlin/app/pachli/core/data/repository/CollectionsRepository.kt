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
import app.pachli.core.model.CollectionWithAccounts
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface CollectionsRepository {
    sealed interface Error : PachliError {
        /** Network error occurred getting the collection. */
        @JvmInline
        value class GetCollection(private val error: ApiError) : Error, PachliError by error

        /** Revoking permission for the user's account failed. */
        @JvmInline
        value class RevokeFromCollection(private val error: ApiError) : Error, PachliError by error
    }

    /**
     * Returns a flow of [CollectionWithAccounts], representing the current content of
     * [collectionId].
     */
    fun getCollection(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccounts?>

    /** Reloads [collectionId] from the server. */
    suspend fun reloadCollection(pachliAccountId: Long, collectionId: String): Result<CollectionWithAccounts, Error.GetCollection>

    /** Revokes permission for [accountId] in [collectionId]. */
    suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): Result<Unit, Error.RevokeFromCollection>

    /** Sets [collectionDisplayAction] for [collectionId]. */
    fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction)
}
