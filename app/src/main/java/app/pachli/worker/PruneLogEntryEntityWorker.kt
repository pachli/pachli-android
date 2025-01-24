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
import app.pachli.core.database.dao.LogEntryDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import timber.log.Timber

/** Prune the database cache of old statuses. */
@HiltWorker
class PruneLogEntryEntityWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val logEntryDao: LogEntryDao,
) : CoroutineWorker(appContext, workerParams) {
    val notification: Notification = createWorkerNotification(applicationContext, R.string.notification_prune_cache)

    override suspend fun doWork(): Result {
        return try {
            val now = Instant.now()
            val oldest = now.minusMillis(OLDEST_ENTRY.inWholeMilliseconds)
            logEntryDao.prune(oldest)
            Result.success()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.e(e, "error in PruneLogEntryEntityWorker.doWork")
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(NOTIFICATION_ID_PRUNE_CACHE, notification)

    companion object {
        private val OLDEST_ENTRY = 48.hours
        const val PERIODIC_WORK_TAG = "PruneLogEntryEntityWorker_periodic"
    }
}
