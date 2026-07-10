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

package app.pachli.core.domain.accounts

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.FollowingAccountDao
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.model.Relationship
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.UseCaseOnly
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Block [accountId].
 *
 * On success:
 *
 * - [BlockEvent] is dispatched.
 */
@Singleton
class BlockAccountUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val followingAccountDao: FollowingAccountDao,
    private val eventHub: EventHub,
) {
    /**
     * @param pachliAccountId
     * @param accountId ID of the account to block.
     */
    @OptIn(UseCaseOnly::class)
    suspend operator fun invoke(pachliAccountId: Long, accountId: String): Result<Relationship, ApiError> = externalScope.async {
        mastodonApi.blockAccount(accountId).map { it.body.asModel() }
            .onSuccess {
                followingAccountDao.delete(pachliAccountId, accountId)
                eventHub.dispatch(BlockEvent(pachliAccountId, accountId))
            }
    }.await()
}
