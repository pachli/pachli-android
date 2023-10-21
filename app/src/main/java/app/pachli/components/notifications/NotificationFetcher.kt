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

package app.pachli.components.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.pachli.components.notifications.NotificationHelper.filterNotification
import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.string.isLessThan
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Marker
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fetch Mastodon notifications and show Android notifications, with summaries, for them.
 *
 * Should only be called by a worker thread.
 *
 * @see app.pachli.worker.NotificationWorker
 * @see <a href="https://developer.android.com/guide/background/persistent/threading/worker">Background worker</a>
 */
@WorkerThread
class NotificationFetcher @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context,
) {
    suspend fun fetchAndShow() {
        for (account in accountManager.getAllAccountsOrderedByActive()) {
            if (account.notificationsEnabled) {
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Create sorted list of new notifications
                    val notifications = fetchNewNotifications(account)
                        .filter { filterNotification(notificationManager, account, it) }
                        .sortedWith(compareBy({ it.id.length }, { it.id })) // oldest notifications first
                        .toMutableList()

                    // There's a maximum limit on the number of notifications an Android app
                    // can display. If the total number of notifications (current notifications,
                    // plus new ones) exceeds this then some newer notifications will be dropped.
                    //
                    // Err on the side of removing *older* notifications to make room for newer
                    // notifications.
                    val currentAndroidNotifications = notificationManager.activeNotifications
                        .sortedWith(compareBy({ it.tag.length }, { it.tag })) // oldest notifications first

                    // Check to see if any notifications need to be removed
                    val toRemove = currentAndroidNotifications.size + notifications.size - MAX_NOTIFICATIONS
                    if (toRemove > 0) {
                        // Prefer to cancel old notifications first
                        currentAndroidNotifications.subList(0, min(toRemove, currentAndroidNotifications.size))
                            .forEach { notificationManager.cancel(it.tag, it.id) }

                        // Still got notifications to remove? Trim the list of new notifications,
                        // starting with the oldest.
                        while (notifications.size > MAX_NOTIFICATIONS) {
                            notifications.removeAt(0)
                        }
                    }

                    // Make and send the new notifications
                    // TODO: Use the batch notification API available in NotificationManagerCompat
                    // 1.11 and up (https://developer.android.com/jetpack/androidx/releases/core#1.11.0-alpha01)
                    // when it is released.
                    notifications.forEachIndexed { index, notification ->
                        val androidNotification = NotificationHelper.make(
                            context,
                            notificationManager,
                            notification,
                            account,
                            index == 0,
                        )
                        notificationManager.notify(notification.id, account.id.toInt(), androidNotification)
                        // Android will rate limit / drop notifications if they're posted too
                        // quickly. There is no indication to the user that this happened.
                        // See https://github.com/tuskyapp/Tusky/pull/3626#discussion_r1192963664
                        delay(1000.milliseconds)
                    }

                    NotificationHelper.updateSummaryNotifications(
                        context,
                        notificationManager,
                        account,
                    )

                    accountManager.saveAccount(account)
                } catch (e: Exception) {
                    Log.e(TAG, "Error while fetching notifications", e)
                }
            }
        }
    }

    /**
     * Fetch new Mastodon Notifications and update the marker position.
     *
     * Here, "new" means "notifications with IDs newer than notifications the user has already
     * seen."
     *
     * The "water mark" for Mastodon Notification IDs are stored in three places.
     *
     * - acccount.lastNotificationId -- the ID of the top-most notification when the user last
     *   left the Notifications tab.
     * - The Mastodon "marker" API -- the ID of the most recent notification fetched here.
     * - account.notificationMarkerId -- local version of the value from the Mastodon marker
     *   API, in case the Mastodon server does not implement that API.
     *
     * The user may have refreshed the "Notifications" tab and seen notifications newer than the
     * ones that were last fetched here. So `lastNotificationId` takes precedence if it is greater
     * than the marker.
     */
    private suspend fun fetchNewNotifications(account: AccountEntity): List<Notification> {
        val authHeader = String.format("Bearer %s", account.accessToken)

        // Figure out where to read from. Choose the most recent notification ID from:
        //
        // - The Mastodon marker API (if the server supports it)
        // - account.notificationMarkerId
        // - account.lastNotificationId
        Log.d(TAG, "getting notification marker for ${account.fullName}")
        val remoteMarkerId = fetchMarker(authHeader, account)?.lastReadId ?: "0"
        val localMarkerId = account.notificationMarkerId
        val markerId = if (remoteMarkerId.isLessThan(localMarkerId)) localMarkerId else remoteMarkerId
        val readingPosition = account.lastNotificationId

        var minId: String? = if (readingPosition.isLessThan(markerId)) markerId else readingPosition
        Log.d(TAG, "  remoteMarkerId: $remoteMarkerId")
        Log.d(TAG, "  localMarkerId: $localMarkerId")
        Log.d(TAG, "  readingPosition: $readingPosition")

        Log.d(TAG, "getting Notifications for ${account.fullName}, min_id: $minId")

        // Fetch all outstanding notifications
        val notifications = buildList {
            while (minId != null) {
                val response = mastodonApi.notificationsWithAuth(
                    authHeader,
                    account.domain,
                    minId = minId,
                )
                if (!response.isSuccessful) break

                // Notifications are returned in the page in order, newest first,
                // (https://github.com/mastodon/documentation/issues/1226), insert the
                // new page at the head of the list.
                response.body()?.let { addAll(0, it) }

                // Get the previous page, which will be chronologically newer
                // notifications. If it doesn't exist this is null and the loop
                // will exit.
                val links = Links.from(response.headers()["link"])
                minId = links.prev
            }
        }

        // Save the newest notification ID in the marker.
        notifications.firstOrNull()?.let {
            val newMarkerId = notifications.first().id
            Log.d(TAG, "updating notification marker for ${account.fullName} to: $newMarkerId")
            mastodonApi.updateMarkersWithAuth(
                auth = authHeader,
                domain = account.domain,
                notificationsLastReadId = newMarkerId,
            )
            account.notificationMarkerId = newMarkerId
            accountManager.saveAccount(account)
        }

        return notifications
    }

    private suspend fun fetchMarker(authHeader: String, account: AccountEntity): Marker? {
        return try {
            val allMarkers = mastodonApi.markersWithAuth(
                authHeader,
                account.domain,
                listOf("notifications"),
            )
            val notificationMarker = allMarkers["notifications"]
            Log.d(TAG, "Fetched marker for ${account.fullName}: $notificationMarker")
            notificationMarker
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch marker", e)
            null
        }
    }

    companion object {
        private const val TAG = "NotificationFetcher"

        // There's a system limit on the maximum number of notifications an app
        // can show, NotificationManagerService.MAX_PACKAGE_NOTIFICATIONS. Unfortunately
        // that's not available to client code or via the NotificationManager API.
        // The current value in the Android source code is 50, set 40 here to both
        // be conservative, and allow some headroom for summary notifications.
        private const val MAX_NOTIFICATIONS = 40
    }
}
