/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.testing.extensions

import app.pachli.core.database.AppDatabase
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount

/**
 * Inserts [statuses] in to the database, populating the correct
 * [TimelineAccountEntity][app.pachli.core.database.model.TimelineAccountEntity],
 * [StatusEntity][app.pachli.core.database.model.StatusEntity],
 * [TimelineStatusEntity][app.pachli.core.database.model.TimelineStatusEntity]
 * tables to ensure tests don't fail because of foreign key constraint
 * violations.
 */
suspend fun AppDatabase.insertStatuses(statuses: Iterable<TimelineStatusWithAccount>) {
    statuses.forEach { statusWithAccount ->
        statusWithAccount.account.let { account ->
            timelineDao().insertAccount(account)
        }
        statusWithAccount.reblogAccount?.let { account ->
            timelineDao().insertAccount(account)
        }
        statusDao().insertStatus(statusWithAccount.status)
    }
    timelineDao().upsertStatuses(
        statuses.map {
            TimelineStatusEntity(
                pachliAccountId = it.status.timelineUserId,
                kind = TimelineStatusEntity.Kind.Home,
                statusId = it.status.serverId,
            )
        },
    )
}
