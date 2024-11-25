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

package app.pachli.core.data.notifications

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationType
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyKind
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

// TODO:
// - Filter preferences will need to be applied in the ViewModel

class NotificationRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val notificationDao: NotificationDao,
    private val remoteKeyDao: RemoteKeyDao,
) {
    private var factory: InvalidatingPagingSourceFactory<Int, NotificationData>? = null

    @OptIn(ExperimentalPagingApi::class)
    fun notifications(pachliAccountId: Long): Flow<PagingData<NotificationData>> {
        factory = InvalidatingPagingSourceFactory { notificationDao.pagingSource(pachliAccountId) }

        return Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = NotificationRemoteMediator(
                pachliAccountId,
                mastodonApi,
                transactionProvider,
                timelineDao,
                remoteKeyDao,
                notificationDao,
            ),
            pagingSourceFactory = factory!!,
        ).flow
    }

    fun invalidate() = factory?.invalidate()

    suspend fun clearNotifications(): Response<ResponseBody> = externalScope.async {
        return@async mastodonApi.clearNotifications()
    }.await()
}

// TODO: Assisted inject?
@OptIn(ExperimentalPagingApi::class)
class NotificationRemoteMediator(
    private val pachliAccountId: Long,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val notificationDao: NotificationDao,
) : RemoteMediator<Int, NotificationData>() {
    private val RKE_TIMELINE_ID = "NOTIFICATIONS"

    override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(loadType: LoadType, state: PagingState<Int, NotificationData>): MediatorResult {
        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> mastodonApi.notifications()

                LoadType.PREPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        RKE_TIMELINE_ID,
                        RemoteKeyKind.PREV,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(minId = rke.key)
                }

                LoadType.APPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        RKE_TIMELINE_ID,
                        RemoteKeyKind.NEXT,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(maxId = rke.key)
                }
            }

            val notifications = response.body()
            if (!response.isSuccessful || notifications == null) {
                return MediatorResult.Error(HttpException(response))
            }

            if (notifications.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
            }

            val links = Links.from(response.headers()["link"])

            transactionProvider {
                when (loadType) {
                    LoadType.REFRESH -> {
                        remoteKeyDao.delete(pachliAccountId, RKE_TIMELINE_ID)
                        notificationDao.clearAll(pachliAccountId)

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
            }

//            notificationDao.insertAll(
//                notifications.map {
//                    NotificationEntity.from(pachliAccountId, it)
//                },
//            )
            insertNotifications(pachliAccountId, notifications)

            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            Timber.e(e, "error loading, loadtype = %s", loadType)
            MediatorResult.Error(e)
        }
    }

    private suspend fun insertNotifications(pachliAccountId: Long, notifications: List<Notification>) {
        notifications.forEach { notification ->
            timelineDao.insertAccount(TimelineAccountEntity.from(notification.account, pachliAccountId))
            notification.status?.let { status ->
                timelineDao.insertAccount(TimelineAccountEntity.from(status.account, pachliAccountId))
                status.reblog?.account?.let {
                    timelineDao.insertAccount(TimelineAccountEntity.from(it, pachliAccountId))
                }
                timelineDao.insertStatus(
                    TimelineStatusEntity.from(status, pachliAccountId),
                )
            }
        }

        notificationDao.insertAll(
            notifications.map { NotificationEntity.from(pachliAccountId, it) },
        )
    }
}

fun NotificationType.Companion.from(notificationType: Notification.Type) = when (notificationType) {
    Notification.Type.UNKNOWN -> NotificationType.UNKNOWN
    Notification.Type.MENTION -> NotificationType.MENTION
    Notification.Type.REBLOG -> NotificationType.REBLOG
    Notification.Type.FAVOURITE -> NotificationType.FAVOURITE
    Notification.Type.FOLLOW -> NotificationType.FOLLOW
    Notification.Type.FOLLOW_REQUEST -> NotificationType.FOLLOW_REQUEST
    Notification.Type.POLL -> NotificationType.POLL
    Notification.Type.STATUS -> NotificationType.STATUS
    Notification.Type.SIGN_UP -> NotificationType.SIGN_UP
    Notification.Type.UPDATE -> NotificationType.UPDATE
    Notification.Type.REPORT -> NotificationType.REPORT
    Notification.Type.SEVERED_RELATIONSHIPS -> NotificationType.SEVERED_RELATIONSHIPS
}

fun NotificationEntity.Companion.from(pachliAccountId: Long, notification: Notification) = NotificationEntity(
    pachliAccountId = pachliAccountId,
    serverId = notification.id,
    type = NotificationType.from(notification.type),
    createdAt = notification.createdAt.toInstant(),
    accountServerId = notification.account.id,
    statusServerId = notification.status?.id,
//    account = TimelineAccountEntity.from(notification.account, pachliAccountId),
//    status = notification.status?.let { TimelineStatusEntity.from(it, pachliAccountId) },
//    // TODO: Handle report
//    report = null,
)

fun NotificationData.Companion.from(pachliAccountId: Long, notification: Notification) = NotificationData(
    notification = NotificationEntity.from(pachliAccountId, notification),
    account = TimelineAccountEntity.from(notification.account, pachliAccountId),
)
