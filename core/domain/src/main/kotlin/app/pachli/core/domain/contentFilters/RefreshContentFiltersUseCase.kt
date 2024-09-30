/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.domain.contentFilters

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.data.repository.ContentFiltersError
import app.pachli.core.data.repository.ContentFiltersRepository
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

class RefreshContentFiltersUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val accountManager: AccountManager,
    private val contentFiltersRepository: ContentFiltersRepository,
) {
    suspend operator fun invoke(
        pachliAccountId: Long,
    ): Result<ContentFilters, ContentFiltersError> {
        return externalScope.async {
            val server = accountManager.getPachliAccount(pachliAccountId)!!.server
            return@async contentFiltersRepository.getContentFilters(server)
                .onSuccess { accountManager.refreshContentFilters(pachliAccountId, it) }
        }.await()
    }
}
