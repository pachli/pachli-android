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

import androidx.annotation.StringRes
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.pachli.core.common.PachliError
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.AccountFilterDecisionUpdate
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.RemoteKeyEntity.RemoteKeyKind
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.eventhub.BookmarkEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.FavoriteEvent
import app.pachli.core.eventhub.PollVoteEvent
import app.pachli.core.eventhub.ReblogEvent
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Errors that can occur acting on a status.
 *
 * Converts the [throwable] from a
 * [NetworkResult][at.connyduck.calladapter.networkresult.NetworkResult] to
 * a [PachliError].
 *
 * @param throwable The wrapped throwable.
 */
// TODO: The API calls should return an ApiResult, then this can wrap those.
sealed class StatusActionError(open val throwable: Throwable) : PachliError {
    @get:StringRes
    override val resourceId = app.pachli.core.network.R.string.error_generic_fmt
    override val formatArgs: Array<out Any>? = arrayOf(throwable)
    override val cause: PachliError? = null

    /** Bookmarking a status failed. */
    data class Bookmark(override val throwable: Throwable) : StatusActionError(throwable)

    /** Favouriting a status failed. */
    data class Favourite(override val throwable: Throwable) : StatusActionError(throwable)

    /** Reblogging a status failed. */
    data class Reblog(override val throwable: Throwable) : StatusActionError(throwable)

    /** Voting in a poll failed. */
    data class VoteInPoll(override val throwable: Throwable) : StatusActionError(throwable)
}

class NotificationRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val timelineDao: TimelineDao,
    private val notificationDao: NotificationDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val eventHub: EventHub,
) {

    private var factory: InvalidatingPagingSourceFactory<Int, NotificationData>? = null

    @OptIn(ExperimentalPagingApi::class)
    suspend fun notifications(pachliAccountId: Long): Flow<PagingData<NotificationData>> {
        factory = InvalidatingPagingSourceFactory { notificationDao.pagingSource(pachliAccountId) }

        // Room is row-keyed, not item-keyed. Find the user's REFRESH key, then find the
        // row of the notification with that ID, and use that as the Pager's initialKey.
        val initialKey = remoteKeyDao.remoteKeyForKind(pachliAccountId, RKE_TIMELINE_ID, RemoteKeyKind.REFRESH)
        val row = initialKey?.key?.let { notificationDao.getNotificationRowNumber(pachliAccountId, it) }

        return Pager(
            initialKey = row,
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
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

    /**
     * Saves the ID of the notification that future refreshes will try and restore
     * from.
     *
     * @param pachliAccountId
     * @param key Notification ID to restore from. Null indicates the refresh should
     * refresh the newest notifications.
     */
    fun saveRefreshKey(pachliAccountId: Long, key: String?) = externalScope.async {
        remoteKeyDao.upsert(
            RemoteKeyEntity(
                pachliAccountId,
                RKE_TIMELINE_ID,
                RemoteKeyKind.REFRESH,
                key,
            ),
        )
    }

    /** @return The notification ID to use when refreshing. */
    suspend fun getRefreshKey(pachliAccountId: Long) = remoteKeyDao.getRefreshKey(pachliAccountId, RKE_TIMELINE_ID)

    suspend fun clearNotifications(): Response<ResponseBody> = externalScope.async {
        return@async mastodonApi.clearNotifications()
    }.await()

    fun clearContentFilter(pachliAccountId: Long, notificationId: String) = externalScope.launch {
        notificationDao.upsert(FilterActionUpdate(pachliAccountId, notificationId, FilterAction.NONE))
    }

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

    fun setContentCollapsed(pachliAccountId: Long, statusViewData: StatusViewData, isCollapsed: Boolean) =
        externalScope.launch {
            saveStatusViewData(pachliAccountId, statusViewData.copy(isCollapsed = isCollapsed))
        }

    fun setShowingContent(pachliAccountId: Long, statusViewData: StatusViewData, isShowingContent: Boolean) =
        externalScope.launch {
            saveStatusViewData(pachliAccountId, statusViewData.copy(isShowingContent = isShowingContent))
        }

    fun setExpanded(pachliAccountId: Long, statusViewData: StatusViewData, isExpanded: Boolean) = externalScope.launch {
        saveStatusViewData(pachliAccountId, statusViewData.copy(isExpanded = isExpanded))
    }

    suspend fun bookmark(
        pachliAccountId: Long,
        statusId: String,
        bookmarked: Boolean,
    ): Result<Unit, StatusActionError.Bookmark> = externalScope.async {
        val deferred = async {
            if (bookmarked) {
                mastodonApi.bookmarkStatus(statusId)
            } else {
                mastodonApi.unbookmarkStatus(statusId)
            }
        }

        timelineDao.setBookmarked(pachliAccountId, statusId, bookmarked)

        val result = deferred.await()

        result.onFailure { throwable ->
            timelineDao.setBookmarked(pachliAccountId, statusId, !bookmarked)
            return@async Err(StatusActionError.Bookmark(throwable))
        }

        result.onSuccess { eventHub.dispatch(BookmarkEvent(statusId, bookmarked)) }

        return@async Ok(Unit)
    }.await()

    suspend fun favourite(
        pachliAccountId: Long,
        statusId: String,
        favourited: Boolean,
    ): Result<Unit, StatusActionError.Favourite> = externalScope.async {
        val deferred = async {
            if (favourited) {
                mastodonApi.favouriteStatus(statusId)
            } else {
                mastodonApi.unfavouriteStatus(statusId)
            }
        }

        timelineDao.setFavourited(pachliAccountId, statusId, favourited)

        val result = deferred.await()

        result.onFailure { throwable ->
            timelineDao.setFavourited(pachliAccountId, statusId, !favourited)
            return@async Err(StatusActionError.Favourite(throwable))
        }

        result.onSuccess { eventHub.dispatch(FavoriteEvent(statusId, favourited)) }

        return@async Ok(Unit)
    }.await()

    suspend fun reblog(
        pachliAccountId: Long,
        statusId: String,
        reblogged: Boolean,
    ): Result<Unit, StatusActionError.Reblog> = externalScope.async {
        val deferred = async {
            if (reblogged) {
                mastodonApi.reblogStatus(statusId)
            } else {
                mastodonApi.unreblogStatus(statusId)
            }
        }

        timelineDao.setReblogged(pachliAccountId, statusId, reblogged)

        val result = deferred.await()

        result.onFailure { throwable ->
            timelineDao.setReblogged(pachliAccountId, statusId, !reblogged)
            return@async Err(StatusActionError.Reblog(throwable))
        }

        result.onSuccess { eventHub.dispatch(ReblogEvent(statusId, reblogged)) }

        return@async Ok(Unit)
    }.await()

    suspend fun voteInPoll(
        pachliAccountId: Long,
        statusId: String,
        pollId: String,
        choices: List<Int>,
    ): Result<Unit, StatusActionError.VoteInPoll> = externalScope.async {
        if (choices.isEmpty()) {
            return@async Err(StatusActionError.VoteInPoll(IllegalStateException()))
        }

        mastodonApi.voteInPoll(pollId, choices)
            .onSuccess { poll ->
                timelineDao.setVoted(pachliAccountId, statusId, poll)
                eventHub.dispatch(PollVoteEvent(statusId, poll))
            }
            .onFailure { throwable -> return@async Err(StatusActionError.VoteInPoll(throwable)) }

        return@async Ok(Unit)
    }.await()

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
