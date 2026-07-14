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

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.database.dao.CollectionsDao
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.NotificationAccountWarning
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationRelationshipSeveranceEvent
import app.pachli.core.database.model.NotificationReport
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.asEntity
import app.pachli.core.model.Account
import app.pachli.core.model.AccountWarning
import app.pachli.core.model.RelationshipSeveranceEvent
import app.pachli.core.model.Report
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.TimelineAccount
import app.pachli.core.model.asTimelineCollection
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers

/**
 * @property context
 * @property pachliAccountId
 * @property accountId Server ID of the account identified by [pachliAccountId],
 * needed by [Notification.asModel].
 * @property mastodonApi
 * @property transactionProvider
 * @property timelineDao
 * @property remoteKeyDao
 * @property notificationDao
 * @property statusDao
 * @property excludeTypes 0 or more [Notification.Type] that should not be fetched.
 */
@OptIn(ExperimentalPagingApi::class)
class NotificationsRemoteMediator(
    private val context: Context,
    private val pachliAccountId: Long,
    private val accountId: String,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val notificationDao: NotificationDao,
    private val statusDao: StatusDao,
    private val collectionsDao: CollectionsDao,
    private val excludeTypes: Iterable<Notification.Type>,
) : RemoteMediator<Int, NotificationData>() {
    private val remoteKeyTimelineId = Timeline.Notifications.remoteKeyTimelineId

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
            }.getOrElse { return@transactionProvider MediatorResult.Error(it.asThrowable(context)) }

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
            prevPage.await().getOrElse { return@coroutineScope Err(it) }.let { this.addAll(it.body) }
            notification.await().get()?.let {
                if (!excludeTypes.contains(it.body.type)) this.add(it.body)
            }
            nextPage.await().getOrElse { return@coroutineScope Err(it) }.let { this.addAll(it.body) }
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
        val timelineAccounts = mutableSetOf<TimelineAccount>()

        /** Unique statuses referenced in this batch of notifications. */
        val statuses = mutableSetOf<Status>()

        /** Unique collections referenced in this batch of notifications. */
        val collections = mutableSetOf<app.pachli.core.model.Collection>()

        /**
         * Account IDs that have been seen in collections. These must be resolved
         * to full [Account]s.
         */
        val accountIdsInCollections = mutableSetOf<String>()

        // Convert to core.model.Notification to ensure the notifications are valid.
        val validNotifications = notifications.asModel(accountId)
        if (validNotifications.isEmpty()) return

        validNotifications.forEach { notification ->
            timelineAccounts.add(notification.account)

            (notification as? app.pachli.core.model.Notification.WithStatus)?.status?.let { status ->
                timelineAccounts.add(status.account)
                status.reblog?.account?.let { timelineAccounts.add(it) }

                statuses.add(status)

                (status.quote as? Status.Quote.FullQuote)?.status?.let {
                    timelineAccounts.add(it.account)
                    it.reblog?.let {
                        timelineAccounts.add(it.account)
                        statuses.add(it)
                    }
                    statuses.add(it)
                }
            }

            (notification as? app.pachli.core.model.Notification.WithCollection)?.collection?.let { collection ->
                collections.add(collection)
                accountIdsInCollections.addAll(collection.allAccountIds())
            }
        }

        // Resolve all the account IDs referenced in any collection to full accounts
        // so:
        // 1. The accounts can be saved.
        // 2. The accounts can be used to create TimelineCollectionEntity objects.
        val accountsInCollections = resolveAccounts(accountIdsInCollections)

        // Bulk upsert the discovered items.
        timelineDao.upsertTimelineAccounts(timelineAccounts.asEntity(pachliAccountId))
        timelineDao.upsertAccounts(accountsInCollections.values.asEntity(pachliAccountId))
        statusDao.upsertStatuses(statuses.map { it.asEntity(pachliAccountId) })
        notificationDao.upsertNotifications(notifications.asModel(accountId).asEntity(pachliAccountId))

        collectionsDao.upsertCollections(collections.asEntity(pachliAccountId))
        collectionsDao.upsertCollectionItems(
            collections.flatMap { it.items.asEntity(pachliAccountId, it.serverId) },
        )
        collectionsDao.upsertTimelineCollections(
            collections.map { it.asTimelineCollection(accountsInCollections) }.asEntity(pachliAccountId),
        )
    }

    /**
     * @return All the account IDs referenced in [Collection]. The account ID of the owner
     * and of all items in the collection.
     */
    private fun app.pachli.core.model.Collection.allAccountIds(): List<String> {
        return items.mapNotNull { it.accountId } + accountId
    }

    /**
     * Calls the server to convert all [accountIds] to the full [Account] details.
     *
     * @return Map between the server's ID for the account and the [Account].
     */
    private suspend fun resolveAccounts(accountIds: Collection<String>): Map<String, Account> {
        if (accountIds.isEmpty()) return emptyMap()

        return mastodonApi.accounts(accountIds)
            .map { it.body.asModel().associateBy { it.serverId } }
            .getOrElse { emptyMap() }
    }
}

/**
 * @return A [NotificationReport] from a network [Notification] for [pachliAccountId].
 */
fun Report.asEntity(pachliAccountId: Long) = NotificationReport(
    reportId = serverId,
    actionTaken = actionTaken,
    actionTakenAt = actionTakenAt,
    category = when (category) {
        Report.Category.SPAM -> NotificationReport.Category.SPAM
        Report.Category.VIOLATION -> NotificationReport.Category.VIOLATION
        Report.Category.OTHER -> NotificationReport.Category.OTHER
    },
    comment = comment,
    forwarded = forwarded,
    createdAt = createdAt,
    statusIds = statusIds,
    ruleIds = ruleIds,
    targetAccount = targetAccount.asEntity(pachliAccountId),
)

/**
 * @return A [NotificationRelationshipSeveranceEvent].
 */
fun RelationshipSeveranceEvent.asEntity() = NotificationRelationshipSeveranceEvent(
    eventId = id,
    type = when (type) {
        RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> NotificationRelationshipSeveranceEvent.Type.DOMAIN_BLOCK
        RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> NotificationRelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK
        RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> NotificationRelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION
        RelationshipSeveranceEvent.Type.UNKNOWN -> NotificationRelationshipSeveranceEvent.Type.UNKNOWN
    },
    purged = purged,
    targetName = targetName,
    followersCount = followersCount,
    followingCount = followingCount,
    createdAt = createdAt,
)

/**
 * @return A [NotificationAccountWarning].
 */
fun AccountWarning.asEntity() = NotificationAccountWarning(
    accountWarningId = id,
    text = text,
    action = when (action) {
        AccountWarning.Action.NONE -> NotificationAccountWarning.Action.NONE
        AccountWarning.Action.DISABLE -> NotificationAccountWarning.Action.DISABLE
        AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE -> NotificationAccountWarning.Action.MARK_STATUSES_AS_SENSITIVE
        AccountWarning.Action.DELETE_STATUSES -> NotificationAccountWarning.Action.DELETE_STATUSES
        AccountWarning.Action.SILENCE -> NotificationAccountWarning.Action.SILENCE
        AccountWarning.Action.SUSPEND -> NotificationAccountWarning.Action.SUSPEND
        AccountWarning.Action.UNKNOWN -> NotificationAccountWarning.Action.UNKNOWN
    },
    createdAt = createdAt,
)
