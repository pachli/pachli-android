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

class UpdateListUseCase @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val accountManager: AccountManager,
    private val listsRepository: ListsRepository,
) {
    /**
     * Update's [accountId]'s list with [listId], setting the [title], [exclusive],
     * and [repliesPolicy]. Updates the local information on success.
     */
    suspend operator fun invoke(accountId: Long, listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy): Result<MastodonList, ListsError.Update> {
        return externalScope.async {
            return@async listsRepository.editList(listId, title, exclusive, repliesPolicy)
                .map {
                    MastodonList(
                        accountId,
                        it.id,
                        it.title,
                        it.repliesPolicy,
                        it.exclusive ?: false,
                    )
                }
                .onSuccess { accountManager.editList(it) }
        }.await()
    }
}
