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
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteEvent
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
 * Mute [accountId].
 *
 * On success:
 *
 * - [MuteEvent] is dispatched.
*/
@Singleton
class MuteAccountUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
) {
    /**
     * @param pachliAccountId
     * @param accountId ID of the account to mute.
     */
    @OptIn(UseCaseOnly::class)
    suspend operator fun invoke(pachliAccountId: Long, accountId: String, notifications: Boolean? = null, duration: Int? = null): Result<Relationship, ApiError> = externalScope.async {
        mastodonApi.muteAccount(accountId, notifications, duration).map { it.body.asModel() }
            .onSuccess { eventHub.dispatch(MuteEvent(pachliAccountId, accountId)) }
    }.await()
}
