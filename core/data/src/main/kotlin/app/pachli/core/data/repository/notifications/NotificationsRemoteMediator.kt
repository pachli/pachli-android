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
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.NotificationAccountWarningEntity
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TSQ
import app.pachli.core.database.model.asEntity
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.TimelineAccount
import app.pachli.core.network.model.AccountWarning
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.model.Report
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers

/**
 * @property excludeTypes 0 or more [Notification.Type] that should not be fetched.
 */
@OptIn(ExperimentalPagingApi::class)
class NotificationsRemoteMediator(
    private val pachliAccountId: Long,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val notificationDao: NotificationDao,
    private val statusDao: StatusDao,
    private val excludeTypes: Iterable<Notification.Type>,
) : RemoteMediator<Int, NotificationData>() {
    private val remoteKeyTimelineId = Timeline.Notifications.remoteKeyTimelineId

    /**
     * Set of notification types that **must** have a non-null status. Some servers
     * break this contract, and notifications from those servers must be filtered
     * out.
     *
     * See https://github.com/tuskyapp/Tusky/issues/2252.
     */
    private val notificationTypesWithStatus = setOf(
        Notification.Type.FAVOURITE,
        Notification.Type.REBLOG,
        Notification.Type.STATUS,
        Notification.Type.MENTION,
        Notification.Type.POLL,
        Notification.Type.UPDATE,
    )

    override suspend fun load(loadType: LoadType, state: PagingState<Int, NotificationData>): MediatorResult {
        return transactionProvider {
            val response = when (loadType) {
                LoadType.REFRESH -> {
                    // Ignore the provided state, always try and fetch from the remote
                    // REFRESH key.
                    val notificationId = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        remoteKeyTimelineId,
                        RemoteKeyKind.REFRESH,
                    )?.key
                    getInitialPage(notificationId, state.config.pageSize)
                }

                LoadType.PREPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        remoteKeyTimelineId,
                        RemoteKeyKind.PREV,
                    ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(minId = rke.key, limit = state.config.pageSize, excludes = excludeTypes)
                }

                LoadType.APPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        remoteKeyTimelineId,
                        RemoteKeyKind.NEXT,
                    ) ?: return@transactionProvider MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(maxId = rke.key, limit = state.config.pageSize, excludes = excludeTypes)
                }
            }.getOrElse { return@transactionProvider MediatorResult.Error(it.throwable) }

            val links = Links.from(response.headers["link"])

            when (loadType) {
                LoadType.REFRESH -> {
                    remoteKeyDao.deletePrevNext(pachliAccountId, remoteKeyTimelineId)
                    notificationDao.deleteAllNotificationsForAccount(pachliAccountId)

                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            pachliAccountId,
                            remoteKeyTimelineId,
                            RemoteKeyKind.NEXT,
                            links.next,
                        ),
                    )

                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            pachliAccountId,
                            remoteKeyTimelineId,
                            RemoteKeyKind.PREV,
                            links.prev,
                        ),
                    )
                }

                LoadType.PREPEND -> links.prev?.let {
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            pachliAccountId,
                            remoteKeyTimelineId,
                            RemoteKeyKind.PREV,
                            it,
                        ),
                    )
                }

                LoadType.APPEND -> links.next?.let {
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            pachliAccountId,
                            remoteKeyTimelineId,
                            RemoteKeyKind.NEXT,
                            it,
                        ),
                    )
                }
            }

            val notifications = response.body

            upsertNotifications(pachliAccountId, notifications)

            val endOfPagination = when (loadType) {
                LoadType.REFRESH -> notifications.isEmpty() || (links.prev == null && links.next == null)
                LoadType.PREPEND -> notifications.isEmpty() || links.prev == null
                LoadType.APPEND -> notifications.isEmpty() || links.next == null
            }

            MediatorResult.Success(endOfPaginationReached = endOfPagination)
        }
    }

    /**
     * @return The initial page of notifications centered on the notification with
     * [notificationId], or the most recent notifications if [notificationId] is null.
     */
    private suspend fun getInitialPage(notificationId: String?, pageSize: Int): ApiResult<List<Notification>> = coroutineScope {
        notificationId ?: return@coroutineScope mastodonApi.notifications(limit = pageSize, excludes = excludeTypes)

        val notification = async { mastodonApi.notification(id = notificationId) }
        val prevPage = async { mastodonApi.notifications(minId = notificationId, limit = pageSize * 3, excludes = excludeTypes) }
        val nextPage = async { mastodonApi.notifications(maxId = notificationId, limit = pageSize * 3, excludes = excludeTypes) }

        val notifications = buildList {
            prevPage.await().get()?.let { this.addAll(it.body) }
            notification.await().get()?.let {
                if (!excludeTypes.contains(it.body.type)) this.add(it.body)
            }
            nextPage.await().get()?.let { this.addAll(it.body) }
        }

        val minId = notifications.firstOrNull()?.id ?: notificationId
        val maxId = notifications.lastOrNull()?.id ?: notificationId

        val headers = Headers.Builder()
            .add("link: </?max_id=$maxId>; rel=\"next\", </?min_id=$minId>; rel=\"prev\"")
            .build()

        return@coroutineScope Ok(ApiResponse(headers, notifications, 200))
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
        val reports = mutableSetOf<NotificationReportEntity>()

        /** Unique relationship severance events referenced in this batch of notifications. */
        val severanceEvents = mutableSetOf<NotificationRelationshipSeveranceEventEntity>()

        /** Unique account warnings referenced in this batch of notifications. */
        val accountWarnings = mutableSetOf<NotificationAccountWarningEntity>()

        // Collect the different items from this batch of notifications.
        notifications.forEach { notification ->
            accounts.add(notification.account.asModel())

            notification.status?.asModel()?.let { status ->
                accounts.add(status.account)
                status.reblog?.account?.let { accounts.add(it) }
                statuses.add(status)
            }

            notification.report?.let { reports.add(it.asEntity(pachliAccountId, notification.id)) }
            notification.relationshipSeveranceEvent?.let { severanceEvents.add(it.asEntity(pachliAccountId, notification.id)) }
            notification.accountWarning?.let { accountWarnings.add(it.asEntity(pachliAccountId, notification.id)) }
        }

        // Bulk upsert the discovered items.
        timelineDao.upsertAccounts(accounts.asEntity(pachliAccountId))
        statusDao.upsertStatuses(statuses.map { it.asEntity(pachliAccountId) })
        notificationDao.upsertReports(reports)
        notificationDao.upsertEvents(severanceEvents)
        notificationDao.upsertAccountWarnings(accountWarnings)
        notificationDao.upsertNotifications(
            notifications.map { it.asEntity(pachliAccountId) },
        )
    }
}

/**
 * @return A [NotificationData] from a network [Notification] for [pachliAccountId].
 */
fun NotificationData.Companion.from(pachliAccountId: Long, notification: Notification) = NotificationData(
    notification = notification.asEntity(pachliAccountId),
    account = notification.account.asEntity(pachliAccountId),
    status = notification.status?.let { status ->
        TSQ(
            timelineStatus = TimelineStatusWithAccount(
                status = status.asEntity(pachliAccountId),
                account = status.account.asEntity(pachliAccountId),
            ),
            quotedStatus = (status.quote?.asModel() as? Status.Quote.FullQuote)?.let {
                TimelineStatusWithAccount(
                    status = it.status.asEntity(pachliAccountId),
                    account = it.status.account.asEntity(pachliAccountId),
                )
            },
        )
    },
    viewData = null,
    report = notification.report?.asEntity(pachliAccountId, notification.id),
    relationshipSeveranceEvent = notification.relationshipSeveranceEvent?.asEntity(pachliAccountId, notification.id),
    accountWarning = notification.accountWarning?.asEntity(pachliAccountId, notification.id),
)

/**
 * @return A [NotificationReportEntity] from a network [Notification] for [pachliAccountId].
 */
fun Report.asEntity(
    pachliAccountId: Long,
    notificationId: String,
) = NotificationReportEntity(
    pachliAccountId = pachliAccountId,
    serverId = notificationId,
    reportId = id,
    actionTaken = actionTaken,
    actionTakenAt = actionTakenAt,
    category = when (category) {
        Report.Category.SPAM -> NotificationReportEntity.Category.SPAM
        Report.Category.VIOLATION -> NotificationReportEntity.Category.VIOLATION
        Report.Category.OTHER -> NotificationReportEntity.Category.OTHER
    },
    comment = comment,
    forwarded = forwarded,
    createdAt = createdAt,
    statusIds = statusIds,
    ruleIds = ruleIds,
    targetAccount = targetAccount.asEntity(pachliAccountId),
)

/**
 * @return A [NotificationRelationshipSeveranceEventEntity] from a network [Notification]
 * for [pachliAccountId].
 */
fun RelationshipSeveranceEvent.asEntity(
    pachliAccountId: Long,
    notificationId: String,
): NotificationRelationshipSeveranceEventEntity = NotificationRelationshipSeveranceEventEntity(
    pachliAccountId = pachliAccountId,
    serverId = notificationId,
    eventId = id,
    type = when (type) {
        RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> NotificationRelationshipSeveranceEventEntity.Type.DOMAIN_BLOCK
        RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> NotificationRelationshipSeveranceEventEntity.Type.USER_DOMAIN_BLOCK
        RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> NotificationRelationshipSeveranceEventEntity.Type.ACCOUNT_SUSPENSION
        RelationshipSeveranceEvent.Type.UNKNOWN -> NotificationRelationshipSeveranceEventEntity.Type.UNKNOWN
    },
    purged = purged,
    targetName = targetName,
    followersCount = followersCount,
    followingCount = followingCount,
    createdAt = createdAt,
)

/**
 * @return A [NotificationAccountWarningEntity] from a network [Notification]
 * for [pachliAccountId].
 */
fun AccountWarning.asEntity(pachliAccountId: Long, notificationId: String) = NotificationAccountWarningEntity(
    pachliAccountId = pachliAccountId,
    serverId = notificationId,
    accountWarningId = id,
    text = text,
    action = when (action) {
        AccountWarning.Action.NONE -> NotificationAccountWarningEntity.Action.NONE
        AccountWarning.Action.DISABLE -> NotificationAccountWarningEntity.Action.DISABLE
        AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE -> NotificationAccountWarningEntity.Action.MARK_STATUSES_AS_SENSITIVE
        AccountWarning.Action.DELETE_STATUSES -> NotificationAccountWarningEntity.Action.DELETE_STATUSES
        AccountWarning.Action.SILENCE -> NotificationAccountWarningEntity.Action.SILENCE
        AccountWarning.Action.SUSPEND -> NotificationAccountWarningEntity.Action.SUSPEND
        AccountWarning.Action.UNKNOWN -> NotificationAccountWarningEntity.Action.UNKNOWN
    },
    createdAt = createdAt,
)
