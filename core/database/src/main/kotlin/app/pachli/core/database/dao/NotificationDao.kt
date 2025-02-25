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
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationAccountFilterDecisionUpdate
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
    a.serverId AS 'a_serverId',
    a.timelineUserId AS 'a_timelineUserId',
    a.localUsername AS 'a_localUsername',
    a.username AS 'a_username',
    a.displayName AS 'a_displayName',
    a.url AS 'a_url',
    a.avatar AS 'a_avatar',
    a.emojis AS 'a_emojis',
    a.bot AS 'a_bot',
    a.createdAt AS 'a_createdAt',
    a.limited AS 'a_limited',
    a.note AS 'a_note',

    -- The status in the notification (if any)
    s.serverId AS 's_serverId',
    s.url AS 's_url',
    s.timelineUserId AS 's_timelineUserId',
    s.authorServerId AS 's_authorServerId',
    s.inReplyToId AS 's_inReplyToId',
    s.inReplyToAccountId AS 's_inReplyToAccountId',
    s.createdAt AS 's_createdAt',
    s.editedAt AS 's_editedAt',
    s.emojis AS 's_emojis',
    s.reblogsCount AS 's_reblogsCount',
    s.favouritesCount AS 's_favouritesCount',
    s.repliesCount AS 's_repliesCount',
    s.reblogged AS 's_reblogged',
    s.favourited AS 's_favourited',
    s.bookmarked AS 's_bookmarked',
    s.sensitive AS 's_sensitive',
    s.spoilerText AS 's_spoilerText',
    s.visibility AS 's_visibility',
    s.mentions AS 's_mentions',
    s.tags AS 's_tags',
    s.application AS 's_application',
    s.reblogServerId AS 's_reblogServerId',
    s.reblogAccountId AS 's_reblogAccountId',
    s.content AS 's_content',
    s.attachments AS 's_attachments',
    s.poll AS 's_poll',
    s.card AS 's_card',
    s.muted AS 's_muted',
    s.pinned AS 's_pinned',
    s.language AS 's_language',
    s.filtered AS 's_filtered',

    -- The status' account (if any)
    sa.serverId AS 's_a_serverId',
    sa.timelineUserId AS 's_a_timelineUserId',
    sa.localUsername AS 's_a_localUsername',
    sa.username AS 's_a_username',
    sa.displayName AS 's_a_displayName',
    sa.url AS 's_a_url',
    sa.avatar AS 's_a_avatar',
    sa.emojis AS 's_a_emojis',
    sa.bot AS 's_a_bot',
    sa.createdAt AS 's_a_createdAt',
    sa.limited AS 's_a_limited',
    sa.note AS 's_a_note',

    -- The status's reblog account (if any)
    rb.serverId AS 's_rb_serverId',
    rb.timelineUserId AS 's_rb_timelineUserId',
    rb.localUsername AS 's_rb_localUsername',
    rb.username AS 's_rb_username',
    rb.displayName AS 's_rb_displayName',
    rb.url AS 's_rb_url',
    rb.avatar AS 's_rb_avatar',
    rb.emojis AS 's_rb_emojis',
    rb.bot AS 's_rb_bot',
    rb.createdAt AS 's_rb_createdAt',
    rb.limited AS 's_rb_limited',
    rb.note AS 's_rb_note',

    -- Status view data
    svd.serverId AS 's_svd_serverId',
    svd.pachliAccountId AS 's_svd_pachliAccountId',
    svd.expanded AS 's_svd_expanded',
    svd.contentShowing AS 's_svd_contentShowing',
    svd.contentCollapsed AS 's_svd_contentCollapsed',
    svd.translationState AS 's_svd_translationState',

    -- Translation
    t.serverId AS 's_t_serverId',
    t.timelineUserId AS 's_t_timelineUserId',
    t.content AS 's_t_content',
    t.spoilerText AS 's_t_spoilerText',
    t.poll AS 's_t_poll',
    t.attachments AS 's_t_attachments',
    t.provider AS 's_t_provider',

    -- NotificationViewData
    nvd.pachliAccountId AS 'nvd_pachliAccountId',
    nvd.serverId AS 'nvd_serverId',
    nvd.contentFilterAction AS 'nvd_contentFilterAction',
    nvd.accountFilterDecision AS 'nvd_accountFilterDecision',

    -- NotificationReportEntity
    report.pachliAccountId AS 'report_pachliAccountId',
    report.serverId AS 'report_serverId',
    report.reportId AS 'report_reportId',
    report.actionTaken AS 'report_actionTaken',
    report.actionTakenAt AS 'report_actionTakenAt',
    report.category AS 'report_category',
    report.comment AS 'report_comment',
    report.forwarded AS 'report_forwarded',
    report.createdAt AS 'report_createdAt',
    report.statusIds AS 'report_statusIds',
    report.ruleIds AS 'report_ruleIds',
    report.target_serverId AS 'report_target_serverId',
    report.target_timelineUserId AS 'report_target_timelineUserId',
    report.target_localUsername AS 'report_target_localUsername',
    report.target_username AS 'report_target_username',
    report.target_displayName AS 'report_target_displayName',
    report.target_url AS 'report_target_url',
    report.target_avatar AS 'report_target_avatar',
    report.target_emojis AS 'report_target_emojis',
    report.target_bot AS 'report_target_bot',
    report.target_createdAt AS 'report_target_createdAt',
    report.target_limited AS 'report_target_limited',
    report.target_note AS 'report_target_note',

    -- NotificationRelationshipSeveranceEvent
    rse.pachliAccountId AS 'rse_pachliAccountId',
    rse.serverId AS 'rse_serverId',
    rse.eventId AS 'rse_eventId',
    rse.type AS 'rse_type',
    rse.purged AS 'rse_purged',
    rse.followersCount AS 'rse_followersCount',
    rse.followingCount AS 'rse_followingCount',
    rse.createdAt AS 'rse_createdAt'

FROM NotificationEntity AS n
LEFT JOIN TimelineAccountEntity AS a ON (n.pachliAccountId = a.timelineUserId AND n.accountServerId = a.serverId)
LEFT JOIN StatusEntity AS s ON (n.pachliAccountId = s.timelineUserId AND n.statusServerId = s.serverId)
LEFT JOIN TimelineAccountEntity AS sa ON (n.pachliAccountId = sa.timelineUserId AND s.authorServerId = sa.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (n.pachliAccountId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (n.pachliAccountId = svd.pachliAccountId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS t
    ON (n.pachliAccountId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
LEFT JOIN NotificationViewDataEntity AS nvd ON (n.pachliAccountId = nvd.pachliAccountId AND n.serverId = nvd.serverId)
LEFT JOIN
    NotificationReportEntity AS report
    ON (n.pachliAccountId = report.pachliAccountId AND n.serverId = report.serverId)
LEFT JOIN
    NotificationRelationshipSeveranceEventEntity AS rse
    ON (n.pachliAccountId = rse.pachliAccountId AND n.serverId = rse.serverId)
WHERE n.pachliAccountId = :pachliAccountId
ORDER BY LENGTH(n.serverId) DESC, n.serverId DESC
""",
    )
    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>

    /**
     * @return Row number (0-based) of the notification with ID [notificationId]
     * for [pachliAccountId]
     */
    @Query(
        """
SELECT rownum
FROM (
    SELECT
        t1.pachliAccountId,
        t1.serverId,
        COUNT(t2.serverId) - 1 AS rownum
    FROM NotificationEntity AS t1
    INNER JOIN
        NotificationEntity AS t2
        ON
            t1.pachliAccountId = t2.pachliAccountId
            AND (LENGTH(t1.serverId) <= LENGTH(t2.serverId) AND t1.serverId <= t2.serverId)
    WHERE t1.pachliAccountId = :pachliAccountId
    GROUP BY t1.serverId
    ORDER BY LENGTH(t1.serverId) DESC, t1.serverId DESC
)
WHERE serverId = :notificationId
""",
    )
    suspend fun getNotificationRowNumber(pachliAccountId: Long, notificationId: String): Int

    /** Remove all cached notifications for [pachliAccountId]. */
    @Query(
        """
DELETE
FROM NotificationEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
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
    suspend fun upsert(notificationAccountFilterDecisionUpdate: NotificationAccountFilterDecisionUpdate)

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
WHERE
    pachliAccountId = :pachliAccountId
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
WHERE
    pachliAccountId = :pachliAccountId
    AND reportId = :reportId
""",
    )
    suspend fun loadReportById(pachliAccountId: Long, reportId: String): NotificationReportEntity?

    @Deprecated("Only present for use in tests")
    @Query(
        """
SELECT *
FROM NotificationRelationshipSeveranceEventEntity
WHERE
    pachliAccountId = :pachliAccountId
    AND eventId = :eventId
""",
    )
    suspend fun loadRelationshipSeveranceeventById(pachliAccountId: Long, eventId: String): NotificationRelationshipSeveranceEventEntity?
}
