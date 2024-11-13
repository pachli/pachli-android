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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.pachli.R
import app.pachli.components.notifications.NOTIFICATION_ID_FETCH_NOTIFICATION
import app.pachli.components.notifications.NotificationFetcher
import app.pachli.components.notifications.createWorkerNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/** Fetch and show new notifications. */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notificationsFetcher: NotificationFetcher,
) : CoroutineWorker(appContext, params) {
    val notification: Notification = createWorkerNotification(applicationContext, R.string.notification_notification_worker)

    override suspend fun doWork(): Result {
        Timber.d("NotificationWorker.doWork() started")
        val accountId = inputData.getAccountId()

        notificationsFetcher.fetchAndShow(accountId)
        return Result.success()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(NOTIFICATION_ID_FETCH_NOTIFICATION, notification)

    companion object {
        private const val ACCOUNT_ID = "accountId"

        /** Notifications for all accounts should be fetched. */
        const val ALL_ACCOUNTS = -1L

        fun data(accountId: Long) = Data.Builder().putLong(ACCOUNT_ID, accountId).build()

        fun Data.getAccountId() = getLong(ACCOUNT_ID, ALL_ACCOUNTS)
    }
}
