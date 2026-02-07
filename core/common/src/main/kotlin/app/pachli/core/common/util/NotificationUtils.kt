/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.common.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import app.pachli.core.common.R

private const val CHANNEL_BACKGROUND_TASKS = "CHANNEL_BACKGROUND_TASKS"

/**
 * Creates a notification channel for notifications for background work that should not
 * disturb the user.
 *
 * @param context context
 */
fun createWorkerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHANNEL_BACKGROUND_TASKS,
        context.getString(R.string.notification_listenable_worker_name),
        NotificationManager.IMPORTANCE_NONE,
    )
    channel.description = context.getString(R.string.notification_listenable_worker_description)
    channel.enableLights(false)
    channel.enableVibration(false)
    channel.setShowBadge(false)
    notificationManager.createNotificationChannel(channel)
}

/**
 * Creates a notification for a background worker.
 *
 * @param context context
 * @param titleResource String resource to use as the notification's title
 * @return the notification
 */
fun createWorkerNotification(
    context: Context,
    @StringRes titleResource: Int,
): android.app.Notification {
    val title = context.getString(titleResource)
    return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_TASKS)
        .setContentTitle(title)
        .setTicker(title)
        .setSmallIcon(R.drawable.ic_notify)
        .setOngoing(true)
        .build()
}
