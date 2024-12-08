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
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.AccountFilterDecisionUpdate
import app.pachli.core.database.model.FilterActionUpdate
import app.pachli.core.database.model.NotificationData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationViewDataEntity

@Dao
@TypeConverters(Converters::class)
interface NotificationDao {
    @Upsert
    suspend fun insertAll(notifications: List<NotificationEntity>)

    //    @Transaction
//    @Query(
//        """
//        SELECT *
//          FROM NotificationEntity
//         WHERE pachliAccountId = :pachliAccountId
//    """,
//    )
//    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>
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
-- The notification's account
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

-- The status' account
sa.serverId as 's_a_serverId', sa.timelineUserId as 's_a_timelineUserId',
sa.localUsername as 's_a_localUsername', sa.username as 's_a_username',
sa.displayName as 's_a_displayName', sa.url as 's_a_url', sa.avatar as 's_a_avatar',
sa.emojis as 's_a_emojis', sa.bot as 's_a_bot', sa.createdAt as 's_a_createdAt', sa.limited as 's_a_limited',
sa.note as 's_a_note',

-- The status's reblog account
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
nvd.accountFilterDecision as 'nvd_accountFilterDecision'

FROM NotificationEntity n
LEFT JOIN TimelineAccountEntity a ON (n.pachliAccountId = a.timelineUserId AND n.accountServerId = a.serverId)
LEFT JOIN TimelineStatusEntity s ON (n.pachliAccountId = s.timelineUserId AND n.statusServerId = s.serverId)
LEFT JOIN TimelineAccountEntity sa ON (n.pachliAccountId = sa.timelineUserId AND s.authorServerId = sa.serverId)
LEFT JOIN TimelineAccountEntity rb ON (n.pachliAccountId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN StatusViewDataEntity svd ON (n.pachliAccountId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN TranslatedStatusEntity t ON (n.pachliAccountId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
LEFT JOIN NotificationViewDataEntity nvd on (n.pachliAccountId = nvd.pachliAccountId AND n.serverId = nvd.serverId)
 WHERE n.pachliAccountId = :pachliAccountId
 ORDER BY LENGTH(n.serverId) DESC, n.serverId DESC
        """,
    )
    fun pagingSource(pachliAccountId: Long): PagingSource<Int, NotificationData>

    /**
     * Returns the database row number of the row for [notificationId]
     */
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
    fun clearAll(pachliAccountId: Long)

    @Upsert(entity = NotificationViewDataEntity::class)
    suspend fun upsert(filterActionUpdate: FilterActionUpdate)

    @Upsert(entity = NotificationViewDataEntity::class)
    suspend fun upsert(accountFilterDecisionUpdate: AccountFilterDecisionUpdate)
}
