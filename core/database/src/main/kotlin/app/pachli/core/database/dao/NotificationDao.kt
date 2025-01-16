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

package app.pachli.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.AccountFilterDecisionUpdate
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.NotificationViewDataEntity

@Dao
@TypeConverters(Converters::class)
interface NotificationDao {
    @Transaction
    @Query(
        """
            SELECT

-- Basic notification info
n.pachliAccountId,
n.serverId,
n.type,
n.createdAt,
n.accountServerId,
n.statusServerId,

-- The account that triggered the notification
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot', a.createdAt as 'a_createdAt', a.limited as 'a_limited',
a.note as 'a_note',

-- The status in the notification (if any)
s.serverId as 's_serverId', s.url as 's_url', s.timelineUserId as 's_timelineUserId',
s.authorServerId as 's_authorServerId', s.inReplyToId as 's_inReplyToId',
s.inReplyToAccountId as 's_inReplyToAccountId', s.createdAt as 's_createdAt',
s.editedAt as 's_editedAt',
s.emojis as 's_emojis', s.reblogsCount as 's_reblogsCount',
s.favouritesCount as 's_favouritesCount', s.repliesCount as 's_repliesCount',
s.reblogged as 's_reblogged', s.favourited as 's_favourited',
s.bookmarked as 's_bookmarked', s.sensitive as 's_sensitive',
s.spoilerText as 's_spoilerText', s.visibility as 's_visibility',
s.mentions as 's_mentions', s.tags as 's_tags', s.application as 's_application',
s.reblogServerId as 's_reblogServerId',s.reblogAccountId as 's_reblogAccountId',
s.content as 's_content', s.attachments as 's_attachments', s.poll as 's_poll',
s.card as 's_card', s.muted as 's_muted', s.pinned as 's_pinned', s.language as 's_language',
s.filtered as 's_filtered',

-- The status' account (if any)
sa.serverId as 's_a_serverId', sa.timelineUserId as 's_a_timelineUserId',
sa.localUsername as 's_a_localUsername', sa.username as 's_a_username',
sa.displayName as 's_a_displayName', sa.url as 's_a_url', sa.avatar as 's_a_avatar',
sa.emojis as 's_a_emojis', sa.bot as 's_a_bot', sa.createdAt as 's_a_createdAt', sa.limited as 's_a_limited',
sa.note as 's_a_note',

-- The status's reblog account (if any)
rb.serverId as 's_rb_serverId', rb.timelineUserId 's_rb_timelineUserId',
rb.localUsername as 's_rb_localUsername', rb.username as 's_rb_username',
rb.displayName as 's_rb_displayName', rb.url as 's_rb_url', rb.avatar as 's_rb_avatar',
rb.emojis as 's_rb_emojis', rb.bot as 's_rb_bot', rb.createdAt as 's_rb_createdAt', rb.limited as 's_rb_limited',
rb.note as 's_rb_note',

-- Status view data
svd.serverId as 's_svd_serverId', svd.timelineUserId as 's_svd_timelineUserId',
svd.expanded as 's_svd_expanded', svd.contentShowing as 's_svd_contentShowing',
svd.contentCollapsed as 's_svd_contentCollapsed', svd.translationState as 's_svd_translationState',

-- Translation
t.serverId as 's_t_serverId', t.timelineUserId as 's_t_timelineUserId', t.content as 's_t_content',
t.spoilerText as 's_t_spoilerText', t.poll as 's_t_poll', t.attachments as 's_t_attachments',
t.provider as 's_t_provider',

-- NotificationViewData
nvd.pachliAccountId as 'nvd_pachliAccountId',
nvd.serverId as 'nvd_serverId',
nvd.contentFilterAction as 'nvd_contentFilterAction',
nvd.accountFilterDecision as 'nvd_accountFilterDecision',

-- NotificationReportEntity
report.pachliAccountId as 'report_pachliAccountId',
report.serverId as 'report_serverId',
report.actionTaken as 'report_actionTaken',
report.actionTakenAt as 'report_actionTakenAt',
report.category as 'report_category',
report.comment as 'report_comment',
report.forwarded as 'report_forwarded',
report.createdAt as 'report_createdAt',
report.statusIds as 'report_statusIds',
report.ruleIds as 'report_rulesIds',
report.target_serverId as 'report_target_serverId',
report.target_timelineUserId as 'report_target_timelineUserId',
report.target_localUsername as 'report_target_localUsername',
report.target_username as 'report_target_username',
report.target_displayName as 'report_target_displayName',
report.target_url as 'report_target_url',
report.target_avatar as 'report_target_avatar',
report.target_emojis as 'report_target_emojis',
report.target_bot as 'report_target_bot',
report.target_createdAt as 'report_target_createdAt',
report.target_limited as 'report_target_limited',
report.target_note as 'report_target_note',

-- NotificationRelationshipSeveranceEvent
rse.pachliAccountId as 'rse_pachliAccountId',
rse.serverId as 'rse_serverId',
rse.eventId as 'rse_eventId',
rse.type as 'rse_type',
rse.purged as 'rse_purged',
rse.followersCount as 'rse_followersCount',
rse.followingCount as 'rse_followingCount',
rse.createdAt as 'rse_createdAt'

FROM NotificationEntity n
LEFT JOIN TimelineAccountEntity a ON (n.pachliAccountId = a.timelineUserId AND n.accountServerId = a.serverId)
LEFT JOIN TimelineStatusEntity s ON (n.pachliAccountId = s.timelineUserId AND n.statusServerId = s.serverId)
LEFT JOIN TimelineAccountEntity sa ON (n.pachliAccountId = sa.timelineUserId AND s.authorServerId = sa.serverId)
LEFT JOIN TimelineAccountEntity rb ON (n.pachliAccountId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN StatusViewDataEntity svd ON (n.pachliAccountId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN TranslatedStatusEntity t ON (n.pachliAccountId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
LEFT JOIN NotificationViewDataEntity nvd on (n.pachliAccountId = nvd.pachliAccountId AND n.serverId = nvd.serverId)
LEFT JOIN NotificationReportEntity report on (n.pachliAccountId = report.pachliAccountId AND n.serverId = report.serverId)
LEFT JOIN NotificationRelationshipSeveranceEventEntity rse on (n.pachliAccountId = rse.pachliAccountId AND n.serverId = rse.serverId)
WHERE n.pachliAccountId = :pachliAccountId
ORDER BY LENGTH(n.serverId) DESC, n.serverId DESC
        """,
    )
    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>

    /** @return The database row number of the row for [notificationId]. */
    @Query(
        """
SELECT RowNum
FROM
  (SELECT pachliAccountId, serverId,
     (SELECT count(*) + 1
      FROM notificationentity
      WHERE rowid < t.rowid
      ORDER BY length(serverId) DESC, serverId DESC) AS RowNum
   FROM notificationentity t)
WHERE pachliAccountId = :pachliAccountId AND serverId = :notificationId;
        """,
    )
    suspend fun getNotificationRowNumber(pachliAccountId: Long, notificationId: String): Int

    /** Remove all cached notifications for [pachliAccountId]. */
    @Query("DELETE FROM NotificationEntity WHERE pachliAccountId = :pachliAccountId")
    suspend fun deleteAllNotificationsForAccount(pachliAccountId: Long)

    @Upsert
    suspend fun upsertNotifications(notifications: Collection<NotificationEntity>)

    @Upsert
    fun upsertReports(reports: Collection<NotificationReportEntity>)

    @Upsert
    fun upsertEvents(events: Collection<NotificationRelationshipSeveranceEventEntity>)

    @Upsert(entity = NotificationViewDataEntity::class)
    suspend fun upsert(filterActionUpdate: FilterActionUpdate)

    @Upsert(entity = NotificationViewDataEntity::class)
    suspend fun upsert(accountFilterDecisionUpdate: AccountFilterDecisionUpdate)

    @Deprecated("Only present for use in tests")
    @Query(
        """
        SELECT *
          FROM NotificationEntity
         WHERE pachliAccountId = :pachliAccountId
         """,
    )
    suspend fun loadAllForAccount(pachliAccountId: Long): List<NotificationEntity>

    @Deprecated("Only present for use in tests")
    @Query(
        """
        SELECT *
          FROM NotificationViewDataEntity
         WHERE pachliAccountId = :pachliAccountId
           AND serverId = :serverId
         """,
    )
    suspend fun loadViewData(pachliAccountId: Long, serverId: String): NotificationViewDataEntity?

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Deprecated("Only present for use in tests")
    @Query(
        """
        SELECT *
          FROM NotificationReportEntity
         WHERE pachliAccountId = :pachliAccountId
           AND reportId = :reportId
        """,
    )
    suspend fun loadReportById(pachliAccountId: Long, reportId: String): NotificationReportEntity?

    @Deprecated("Only present for use in tests")
    @Query(
        """
        SELECT *
          FROM NotificationRelationshipSeveranceEventEntity
         WHERE pachliAccountId = :pachliAccountId
           AND eventId = :eventId
        """,
    )
    suspend fun loadRelationshipSeveranceeventById(pachliAccountId: Long, eventId: String): NotificationRelationshipSeveranceEventEntity?
}
