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

package app.pachli.core.data.repository.hashtags

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.HashtagsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.HashtagEntity
import app.pachli.core.database.model.asEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.Hashtag
import app.pachli.core.network.model.HttpHeaderLink
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for hashtags that caches information locally.
 *
 * - Methods that query data always return from the cache.
 * - Methods that update data update the remote server first, and cache
 * successful responses.
 * - Call [refreshFollowedHashtags] to update the local cache of followed
 * hashtags.
 */
@Singleton
internal class OfflineFirstHashtagsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val localDataSource: HashtagsLocalDataSource,
    private val remoteDataSource: HashtagsRemoteDataSource,
) : HashtagsRepository {
    override suspend fun refreshFollowedHashtags(pachliAccountId: Long): Result<List<Hashtag>, HashtagsError.Retrieve> = externalScope.async {
        remoteDataSource.getFollowedHashtags(pachliAccountId).map { it.asModel() }
            .onSuccess { localDataSource.replace(pachliAccountId, it.asEntity(pachliAccountId)) }
    }.await()

    override fun getFollowedHashtags(pachliAccountId: Long): Flow<List<Hashtag>> =
        localDataSource.getFollowedHashtags(pachliAccountId).map { it.asModel() }

    override suspend fun followHashtag(pachliAccountId: Long, hashtag: String): Result<Hashtag, HashtagsError.Follow> = externalScope.async {
        remoteDataSource.followHashtag(hashtag)
            .map { it.asModel() }
            .onSuccess { localDataSource.followHashtag(it.asEntity(pachliAccountId)) }
    }.await()

    override suspend fun unfollowHashtag(pachliAccountId: Long, hashtag: String): Result<Hashtag, HashtagsError.Unfollow> = externalScope.async {
        remoteDataSource.unfollowHashtag(hashtag)
            .map { it.asModel() }
            .onSuccess { localDataSource.unfollowHashtag(it.asEntity(pachliAccountId)) }
    }.await()
}

@Singleton
internal class HashtagsLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val hashtagsDao: HashtagsDao,
) {
    suspend fun replace(pachliAccountId: Long, hashtags: List<HashtagEntity>) {
        transactionProvider {
            hashtagsDao.deleteFollowedHashtagsForAccount(pachliAccountId)
            hashtagsDao.upsert(hashtags)
        }
    }

    fun getFollowedHashtags(pachliAccountId: Long) = hashtagsDao.followingFlowByAccount(pachliAccountId)

    suspend fun followHashtag(hashtag: HashtagEntity) = hashtagsDao.upsert(hashtag)

    suspend fun unfollowHashtag(hashtag: HashtagEntity) = hashtagsDao.deleteForAccount(hashtag.pachliAccountId, hashtag.name)
}

@Singleton
internal class HashtagsRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun getFollowedHashtags(pachliAccountId: Long): Result<List<app.pachli.core.network.model.HashTag>, HashtagsError.Retrieve> {
        var maxId: String? = null
        val hashtags = buildList {
            do {
                val response = mastodonApi.followedTags(maxId = maxId)
                    .getOrElse { return Err(HashtagsError.Retrieve(it)) }
                addAll(response.body)
                val links = HttpHeaderLink.parse(response.headers["Link"])
                val next = HttpHeaderLink.findByRelationType(links, "next")
                maxId = next?.uri?.getQueryParameter("max_id")
            } while (maxId != null)
        }

        return Ok(hashtags)
    }

    suspend fun followHashtag(hashtag: String) = mastodonApi.followTag(hashtag)
        .mapEither({ it.body }, { HashtagsError.Follow(it) })

    suspend fun unfollowHashtag(hashtag: String) = mastodonApi.unfollowTag(hashtag)
        .mapEither({ it.body }, { HashtagsError.Unfollow(it) })
}
