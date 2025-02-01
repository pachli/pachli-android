/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.data.repository.notifications

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.data.repository.notifications.NotificationsRepository.Companion.RKE_TIMELINE_ID
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.model.Report
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

@OptIn(ExperimentalPagingApi::class)
class NotificationsRemoteMediator(
    private val pachliAccountId: Long,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val notificationDao: NotificationDao,
    private val statusDao: StatusDao,
) : RemoteMediator<Int, NotificationData>() {
    override suspend fun load(loadType: LoadType, state: PagingState<Int, NotificationData>): MediatorResult {
        return try {
            transactionProvider {
                val response = when (loadType) {
                    LoadType.REFRESH -> {
                        // Ignore the provided state, always try and fetch from the remote
                        // REFRESH key.
                        val notificationId = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.REFRESH,
                        )?.key
                        getInitialPage(notificationId, state.config.pageSize)
                    }

                    LoadType.PREPEND -> {
                        val rke = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.PREV,
                        ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                        mastodonApi.notifications(minId = rke.key, limit = state.config.pageSize)
                    }

                    LoadType.APPEND -> {
                        val rke = remoteKeyDao.remoteKeyForKind(
                            pachliAccountId,
                            RKE_TIMELINE_ID,
                            RemoteKeyKind.NEXT,
                        ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                        mastodonApi.notifications(maxId = rke.key, limit = state.config.pageSize)
                    }
                }

                val notifications = response.body()
                if (!response.isSuccessful || notifications == null) {
                    return@transactionProvider MediatorResult.Error(HttpException(response))
                }

                if (notifications.isEmpty()) {
                    return@transactionProvider MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
                }

                val links = Links.from(response.headers()["link"])

                when (loadType) {
                    LoadType.REFRESH -> {
                        remoteKeyDao.deletePrevNext(pachliAccountId, RKE_TIMELINE_ID)
                        notificationDao.deleteAllNotificationsForAccount(pachliAccountId)

                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                links.next,
                            ),
                        )

                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.PREV,
                                links.prev,
                            ),
                        )
                    }

                    LoadType.PREPEND -> links.prev?.let {
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.PREV,
                                it,
                            ),
                        )
                    }

                    LoadType.APPEND -> links.next?.let {
                        remoteKeyDao.upsert(
                            RemoteKeyEntity(
                                pachliAccountId,
                                RKE_TIMELINE_ID,
                                RemoteKeyKind.NEXT,
                                it,
                            ),
                        )
                    }
                }

                upsertNotifications(pachliAccountId, notifications)

                MediatorResult.Success(endOfPaginationReached = false)
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.e(e, "error loading, loadtype = %s", loadType)
            MediatorResult.Error(e)
        }
    }

    /**
     * Fetch the initial page of notifications, using [notificationId] as the ID of the initial
     * notification to fetch.
     *
     * - If there is no key, a page of the most recent notifications is returned.
     * - If the notifications exists a page that contains the notification, and the notifications
     * immediately before and after it are returned. This provides enough content that the list
     * adapter can restore the user's reading position.
     * - If the notification does not exist the page of notifications immediately before is returned (if
     *   non-empty).
     * - If there is no page of notifications immediately before then the page immediately after is
     *   returned (if non-empty).
     * - Finally, fall back to the most recent notifications.
     */
    private suspend fun getInitialPage(notificationId: String?, pageSize: Int): Response<List<Notification>> =
        coroutineScope {
            // If the key is null this is straightforward, just return the most recent statuses.
            notificationId ?: return@coroutineScope mastodonApi.notifications(limit = pageSize)

            // It's important to return *something* from this state. If an empty page is returned
            // (even with next/prev links) Pager3 assumes there is no more data to load and stops.
            //
            // In addition, the Mastodon API does not let you fetch a page that contains a given key.
            // You can fetch the page immediately before the key, or the page immediately after, but
            // you can not fetch the page itself.

            // Fetch the requested notification, and the page immediately before (prev) and after (next)
            val deferredNotification = async { mastodonApi.notification(id = notificationId) }
            val deferredPrevPage = async {
                mastodonApi.notifications(minId = notificationId, limit = pageSize * 3)
            }
            val deferredNextPage = async {
                mastodonApi.notifications(maxId = notificationId, limit = pageSize * 3)
            }

            deferredNotification.await().getOrNull()?.let { notification ->
                val notifications = buildList {
                    deferredPrevPage.await().body()?.let { this.addAll(it) }
                    this.add(notification)
                    deferredNextPage.await().body()?.let { this.addAll(it) }
                }

                // "notifications" now contains at least one notification we can return, and
                // hopefully a full page.

                // Build correct max_id and min_id links for the response. The "min_id" to use
                // when fetching the next page is the same as "key". The "max_id" is the ID of
                // the oldest notification in the list.
                val minId = notifications.first().id
                val maxId = notifications.last().id
                val headers = Headers.Builder()
                    .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$minId>; rel=\"prev\"")
                    .build()

                return@coroutineScope Response.success(notifications, headers)
            }

            // The user's last read notification was missing. Use the page of notifications
            // chronologically older than their desired notification. This page must *not* be
            // empty (as noted earlier, if it is, paging stops).
            deferredNextPage.await().apply {
                if (isSuccessful && !body().isNullOrEmpty()) {
                    return@coroutineScope this
                }
            }

            // There were no notifications older than the user's desired notification. Return the page
            // of notifications immediately newer than their desired notification. This page must
            // *not* be empty (as noted earlier, if it is, paging stops).
            deferredPrevPage.await().apply {
                if (isSuccessful && !body().isNullOrEmpty()) {
                    return@coroutineScope this
                }
            }

            // Everything failed -- fallback to fetching the most recent notifications
            return@coroutineScope mastodonApi.notifications(limit = pageSize)
        }

    /**
     * Upserts [notifications] and related data in to the local database.
     *
     * Must be called inside an existing database transaction.
     *
     * @param pachliAccountId
     * @param notifications Notifications to upsert.
     */
    private suspend fun upsertNotifications(pachliAccountId: Long, notifications: List<Notification>) {
        check(transactionProvider.inTransaction())

        /** Unique accounts referenced in this batch of notifications. */
        val accounts = mutableSetOf<TimelineAccount>()

        /** Unique statuses referenced in this batch of notifications. */
        val statuses = mutableSetOf<Status>()

        /** Unique reports referenced in this batch of notifications. */
        val reports = mutableSetOf<Notification>()

        /** Unique relationship severance events referenced in this batch of notifications. */
        val severanceEvents = mutableSetOf<Notification>()

        // Collect the different items from this batch of notifications.
        notifications.forEach { notification ->
            accounts.add(notification.account)

            notification.status?.let { status ->
                accounts.add(status.account)
                status.reblog?.account?.let { accounts.add(it) }
                statuses.add(status)
            }

            notification.report?.let { reports.add(notification) }
            notification.relationshipSeveranceEvent?.let { severanceEvents.add(notification) }
        }

        // Bulk upsert the discovered items.
        timelineDao.upsertAccounts(accounts.map { TimelineAccountEntity.from(it, pachliAccountId) })
        statusDao.upsertStatuses(statuses.map { StatusEntity.from(it, pachliAccountId) })
        notificationDao.upsertReports(reports.mapNotNull { NotificationReportEntity.from(pachliAccountId, it) })
        notificationDao.upsertEvents(
            severanceEvents.mapNotNull {
                NotificationRelationshipSeveranceEventEntity.from(pachliAccountId, it)
            },
        )
        notificationDao.upsertNotifications(
            notifications.map { NotificationEntity.from(pachliAccountId, it) },
        )
    }
}

/**
 * @return A [NotificationData] from a network [Notification] for [pachliAccountId].
 */
fun NotificationData.Companion.from(pachliAccountId: Long, notification: Notification) = NotificationData(
    notification = NotificationEntity.from(pachliAccountId, notification),
    account = TimelineAccountEntity.from(notification.account, pachliAccountId),
    status = notification.status?.let { status ->
        TimelineStatusWithAccount(
            status = StatusEntity.from(status, pachliAccountId),
            account = TimelineAccountEntity.from(status.account, pachliAccountId),
        )
    },
    viewData = null,
    report = NotificationReportEntity.from(pachliAccountId, notification),
    relationshipSeveranceEvent = NotificationRelationshipSeveranceEventEntity.from(pachliAccountId, notification),
)

/**
 * @return A [NotificationEntity] from a network [Notification] for [pachliAccountId].
 */
fun NotificationEntity.Companion.from(pachliAccountId: Long, notification: Notification) = NotificationEntity(
    pachliAccountId = pachliAccountId,
    serverId = notification.id,
    type = NotificationEntity.Type.from(notification.type),
    createdAt = notification.createdAt.toInstant(),
    accountServerId = notification.account.id,
    statusServerId = notification.status?.id,
)

/**
 * @return A [NotificationReportEntity] from a network [Notification] for [pachliAccountId].
 */
fun NotificationReportEntity.Companion.from(
    pachliAccountId: Long,
    notification: Notification,
): NotificationReportEntity? {
    val report = notification.report ?: return null

    return NotificationReportEntity(
        pachliAccountId = pachliAccountId,
        serverId = notification.id,
        reportId = report.id,
        actionTaken = report.actionTaken,
        actionTakenAt = report.actionTakenAt,
        category = when (report.category) {
            Report.Category.SPAM -> NotificationReportEntity.Category.SPAM
            Report.Category.VIOLATION -> NotificationReportEntity.Category.VIOLATION
            Report.Category.OTHER -> NotificationReportEntity.Category.OTHER
        },
        comment = report.comment,
        forwarded = report.forwarded,
        createdAt = report.createdAt,
        statusIds = report.statusIds,
        ruleIds = report.ruleIds,
        targetAccount = TimelineAccountEntity.from(report.targetAccount, pachliAccountId),
    )
}

/**
 * @return A [NotificationRelationshipSeveranceEventEntity] from a network [Notification]
 * for [pachliAccountId].
 */
fun NotificationRelationshipSeveranceEventEntity.Companion.from(
    pachliAccountId: Long,
    notification: Notification,
): NotificationRelationshipSeveranceEventEntity? {
    val rse = notification.relationshipSeveranceEvent ?: return null

    return NotificationRelationshipSeveranceEventEntity(
        pachliAccountId = pachliAccountId,
        serverId = notification.id,
        eventId = rse.id,
        type = when (rse.type) {
            RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> NotificationRelationshipSeveranceEventEntity.Type.DOMAIN_BLOCK
            RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> NotificationRelationshipSeveranceEventEntity.Type.USER_DOMAIN_BLOCK
            RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> NotificationRelationshipSeveranceEventEntity.Type.ACCOUNT_SUSPENSION
            RelationshipSeveranceEvent.Type.UNKNOWN -> NotificationRelationshipSeveranceEventEntity.Type.UNKNOWN
        },
        purged = rse.purged,
        followersCount = rse.followersCount,
        followingCount = rse.followingCount,
        createdAt = rse.createdAt,
    )
}
