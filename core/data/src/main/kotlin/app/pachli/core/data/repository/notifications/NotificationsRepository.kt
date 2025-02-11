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
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountFilterDecisionUpdate
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.onSuccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Repository for [NotificationData] interacting with the remote [MastodonApi]
 * using the local database as a cache.
 */
class NotificationsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val notificationDao: NotificationDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val statusDao: StatusDao,
) {
    private var factory: InvalidatingPagingSourceFactory<Int, NotificationData>? = null

    /**
     * @return Notifications for [pachliAccountId].
     */
    @OptIn(ExperimentalPagingApi::class)
    suspend fun notifications(pachliAccountId: Long): Flow<PagingData<NotificationData>> {
        factory = InvalidatingPagingSourceFactory { notificationDao.pagingSource(pachliAccountId) }

        // Room is row-keyed, not item-keyed. Find the user's REFRESH key, then find the
        // row of the notification with that ID, and use that as the Pager's initialKey.
        val initialKey = remoteKeyDao.remoteKeyForKind(pachliAccountId, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH)?.key
        val row = initialKey?.let { notificationDao.getNotificationRowNumber(pachliAccountId, it) }

        return Pager(
            initialKey = row,
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
            ),
            remoteMediator = NotificationsRemoteMediator(
                pachliAccountId,
                mastodonApi,
                transactionProvider,
                timelineDao,
                remoteKeyDao,
                notificationDao,
                statusDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow
    }

    fun invalidate() = factory?.invalidate()

    /**
     * Saves the ID of the notification that future refreshes will try and restore
     * from.
     *
     * @param pachliAccountId
     * @param key Notification ID to restore from. Null indicates the refresh should
     * refresh the newest notifications.
     */
    suspend fun saveRefreshKey(pachliAccountId: Long, key: String?) = externalScope.async {
        Timber.d("saveRefreshKey: $key")
        remoteKeyDao.upsert(
            RemoteKeyEntity(pachliAccountId, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH, key),
        )
    }.await()

    /**
     * @param pachliAccountId
     * @return The most recent saved refresh key. Null if not set, or the refresh
     * should fetch the latest notifications.
     */
    suspend fun getRefreshKey(pachliAccountId: Long): String? = externalScope.async {
        remoteKeyDao.getRefreshKey(pachliAccountId, RKE_TIMELINE_ID)
    }.await()

    /**
     * Clears (deletes) all notifications from the server. Invalidates the repository
     * if successful.
     */
    suspend fun clearNotifications() = externalScope.async {
        return@async mastodonApi.clearNotifications().onSuccess { invalidate() }
    }.await()

    /**
     * Sets the [FilterAction] for [notificationId] to [FilterAction.NONE]
     *
     * @param pachliAccountId
     * @param notificationId Notification's server ID.
     */
    fun clearContentFilter(pachliAccountId: Long, notificationId: String) = externalScope.launch {
        notificationDao.upsert(FilterActionUpdate(pachliAccountId, notificationId, FilterAction.NONE))
    }

    /**
     * Sets the [AccountFilterDecision] for [notificationId] to [accountFilterDecision].
     *
     * @param pachliAccountId
     * @param notificationId Notification's server ID.
     * @param accountFilterDecision New [AccountFilterDecision].
     */
    fun setAccountFilterDecision(
        pachliAccountId: Long,
        notificationId: String,
        accountFilterDecision: AccountFilterDecision,
    ) = externalScope.launch {
        notificationDao.upsert(
            AccountFilterDecisionUpdate(
                pachliAccountId,
                notificationId,
                accountFilterDecision,
            ),
        )
    }

    // Copied from CachedTimelineRepository
    // Status management probably belongs in a StatusRepository to hold these
    // functions, with separate repositories for timelines and notifications.
    private suspend fun saveStatusViewData(pachliAccountId: Long, statusViewData: StatusViewData) =
        externalScope.launch {
            timelineDao.upsertStatusViewData(
                StatusViewDataEntity(
                    serverId = statusViewData.actionableId,
                    timelineUserId = pachliAccountId,
                    expanded = statusViewData.isExpanded,
                    contentShowing = statusViewData.isShowingContent,
                    contentCollapsed = statusViewData.isCollapsed,
                    translationState = statusViewData.translationState,
                ),
            )
        }.join()

    /**
     * Saves a copy of [statusViewData] with [StatusViewData.isCollapsed] set to
     * [isCollapsed].
     */
    fun setContentCollapsed(pachliAccountId: Long, statusViewData: StatusViewData, isCollapsed: Boolean) =
        externalScope.launch {
            saveStatusViewData(pachliAccountId, statusViewData.copy(isCollapsed = isCollapsed))
        }

    /**
     * Saves a copy of [statusViewData] with [StatusViewData.isShowingContent] set to
     * [isShowingContent].
     */
    fun setShowingContent(pachliAccountId: Long, statusViewData: StatusViewData, isShowingContent: Boolean) =
        externalScope.launch {
            saveStatusViewData(pachliAccountId, statusViewData.copy(isShowingContent = isShowingContent))
        }

    /**
     * Saves a copy of [statusViewData] with [StatusViewData.isExpanded] set to
     * [isExpanded].
     */
    fun setExpanded(pachliAccountId: Long, statusViewData: StatusViewData, isExpanded: Boolean) = externalScope.launch {
        saveStatusViewData(pachliAccountId, statusViewData.copy(isExpanded = isExpanded))
    }

    companion object {
        private const val PAGE_SIZE = 30

        internal const val RKE_TIMELINE_ID = "NOTIFICATIONS"
    }
}

fun NotificationEntity.Type.Companion.from(notificationType: Notification.Type) = when (notificationType) {
    Notification.Type.UNKNOWN -> NotificationEntity.Type.UNKNOWN
    Notification.Type.MENTION -> NotificationEntity.Type.MENTION
    Notification.Type.REBLOG -> NotificationEntity.Type.REBLOG
    Notification.Type.FAVOURITE -> NotificationEntity.Type.FAVOURITE
    Notification.Type.FOLLOW -> NotificationEntity.Type.FOLLOW
    Notification.Type.FOLLOW_REQUEST -> NotificationEntity.Type.FOLLOW_REQUEST
    Notification.Type.POLL -> NotificationEntity.Type.POLL
    Notification.Type.STATUS -> NotificationEntity.Type.STATUS
    Notification.Type.SIGN_UP -> NotificationEntity.Type.SIGN_UP
    Notification.Type.UPDATE -> NotificationEntity.Type.UPDATE
    Notification.Type.REPORT -> NotificationEntity.Type.REPORT
    Notification.Type.SEVERED_RELATIONSHIPS -> NotificationEntity.Type.SEVERED_RELATIONSHIPS
}
