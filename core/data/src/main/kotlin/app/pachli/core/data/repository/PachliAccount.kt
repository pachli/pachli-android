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

package app.pachli.core.data.repository

import app.pachli.core.data.model.InstanceInfo
import app.pachli.core.data.model.MastodonList
import app.pachli.core.data.model.Server
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.Announcement
import app.pachli.core.network.model.Emoji

/**
 * A single Pachli account with all the information associated with it.
 *
 * @param id Account's unique local database ID.
 * @param entity [AccountEntity] from the local database.
 * @param instanceInfo Details about the account's server's instance info.
 * @param lists Account's lists.
 * @param emojis Account's emojis.
 * @param server Details about the account's server.
 * @param contentFilters Account's content filters.
 * @param announcements Announcements from the account's server.
 */
// TODO: Still not sure if it's better to have one class that contains everything,
// or provide dedicated functions that return specific flows for the different
// things, parameterised by the account ID.
data class PachliAccount(
    val id: Long,
    // TODO: Should be a core.data type
    val entity: AccountEntity,
    val instanceInfo: InstanceInfo,
    val lists: List<MastodonList>,
    val emojis: List<Emoji>,
    val server: Server,
    val contentFilters: ContentFilters,
    val announcements: List<Announcement>,
) {
    companion object {
        fun make(
            account: app.pachli.core.database.model.PachliAccount,
        ): PachliAccount {
            return PachliAccount(
                id = account.account.id,
                entity = account.account,
                instanceInfo = InstanceInfo.from(account.instanceInfo),
                lists = account.lists.map { MastodonList.from(it) },
                emojis = account.emojis?.emojiList.orEmpty(),
                server = Server.from(account.server),
                contentFilters = ContentFilters(
                    version = account.contentFilters.version,
                    contentFilters = account.contentFilters.contentFilters,
                ),
                announcements = account.announcements.map { it.announcement },
            )
        }
    }
}
