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
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.pachli.R
import app.pachli.components.notifications.NOTIFICATION_ID_FETCH_NOTIFICATION
import app.pachli.components.notifications.NotificationFetcher
import app.pachli.components.notifications.createWorkerNotification
import javax.inject.Inject

/** Fetch and show new notifications. */
class NotificationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val notificationsFetcher: NotificationFetcher,
) : CoroutineWorker(appContext, params) {
    val notification: Notification = createWorkerNotification(applicationContext, R.string.notification_notification_worker)

    override suspend fun doWork(): Result {
        notificationsFetcher.fetchAndShow()
        return Result.success()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(NOTIFICATION_ID_FETCH_NOTIFICATION, notification)

    class Factory @Inject constructor(
        private val notificationsFetcher: NotificationFetcher,
    ) : ChildWorkerFactory {
        override fun createWorker(appContext: Context, params: WorkerParameters): CoroutineWorker {
            return NotificationWorker(appContext, params, notificationsFetcher)
        }
    }
}
