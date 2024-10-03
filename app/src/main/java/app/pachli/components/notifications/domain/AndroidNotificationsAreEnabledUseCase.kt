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

package app.pachli.components.notifications.domain

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.NotificationConfig
import java.util.Locale
import javax.inject.Inject
import timber.log.Timber

/**
 * @return True if at least one account has Android notifications enabled.
 */
class AndroidNotificationsAreEnabledUseCase @Inject constructor(
    val accountManager: AccountManager,
) {
    operator fun invoke(context: Context): Boolean {
        Timber.Forest.d("Checking if Android notifications are enabled")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.Forest.d(
                String.Companion.format(
                    Locale.US,
                    "%d >= %d, checking notification manager",
                    Build.VERSION.SDK_INT,
                    Build.VERSION_CODES.O,
                ),
            )
            // on Android >= O notifications are enabled if at least one channel is enabled
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.areNotificationsEnabled()) {
                for (channel in notificationManager.notificationChannels) {
                    Timber.Forest.d(
                        "Checking NotificationChannel %s / importance: %s",
                        channel.id,
                        channel.importance,
                    )
                    if (channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE) {
                        Timber.Forest.d("NotificationsEnabled")
                        Timber.Forest.d("Channel notification importance > %d, enabling notifications", NotificationManager.IMPORTANCE_NONE)
                        NotificationConfig.androidNotificationsEnabled = true
                        return true
                    } else {
                        Timber.Forest.d("Channel notification importance <= %d, skipping", NotificationManager.IMPORTANCE_NONE)
                    }
                }
            }
            Timber.Forest.i("Notifications disabled because no notification channels are enabled")
            NotificationConfig.androidNotificationsEnabled = false
            false
        } else {
            // on Android < O notifications are enabled if at least one account has notification enabled
            Timber.Forest.d(
                "%d < %d, checking account manager",
                Build.VERSION.SDK_INT,
                Build.VERSION_CODES.O,
            )
            val result = accountManager.areAndroidNotificationsEnabled()
            Timber.Forest.d("Did any accounts have notifications enabled?: %s", result)
            NotificationConfig.androidNotificationsEnabled = result
            return result
        }
    }
}
