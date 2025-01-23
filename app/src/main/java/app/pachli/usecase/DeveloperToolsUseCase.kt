/*
 * Copyright 2023 Pachli Association
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

package app.pachli.usecase

import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import javax.inject.Inject

/**
 * Functionality that is only intended to be used by the "Developer Tools" menu when built
 * in debug mode.
 */
class DeveloperToolsUseCase @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
) {
    /**
     * Clear the home timeline cache.
     */
    suspend fun clearHomeTimelineCache(accountId: Long) {
        timelineDao.deleteAllStatusesForAccount(accountId)
    }

    /**
     * Delete first K statuses
     */
    suspend fun deleteFirstKStatuses(accountId: Long, k: Int) {
        transactionProvider {
            val ids = timelineDao.getMostRecentNStatusIds(accountId, k)
            timelineDao.deleteRange(accountId, ids.last(), ids.first())
        }
    }
}
