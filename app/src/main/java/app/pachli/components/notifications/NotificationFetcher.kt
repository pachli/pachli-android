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
import androidx.annotation.WorkerThread
import app.pachli.core.common.string.isLessThan
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.notifications.NotificationsRepository
import app.pachli.core.data.repository.notifications.from
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.NotificationData
import app.pachli.core.domain.notifications.NotificationConfig
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Marker
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.worker.NotificationWorker
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import timber.log.Timber

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
    private val notificationsRepository: NotificationsRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun fetchAndShow(pachliAccountId: Long) {
        Timber.d("NotificationFetcher.fetchAndShow(%d) started", pachliAccountId)

        val pachliAccounts = buildList {
            if (pachliAccountId == NotificationWorker.ALL_ACCOUNTS) {
                addAll(accountManager.pachliAccountsFlow.take(1).first())
            } else {
                accountManager.getPachliAccountFlow(pachliAccountId).take(1).first()?.let { add(it) }
            }
        }

        for (pachliAccount in pachliAccounts) {
            val entity = pachliAccount.entity
            Timber.d(
                "Checking %s, notificationsEnabled = %s",
                entity.fullName,
                entity.notificationsEnabled,
            )
            if (entity.notificationsEnabled) {
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Create sorted list of new notifications
                    val notifications = fetchNewNotifications(entity)
                        .filter { filterNotification(notificationManager, entity, it.type.asModel()) }
                        .filter {
                            val decision = filterNotificationByAccount(pachliAccount, NotificationData.from(pachliAccountId, it))
                            decision is AccountFilterDecision.None
                        }
                        .sortedWith(compareBy({ it.id.length }, { it.id })) // oldest notifications first
                        .toMutableList()

                    // There's a maximum limit on the number of notifications an Android app
                    // can display. If the total number of notifications (current notifications,
                    // plus new ones) exceeds this then some newer notifications will be dropped.
                    //
                    // Err on the side of removing *older* notifications to make room for newer
                    // notifications.
                    val currentAndroidNotifications = notificationManager.activeNotifications
                        .filter { it.tag != null }
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
                        val androidNotification = makeNotification(
                            context,
                            notificationManager,
                            notification.asModel(),
                            entity,
                            index == 0,
                        )
                        notificationManager.notify(notification.id, entity.id.toInt(), androidNotification)
                        // Android will rate limit / drop notifications if they're posted too
                        // quickly. There is no indication to the user that this happened.
                        // See https://github.com/tuskyapp/Tusky/pull/3626#discussion_r1192963664
                        delay(1000.milliseconds)
                    }

                    updateSummaryNotifications(
                        context,
                        notificationManager,
                        entity,
                    )
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Timber.e(e, "Error while fetching notifications")
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
     * - Notification refresh key -- the ID of the top-most notification when the user last
     *   left the Notifications tab.
     * - The Mastodon "marker" API -- the ID of the most recent notification fetched here.
     * - account.notificationMarkerId -- local version of the value from the Mastodon marker
     *   API, in case the Mastodon server does not implement that API.
     *
     * The user may have refreshed the "Notifications" tab and seen notifications newer than the
     * ones that were last fetched here. So the refresh key takes precedence if it is greater
     * than the marker.
     */
    private suspend fun fetchNewNotifications(account: AccountEntity): List<Notification> {
        Timber.d("fetchNewNotifications(%s)", account.fullName)

        // Figure out which water mark to use.
        Timber.d("getting notification marker for %s", account.fullName)
        val remoteMarkerId = fetchMarker(account)?.lastReadId ?: "0"
        val localMarkerId = account.notificationMarkerId
        val markerId = if (remoteMarkerId.isLessThan(localMarkerId)) localMarkerId else remoteMarkerId
        val readingPosition = notificationsRepository.getRefreshKey(account.id) ?: "0"

        var minId: String? = if (readingPosition.isLessThan(markerId)) markerId else readingPosition
        Timber.d("  remoteMarkerId: %s", remoteMarkerId)
        Timber.d("  localMarkerId: %s", localMarkerId)
        Timber.d("  readingPosition: %s", readingPosition)

        Timber.d("getting Notifications for %s, min_id: %s", account.fullName, minId)

        // Fetch all outstanding notifications
        val notifications = buildList {
            while (minId != null) {
                val now = Instant.now()
                Timber.d("Fetching notifications from server")
                mastodonApi.notificationsWithAuth(
                    account.authHeader,
                    account.domain,
                    minId = minId,
                ).onSuccess { response ->
                    val notifications = response.body
                    NotificationConfig.lastFetchNewNotifications[account.fullName] = Pair(now, Ok(Unit))
                    Timber.i(
                        "Fetching notifications from server succeeded, returned %d notifications",
                        notifications.size,
                    )

                    // Notifications are returned in the page in order, newest first,
                    // (https://github.com/mastodon/documentation/issues/1226), insert the
                    // new page at the head of the list.
                    addAll(0, notifications)

                    // Get the previous page, which will be chronologically newer
                    // notifications. If it doesn't exist this is null and the loop
                    // will exit.
                    val links = Links.from(response.headers["link"])
                    minId = links.prev
                }
                    .onFailure {
                        val error = it.fmt(context)
                        Timber.e("Fetching notifications from server failed: %s", error)
                        NotificationConfig.lastFetchNewNotifications[account.fullName] = Pair(now, Err(error))
                        return@buildList
                    }
            }
        }

        // Save the newest notification ID in the marker.
        notifications.firstOrNull()?.let {
            val newMarkerId = notifications.first().id
            Timber.d("updating notification marker for %s to: %s", account.fullName, newMarkerId)
            mastodonApi.updateMarkersWithAuth(
                auth = account.authHeader,
                domain = account.domain,
                notificationsLastReadId = newMarkerId,
            )
            accountManager.setNotificationMarkerId(account.id, newMarkerId)
            Timber.d("Updated notification marker for %s to: %s", account.fullName, newMarkerId)
        }

        return notifications
    }

    private suspend fun fetchMarker(account: AccountEntity): Marker? {
        return mastodonApi.markersWithAuth(
            account.authHeader,
            account.domain,
            listOf("notifications"),
        ).mapBoth(
            {
                val notificationMarker = it.body["notifications"]
                Timber.d("Fetched marker for %s: %s", account.fullName, notificationMarker)
                notificationMarker
            },
            {
                Timber.e("Failed to fetch marker: %s", it)
                null
            },
        )
    }

    companion object {
        // There's a system limit on the maximum number of notifications an app
        // can show, NotificationManagerService.MAX_PACKAGE_NOTIFICATIONS. Unfortunately
        // that's not available to client code or via the NotificationManager API.
        // The current value in the Android source code is 50, set 40 here to both
        // be conservative, and allow some headroom for summary notifications.
        private const val MAX_NOTIFICATIONS = 40
    }
}
