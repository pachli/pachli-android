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
import app.pachli.util.MEDIA_TEMP_PREFIX
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import timber.log.Timber

/** Prune old media that was cached for sharing */
@HiltWorker
class PruneCachedMediaWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    val notification: Notification = createWorkerNotification(applicationContext, R.string.notification_prune_cache)

    override suspend fun doWork(): Result {
        val mediaDirectory = appContext.getExternalFilesDir("Pachli")
        if (mediaDirectory == null) {
            Timber.d("Skipping prune, shared storage is not available")
            return Result.success()
        }

        if (!mediaDirectory.exists()) {
            Timber.d("Skipping prune, media directory does not exist: %s", mediaDirectory.absolutePath)
            return Result.success()
        }

        val now = Instant.now()
        val cutoffInMs = now.minusSeconds(OLDEST_ENTRY.inWholeSeconds).toEpochMilli()

        mediaDirectory.listFiles { file ->
            file.lastModified() < cutoffInMs && file.name.startsWith(MEDIA_TEMP_PREFIX)
        }?.forEach { file ->
            try {
                file.delete()
            } catch (se: SecurityException) {
                Timber.e(se, "Error removing %s", file.name)
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(NOTIFICATION_ID_PRUNE_CACHE, notification)

    companion object {
        private val OLDEST_ENTRY = 24.hours
        const val PERIODIC_WORK_TAG = "PruneCachedMediaWorker"
    }
}
