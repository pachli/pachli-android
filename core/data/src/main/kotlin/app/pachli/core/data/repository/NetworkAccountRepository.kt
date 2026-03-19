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
import app.pachli.core.model.Account
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Repository for account data, data is always fetched from the user's server.
 */
class NetworkAccountRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val api: MastodonApi,
) : AccountRepository {
    override suspend fun getAccount(accountId: String): Result<Account, AccountError.GetAccountError> = binding {
        api.account(accountId)
            .map { it.body.asModel() }
            .mapError { AccountError.GetAccountError(it) }
            .bind()
    }
}
