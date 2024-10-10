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

package app.pachli.core.data.model

import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.UserListRepliesPolicy

data class MastodonList(
    val accountId: Long,
    val listId: String,
    val title: String,
    val repliesPolicy: UserListRepliesPolicy,
    val exclusive: Boolean,
) {
    fun entity() = MastodonListEntity(
        accountId = accountId,
        listId = listId,
        title = title,
        repliesPolicy = repliesPolicy,
        exclusive = exclusive,
    )
    companion object {
        fun from(entity: MastodonListEntity) = MastodonList(
            accountId = entity.accountId,
            listId = entity.listId,
            title = entity.title,
            repliesPolicy = entity.repliesPolicy,
            exclusive = entity.exclusive,
        )

        fun from(entities: List<MastodonListEntity>) = entities.map { from(it) }

        fun make(pachliAccountId: Long, networkList: MastoList) = MastodonList(
            accountId = pachliAccountId,
            listId = networkList.id,
            title = networkList.title,
            repliesPolicy = networkList.repliesPolicy,
            exclusive = networkList.exclusive ?: false,
        )

        fun make(pachliAccountId: Long, networkLists: List<MastoList>) =
            networkLists.map { make(pachliAccountId, it) }
    }
}
