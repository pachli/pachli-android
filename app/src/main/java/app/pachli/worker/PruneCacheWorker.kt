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

package app.pachli.worker

import android.app.Notification
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.pachli.R
import app.pachli.components.notifications.NOTIFICATION_ID_PRUNE_CACHE
import app.pachli.components.notifications.createWorkerNotification
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.dao.TimelineDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/** Prune the database cache of old statuses. */
@HiltWorker
class PruneCacheWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val timelineDao: TimelineDao,
    private val accountManager: AccountManager,
) : CoroutineWorker(appContext, workerParams) {
    val notification: Notification = createWorkerNotification(applicationContext, R.string.notification_prune_cache)

    override suspend fun doWork(): Result {
        for (account in accountManager.accounts) {
            Timber.d("Pruning database using account ID: %d", account.id)
            timelineDao.cleanup(account.id)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(NOTIFICATION_ID_PRUNE_CACHE, notification)

    companion object {
        const val PERIODIC_WORK_TAG = "PruneCacheWorker_periodic"
    }
}
