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

package app.pachli.core.domain.lists

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.data.repository.MastodonList
import app.pachli.core.network.model.UserListRepliesPolicy
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

class CreateListUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val accountManager: AccountManager,
    private val listsRepository: ListsRepository,
) {
    /**
     * Creates a new list on the server, updates the local account on success.
     *
     * @param title Title for the new list
     * @param exclusive True if the new list is exclusive
     * @return Details of the new list if created, or an error
     */
    suspend operator fun invoke(accountId: Long, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastodonList, ListsError.Create> {
        return externalScope.async {
            return@async listsRepository.createList(title, exclusive, repliesPolicy)
                .map {
                    MastodonList(
                        accountId,
                        it.id,
                        it.title,
                        it.repliesPolicy,
                        it.exclusive ?: false,
                    )
                }
                .onSuccess { accountManager.createList(it) }
        }.await()
    }
}
