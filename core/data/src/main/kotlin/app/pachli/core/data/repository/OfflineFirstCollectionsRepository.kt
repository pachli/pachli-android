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
import app.pachli.core.database.dao.CollectionsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.CollectionViewDataEntity
import app.pachli.core.database.model.asEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.CollectionWithAccounts
import app.pachli.core.model.asTimelineAccount
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/**
 * Data source for locally cached [Collection] data.
 */
@Singleton
internal class CollectionsLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val collectionsDao: CollectionsDao,
    private val accountRepository: IAccountRepository,
) {
    /**
     * @return Flow of [CollectionWithAccounts] for [collectionId].
     */
    fun getCollection(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccounts?> {
        return collectionsDao.getCollection(pachliAccountId, collectionId)
            .map {
                it.firstNotNullOfOrNull { (collectionAndOwner, accounts) ->
                    CollectionWithAccounts(
                        collection = collectionAndOwner.collection.asModel(),
                        owner = collectionAndOwner.ownerAccount?.asModel(),
                        accounts = accounts.asModel(),
                    )
                }
            }
    }

    /**
     * Saves the collection and accounts in [collectionWithAccounts] locally.
     *
     * @param pachliAccountId
     * @param collectionWithAccounts
     */
    suspend fun saveCollection(pachliAccountId: Long, collectionWithAccounts: CollectionWithAccounts) {
        transactionProvider {
            collectionsDao.upsertCollection(collectionWithAccounts.collection.asEntity(pachliAccountId))
            collectionsDao.upsertCollectionItems(
                collectionWithAccounts.collection.items.asEntity(
                    pachliAccountId,
                    collectionWithAccounts.collection.serverId,
                ),
            )
            accountRepository.saveAccounts(
                pachliAccountId,
                buildSet {
                    collectionWithAccounts.owner?.let { add(it) }
                    addAll(collectionWithAccounts.accounts)
                },
            )
            // Save owner account as `TimelineAccount`, as `TimelineCollection` uses
            // `TimelineAccount` for the `ownerAccount` property.
            collectionWithAccounts.owner?.let {
                accountRepository.saveTimelineAccount(pachliAccountId, it.asTimelineAccount())
            }
        }
    }

    /**
     * Sets the [collectionDisplayAction] for [collectionId].
     *
     * @param pachliAccountId
     * @param collectionId
     * @param collectionDisplayAction New [CollectionDisplayAction].
     */
    suspend fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction) {
        collectionsDao.upsertCollectionViewData(
            CollectionViewDataEntity(pachliAccountId, collectionId, collectionDisplayAction),
        )
    }

    /**
     * Removes [accountId] from the cached copy of [collectionId].
     */
    suspend fun removeAccountFromCollection(pachliAccountId: Long, collectionId: String, accountId: String) {
        collectionsDao.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
    }
}

@Singleton
internal class CollectionsRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun getCollection(pachliAccountId: Long, collectionId: String) = binding {
        mastodonApi.getCollectionWithAccounts(collectionId)
            .mapEither(
                { it.body.asModel() },
                { ICollectionsRepository.Error.GetCollection(it) },
            )
            .bind()
    }

    suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): ApiResult<Unit> {
        return mastodonApi.revokeItemInCollection(collectionId, accountId)
    }
}

fun <T1, T2, R> combineFlatMapLatest(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: suspend (T1, T2) -> Flow<R>,
): Flow<R> {
    return combine(flow1, flow2) { first, second -> first to second }.flatMapLatest { pair ->
        transform(pair.first, pair.second)
    }
}

fun <T1, T2, T3, R> combineFlatMapLatest(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: suspend (T1, T2, T3) -> Flow<R>,
): Flow<R> {
    return combine(flow1, flow2, flow3) { first, second, third -> Triple(first, second, third) }.flatMapLatest { triple ->
        transform(triple.first, triple.second, triple.third)
    }
}

fun <T1, T2, T3, T4, T5, R> combineFlatMapLatest(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    transform: suspend (T1, T2, T3, T4, T5) -> Flow<R>,
): Flow<R> {
    data class Tuple5<T1, T2, T3, T4, T5>(
        val v1: T1,
        val v2: T2,
        val v3: T3,
        val v4: T4,
        val v5: T5,
    )

    return combine(flow1, flow2, flow3, flow4, flow5) { array ->
        @Suppress("UNCHECKED_CAST")
        Tuple5(
            array[0] as T1,
            array[1] as T2,
            array[2] as T3,
            array[3] as T4,
            array[4] as T5,
        )
    }.flatMapLatest { tuple ->
        transform(tuple.v1, tuple.v2, tuple.v3, tuple.v4, tuple.v5)
    }
}
