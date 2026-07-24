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
import app.pachli.core.model.collection.CollectionCardViewData
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/** [CollectionsRepository] that caches collection data locally. */
@Singleton
internal class OfflineFirstCollectionsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val transactionProvider: TransactionProvider,
    private val localDataSource: CollectionsLocalDataSource,
    private val remoteDataSource: CollectionsRemoteDataSource,
) : CollectionsRepository {
    /**
     * Returns a flow of the local cached copy of [collectionId] as a
     * [app.pachli.core.model.CollectionWithAccounts]. Returns null if there is no local cached copy
     * of [collectionId].
     */
    override fun getCollectionFlow(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccounts> =
        localDataSource.getCollectionFlow(pachliAccountId, collectionId)

    /**
     * Returns the available [CollectionCardViewData] for [collectionIds].
     */
    override suspend fun getCollectionCardViewData(pachliAccountId: Long, collectionIds: Collection<String>): List<CollectionCardViewData> {
        return localDataSource.getCollectionCardViewData(pachliAccountId, collectionIds)
    }

    /**
     * Fetches [collectionId] from the server and if successful, updates the local copy
     * with the result.
     *
     * @return The reloaded [CollectionWithAccounts], or the error that occurred
     * performing the reload.
     */
    override suspend fun reloadCollection(pachliAccountId: Long, collectionId: String) = remoteDataSource.getCollection(pachliAccountId, collectionId)
        .onSuccess { localDataSource.saveCollection(pachliAccountId, it) }
        .onFailure { Timber.e("Couldn't fetch $collectionId: $it") }

    /**
     * Fetches [collectionIds] from the server. If a collection is fetched successfully
     * the local copy is updated.
     *
     * @return A list of the results of loading each collection.
     */
    override suspend fun reloadCollections(pachliAccountId: Long, collectionIds: Collection<String>): List<Result<CollectionWithAccounts, CollectionsRepository.Error.GetCollection>> {
        val results = remoteDataSource.getCollections(pachliAccountId, collectionIds)
        results.forEach {
            it.onSuccess { localDataSource.saveCollection(pachliAccountId, it) }
                .onFailure { Timber.e("Couldn't fetch: $it") }
        }
        return results
    }

    /**
     * Calls the server to revoke [accountId] from [collectionId]. On success removes
     * [accountId] from the cached copy of [collectionId], on failure returns the
     * error.
     */
    override suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): Result<Unit, CollectionsRepository.Error.RevokeFromCollection> {
        return externalScope.async {
            remoteDataSource.revokeFromCollection(pachliAccountId, collectionId, accountId).mapEither(
                { },
                { CollectionsRepository.Error.RevokeFromCollection(it) },
            ).onSuccess {
                localDataSource.removeAccountFromCollection(pachliAccountId, collectionId, accountId)
            }
        }.await()
    }

    override fun setCollectionDisplayAction(pachliAccountId: Long, collectionId: String, collectionDisplayAction: CollectionDisplayAction) {
        externalScope.launch {
            localDataSource.setCollectionDisplayAction(pachliAccountId, collectionId, collectionDisplayAction)
        }
    }
}

/** Data source for locally cached [Collection] data. */
@Singleton
internal class CollectionsLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val collectionsDao: CollectionsDao,
    private val accountRepository: AccountRepository,
) {
    /**
     * @return Flow of [CollectionWithAccounts] for [collectionId].
     */
    fun getCollectionFlow(pachliAccountId: Long, collectionId: String): Flow<CollectionWithAccounts> {
        return collectionsDao.getCollectionFlow(pachliAccountId, collectionId)
            .mapNotNull {
                it.firstNotNullOfOrNull { (collectionAndOwner, accounts) ->
                    CollectionWithAccounts(
                        collection = collectionAndOwner.collection.asModel(),
                        owner = collectionAndOwner.ownerAccount?.asModel(),
                        accounts = accounts.asModel(),
                    )
                }
            }
    }

    suspend fun getCollectionCardViewData(pachliAccountId: Long, collectionIds: Collection<String>): List<CollectionCardViewData> {
        return collectionsDao.getCollectionCardViewData(pachliAccountId, collectionIds).map { it.asModel() }
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
                    collectionWithAccounts.collection.collectionId,
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
            collectionsDao.upsertTimelineCollections(
                listOf(collectionWithAccounts.asTimelineCollection().asEntity(pachliAccountId)),
            )
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
     * Removes [accountId] from the cached copy of [collectionId] in
     * [CollectionEntity][app.pachli.core.database.model.CollectionEntity] and
     * [TimelineCollectionEntity][app.pachli.core.database.model.TimelineCollectionEntity].
     */
    suspend fun removeAccountFromCollection(pachliAccountId: Long, collectionId: String, accountId: String) {
        transactionProvider {
            collectionsDao.removeAccountFromCollection(pachliAccountId, collectionId, accountId)

            // Rewrite the TimelineCollectionEntity, if it exists.
            collectionsDao.getTimelineCollection(pachliAccountId, collectionId)?.let { timelineCollectionEntity ->
                val indexToRemove = timelineCollectionEntity.items.indexOfFirst { it.accountId == accountId }
                if (indexToRemove == -1) return@transactionProvider

                val items = timelineCollectionEntity.items.toMutableList().apply { removeAt(indexToRemove) }
                val itemIconUrls = timelineCollectionEntity.itemIconUrls.toMutableList().apply { removeAt(indexToRemove) }

                val newEntity = timelineCollectionEntity.copy(
                    items = items,
                    itemIconUrls = itemIconUrls,
                )
                collectionsDao.upsertTimelineCollection(newEntity)
            }
        }
    }
}

/** Data source for [Collection] data fetched from the server. */
@Singleton
internal class CollectionsRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    /**
     * Returns the most recent version of [collectionId] from the server, or
     * the error that occurred.
     */
    suspend fun getCollection(pachliAccountId: Long, collectionId: String) = binding {
        mastodonApi.getCollectionWithAccounts(collectionId)
            .mapEither(
                { it.body.asModel() },
                { CollectionsRepository.Error.GetCollection(it) },
            )
            .bind()
    }

    suspend fun getCollections(pachliAccountId: Long, collectionIds: Collection<String>): List<Result<CollectionWithAccounts, CollectionsRepository.Error.GetCollection>> {
        // Note: There's no API call that can fetch multiple collections at once,
        // so make async fetches and wait for the result.
        return coroutineScope {
            val jobs = collectionIds.map {
                async { getCollection(pachliAccountId, it) }
            }
            return@coroutineScope jobs.awaitAll()
        }
    }

    suspend fun revokeFromCollection(pachliAccountId: Long, collectionId: String, accountId: String): ApiResult<Unit> {
        return mastodonApi.revokeItemInCollection(collectionId, accountId)
    }
}
