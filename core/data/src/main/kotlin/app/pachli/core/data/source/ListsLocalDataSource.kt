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

package app.pachli.core.data.source

import app.pachli.core.database.dao.ListsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.MastodonListEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListsLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val listsDao: ListsDao,
) {
    suspend fun replace(pachliAccountId: Long, lists: List<MastodonListEntity>) {
        transactionProvider {
            listsDao.deleteAllForAccount(pachliAccountId)
            listsDao.upsert(lists)
        }
    }

    fun getLists(pachliAccountId: Long) = listsDao.flowByAccount(pachliAccountId)

    fun getAllLists() = listsDao.flowAll()

    suspend fun createList(list: MastodonListEntity) = listsDao.upsert(list)

    suspend fun updateList(list: MastodonListEntity) = listsDao.upsert(list)

    suspend fun deleteList(list: MastodonListEntity) = listsDao.deleteForAccount(list.accountId, list.listId)
}
