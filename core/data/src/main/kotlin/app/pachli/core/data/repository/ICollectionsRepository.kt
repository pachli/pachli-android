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
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.ICollectionsRepository.Error
import app.pachli.core.database.dao.CollectionsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.CollectionAndOwner
import app.pachli.core.database.model.CollectionViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.model.Account
import app.pachli.core.model.Collection
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.network.model.CollectionWithAccounts
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface ICollectionsRepository {
    sealed interface Error : PachliError {
        @JvmInline
        value class GetCollection(private val error: ApiError) : Error, PachliError by error

        @JvmInline
        value class RevokeFromCollection(private val error: ApiError) : Error, PachliError by error
    }

    suspend fun getCollection(pachliAccountId: Long, collectionId: String): Result<Pair<Collection, List<Account>>, Error.GetCollection>

    suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): Result<Unit, Error.RevokeFromCollection>

    /**
     *
     */
    fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction)
}

@Singleton
internal class OfflineFirstCollectionsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val localDataSource: CollectionsLocalDataSource,
    private val remoteDataSource: CollectionsRemoteDataSource,
) : ICollectionsRepository {
    /**
     * Makes a **network** request for [collectionId].
     */
    override suspend fun getCollection(pachliAccountId: Long, collectionId: String): Result<Pair<Collection, List<Account>>, Error.GetCollection> {
        // TODO: This should probably return a flow from the database, and kick off
        // an async network request.

//        return localDataSource.getCollection(pachliAccountId, collectionId).map {
//            val firstEntry = it.entries.firstOrNull() ?: return@map null
//            val collection = firstEntry.key.collection.asModel()
//            val accounts = firstEntry.value.map { it.asModel() }
//
//            Pair(collection, accounts)
//        }
        return remoteDataSource.getCollection(pachliAccountId, collectionId)
            .map { it.body }
            .mapEither(
                { Pair(it.collection.asModel(), it.accounts.asModel()) },
                { Error.GetCollection(it) },
            )
    }

    override suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): Result<Unit, Error.RevokeFromCollection> {
        return remoteDataSource.revokeFromCollection(pachliAccountId, collectionId, accountId).mapEither(
            { it.body },
            { Error.RevokeFromCollection(it) },
        ).onSuccess {
            localDataSource.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
        }
    }

    override fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction) {
        externalScope.launch {
            localDataSource.setCollectionDisplayAction(pachliAccountId, collectionId, collectionDisplayAction)
        }
    }
}

@Singleton
internal class CollectionsLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val collectionsDao: CollectionsDao,
) {
    fun getCollection(pachliAccountId: Long, collectionId: String): Flow<Map<CollectionAndOwner, List<TimelineAccountEntity>>> {
        return collectionsDao.getCollection(pachliAccountId, collectionId)
    }

    suspend fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction) {
        collectionsDao.upsertCollectionViewData(
            CollectionViewDataEntity(pachliAccountId, collectionId, collectionDisplayAction),
        )
    }

    suspend fun removeAccountFromCollection(pachliAccountId: Long, collectionId: String, accountId: String) {
        collectionsDao.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
    }
}

@Singleton
internal class CollectionsRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun getCollection(pachliAccountId: Long, collectionId: String): ApiResult<CollectionWithAccounts> {
        return mastodonApi.getCollectionWithAccounts(collectionId)
    }

    suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): ApiResult<Unit> {
        return mastodonApi.revokeItemInCollection(collectionId, accountId)
    }
}
