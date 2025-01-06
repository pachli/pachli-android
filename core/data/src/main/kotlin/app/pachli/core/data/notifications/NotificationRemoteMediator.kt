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
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
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

                upsertNotifications(pachliAccountId, notifications)
            }

            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            Timber.e(e, "error loading, loadtype = %s", loadType)
            MediatorResult.Error(e)
        }
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
        timelineDao.upsertStatuses(statuses.map { TimelineStatusEntity.from(it, pachliAccountId) })
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
