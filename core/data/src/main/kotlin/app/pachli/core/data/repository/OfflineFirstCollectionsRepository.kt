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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.model.CollectionWithAccounts
import app.pachli.core.model.collection.CollectionDisplayAction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Singleton
internal class OfflineFirstCollectionsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val localDataSource: CollectionsLocalDataSource,
    private val remoteDataSource: CollectionsRemoteDataSource,
) : ICollectionsRepository {
    /**
     * Returns a flow of the local cached copy of [collectionId] as a
     * [app.pachli.core.model.CollectionWithAccounts]. Returns null if there is no local cached copy
     * of [collectionId].
     */
    override fun getCollection(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccounts?> =
        localDataSource.getCollection(pachliAccountId, collectionId)

    /**
     * Fetches [collectionId] from the server and if successful, updates the local copy
     * with the result.
     *
     * @return The reloaded [CollectionWithAccounts], or the error that occurred
     * performing the reload.
     */
    override suspend fun reloadCollection(pachliAccountId: Long, collectionId: String) = binding {
        externalScope.async {
            remoteDataSource.getCollection(pachliAccountId, collectionId)
                .onSuccess {
                    localDataSource.saveCollection(pachliAccountId, it)
                }
        }.await().bind()
    }

    /**
     * Calls the server to revoke [accountId] from [collectionId]. On success removes
     * [accountId] from the cached copy of [collectionId], on failure returns the
     * error.
     */
    override suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): Result<Unit, ICollectionsRepository.Error.RevokeFromCollection> {
        return remoteDataSource.revokeFromCollection(pachliAccountId, collectionId, accountId).mapEither(
            { it.body },
            { ICollectionsRepository.Error.RevokeFromCollection(it) },
        ).onSuccess {
            localDataSource.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
        }
//        localDataSource.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
//        return Ok(Unit)
    }

    override fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction) {
        externalScope.launch {
            localDataSource.setCollectionDisplayAction(pachliAccountId, collectionId, collectionDisplayAction)
        }
    }
}
