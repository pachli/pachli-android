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
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.dao.FollowingAccountDao
import app.pachli.core.database.model.FollowingAccountEntity
import app.pachli.core.model.ITimelineAccount
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
 * Sends a follow request for [accountId] to the server for [pachliAccountId].
 *
 * On success:
 *
 * - The following relationship is added to [AccountManager].
 */
@Singleton
class FollowAccountUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val followingAccountDao: FollowingAccountDao,
) {
    /**
     * @param pachliAccountId
     * @param accountId
     * @param showReblogs True if the boosts of posts made by the account
     * should be shown.
     * @param notify True if the user should be notified when this account
     * posts.
     */
    @OptIn(UseCaseOnly::class)
    suspend operator fun invoke(pachliAccountId: Long, account: ITimelineAccount, showReblogs: Boolean? = null, notify: Boolean? = null): Result<Relationship, ApiError> = externalScope.async {
        mastodonApi.followAccount(account.accountId, showReblogs, notify).map { it.body.asModel() }
            .onSuccess {
                followingAccountDao.upsert(FollowingAccountEntity(pachliAccountId, account.accountId, account.domain))
            }
    }.await()
}
