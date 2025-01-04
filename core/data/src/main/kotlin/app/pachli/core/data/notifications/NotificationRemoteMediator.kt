package app.pachli.core.data.notifications

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyKind
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.model.Report
import app.pachli.core.network.retrofit.MastodonApi
import retrofit2.HttpException
import timber.log.Timber

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

    override suspend fun load(loadType: LoadType, state: PagingState<Int, NotificationData>): MediatorResult {
        return try {
            val response = when (loadType) {
                LoadType.REFRESH -> mastodonApi.notifications(limit = state.config.initialLoadSize)

                LoadType.PREPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        RKE_TIMELINE_ID,
                        RemoteKeyKind.PREV,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(minId = rke.key, limit = state.config.pageSize)
                }

                LoadType.APPEND -> {
                    val rke = remoteKeyDao.remoteKeyForKind(
                        pachliAccountId,
                        RKE_TIMELINE_ID,
                        RemoteKeyKind.NEXT,
                    ) ?: return MediatorResult.Success(endOfPaginationReached = true)
                    mastodonApi.notifications(maxId = rke.key, limit = state.config.pageSize)
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

                insertNotifications(pachliAccountId, notifications)
            }

            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            Timber.e(e, "error loading, loadtype = %s", loadType)
            MediatorResult.Error(e)
        }
    }

    private suspend fun insertNotifications(pachliAccountId: Long, notifications: List<Notification>) {
        // TODO: This could (maybe) be better about minimising database updates.
        // In the same batch of notifications:
        // - The same account might boost/favourite the same status (two notifiations)
        // - The same status might be affected repeatedly
        // - The same account might make multiple mentions
        //
        // This suggests iterating over the notifications once to collect data about
        // all the accounts and statuses mentioned (collapsing duplicates), and then
        // batch-inserting the accounts, statuses, and notifications.
        //
        // The status in a notification is always the most recent version of the
        // status known (not the status as it was the time the notification was
        // generated), so this doesn't risk overwriting a status with obsolete
        // information.

        notifications.forEach { notification ->
            timelineDao.insertAccount(
                TimelineAccountEntity.from(
                    notification.account,
                    pachliAccountId,
                ),
            )
            notification.status?.let { status ->
                timelineDao.insertAccount(
                    TimelineAccountEntity.from(
                        status.account,
                        pachliAccountId,
                    ),
                )
                status.reblog?.account?.let {
                    timelineDao.insertAccount(TimelineAccountEntity.from(it, pachliAccountId))
                }
                timelineDao.insertStatus(
                    TimelineStatusEntity.from(status, pachliAccountId),
                )
            }

            NotificationReportEntity.from(pachliAccountId, notification)?.let { notificationReportEntity ->
                notificationDao.upsert(notificationReportEntity)
            }

            NotificationRelationshipSeveranceEventEntity.from(pachliAccountId, notification)?.let { notificationRelationshipSeveranceEventEntity ->
                notificationDao.upsert(notificationRelationshipSeveranceEventEntity)
            }
        }

        notificationDao.upsert(
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
            status = TimelineStatusEntity.from(status, pachliAccountId),
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
fun NotificationReportEntity.Companion.from(pachliAccountId: Long, notification: Notification): NotificationReportEntity? {
    val report = notification.report ?: return null

    return NotificationReportEntity(
        pachliAccountId = pachliAccountId,
        serverId = notification.id,
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
fun NotificationRelationshipSeveranceEventEntity.Companion.from(pachliAccountId: Long, notification: Notification): NotificationRelationshipSeveranceEventEntity? {
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
