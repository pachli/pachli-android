/*
 * Copyright 2021 Tusky Contributors
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
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.network.model.Poll

@Dao
@TypeConverters(Converters::class)
abstract class TimelineDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Upsert
    abstract suspend fun upsertAccounts(accounts: Collection<TimelineAccountEntity>)

    @Upsert
    abstract suspend fun upsertStatuses(statuses: Collection<TimelineStatusEntity>)

    @Upsert
    abstract suspend fun insertStatus(timelineStatusEntity: TimelineStatusEntity): Long

    @Query(
        """
SELECT s.serverId, s.url, s.timelineUserId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments, s.poll, s.card, s.muted, s.pinned, s.language, s.filtered,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot', a.createdAt as 'a_createdAt', a.limited as 'a_limited',
a.note as 'a_note',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as 'rb_emojis', rb.bot as 'rb_bot', rb.createdAt as 'rb_createdAt', rb.limited as 'rb_limited',
rb.note as 'rb_note',
svd.serverId as 'svd_serverId', svd.timelineUserId as 'svd_timelineUserId',
svd.expanded as 'svd_expanded', svd.contentShowing as 'svd_contentShowing',
svd.contentCollapsed as 'svd_contentCollapsed', svd.translationState as 'svd_translationState',
t.serverId as 't_serverId', t.timelineUserId as 't_timelineUserId', t.content as 't_content',
t.spoilerText as 't_spoilerText', t.poll as 't_poll', t.attachments as 't_attachments',
t.provider as 't_provider'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN StatusViewDataEntity svd ON (s.timelineUserId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN TranslatedStatusEntity t ON (s.timelineUserId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
WHERE s.timelineUserId = :account
ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC""",
    )
    abstract fun getStatuses(account: Long): PagingSource<Int, TimelineStatusWithAccount>

    /**
     * @return Row number (0 based) of the status with ID [statusId] for [pachliAccountId].
     *
     * @see [app.pachli.components.timeline.viewmodel.CachedTimelineViewModel.statuses]
     */
    @Query(
        """
            SELECT rownum
              FROM (
                SELECT t1.timelineUserId AS timelineUserId, t1.serverId, COUNT(t2.serverId) - 1 AS rownum
                  FROM TimelineStatusEntity t1
                  JOIN TimelineStatusEntity t2 ON t1.timelineUserId = t2.timelineUserId AND (LENGTH(t1.serverId) <= LENGTH(t2.serverId) AND t1.serverId <= t2.serverId)
                 WHERE t1.timelineUserId = :pachliAccountId
                 GROUP BY t1.serverId
                 ORDER BY length(t1.serverId) DESC, t1.serverId DESC
             )
             WHERE serverId = :statusId
        """,
    )
    abstract suspend fun getStatusRowNumber(pachliAccountId: Long, statusId: String): Int

    @Query(
        """
SELECT s.serverId, s.url, s.timelineUserId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application, s.reblogServerId,s.reblogAccountId,
s.content, s.attachments, s.poll, s.card, s.muted, s.pinned, s.language, s.filtered,
a.serverId as 'a_serverId', a.timelineUserId as 'a_timelineUserId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar',
a.emojis as 'a_emojis', a.bot as 'a_bot', a.createdAt as 'a_createdAt', a.limited as 'a_limited',
a.note as 'a_note',
rb.serverId as 'rb_serverId', rb.timelineUserId 'rb_timelineUserId',
rb.localUsername as 'rb_localUsername', rb.username as 'rb_username',
rb.displayName as 'rb_displayName', rb.url as 'rb_url', rb.avatar as 'rb_avatar',
rb.emojis as 'rb_emojis', rb.bot as 'rb_bot', rb.createdAt as 'rb_createdAt', rb.limited as 'rb_limited',
rb.note as 'rb_note',
svd.serverId as 'svd_serverId', svd.timelineUserId as 'svd_timelineUserId',
svd.expanded as 'svd_expanded', svd.contentShowing as 'svd_contentShowing',
svd.contentCollapsed as 'svd_contentCollapsed', svd.translationState as 'svd_translationState',
t.serverId as 't_serverId', t.timelineUserId as 't_timelineUserId', t.content as 't_content',
t.spoilerText as 't_spoilerText', t.poll as 't_poll', t.attachments as 't_attachments',
t.provider as 't_provider'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN StatusViewDataEntity svd ON (s.timelineUserId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN TranslatedStatusEntity t ON (s.timelineUserId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
WHERE (s.serverId = :statusId OR s.reblogServerId = :statusId)
AND s.authorServerId IS NOT NULL""",
    )
    abstract suspend fun getStatus(statusId: String): TimelineStatusWithAccount?

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId AND
        (LENGTH(serverId) < LENGTH(:maxId) OR LENGTH(serverId) == LENGTH(:maxId) AND serverId <= :maxId)
AND
(LENGTH(serverId) > LENGTH(:minId) OR LENGTH(serverId) == LENGTH(:minId) AND serverId >= :minId)
    """,
    )
    abstract suspend fun deleteRange(accountId: Long, minId: String, maxId: String): Int

    @Query(
        """UPDATE TimelineStatusEntity SET favourited = :favourited
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)""",
    )
    abstract suspend fun setFavourited(pachliAccountId: Long, statusId: String, favourited: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET bookmarked = :bookmarked
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)""",
    )
    abstract suspend fun setBookmarked(pachliAccountId: Long, statusId: String, bookmarked: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE timelineUserId = :pachliAccountId AND (serverId = :statusId OR reblogServerId = :statusId)""",
    )
    abstract suspend fun setReblogged(pachliAccountId: Long, statusId: String, reblogged: Boolean)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :pachliAccountId AND
(authorServerId = :userId OR reblogAccountId = :userId)""",
    )
    abstract suspend fun removeAllByUser(pachliAccountId: Long, userId: String)

    /**
     * Removes all statuses from the cached **home** timeline.
     *
     * Statuses that are referenced by notifications are retained, to ensure
     * they show up in the Notifications list.
     */
    @Query(
        """
        DELETE FROM TimelineStatusEntity
         WHERE timelineUserId = :accountId
           AND serverId NOT IN (
             SELECT statusServerId
               FROM NotificationEntity
              WHERE statusServerId IS NOT NULL
           )
        """,
    )
    abstract suspend fun deleteAllStatusesForAccount(accountId: Long)

    @Query("DELETE FROM StatusViewDataEntity WHERE timelineUserId = :accountId")
    abstract suspend fun removeAllStatusViewData(accountId: Long)

    @Query("DELETE FROM TranslatedStatusEntity WHERE timelineUserId = :accountId")
    abstract suspend fun removeAllTranslatedStatus(accountId: Long)

    @Query(
        """DELETE FROM TimelineStatusEntity WHERE timelineUserId = :accountId
AND serverId = :statusId""",
    )
    abstract suspend fun delete(accountId: Long, statusId: String)

    /**
     * Cleans the TimelineStatusEntity and TimelineAccountEntity tables from old entries.
     * @param accountId id of the account for which to clean tables
     * @param limit how many statuses to keep
     */
    @Transaction
    open suspend fun cleanup(accountId: Long, limit: Int) {
        cleanupStatuses(accountId, limit)
        cleanupAccounts(accountId)
        cleanupStatusViewData(accountId, limit)
        cleanupTranslatedStatus(accountId, limit)
    }

    /**
     * Deletes rows from [TimelineStatusEntity], keeping the newest [keep]
     * statuses.
     *
     * @param accountId id of the account for which to clean statuses
     * @param keep (1-based) how many statuses to keep
     */
    @Query(
        """
DELETE
  FROM TimelineStatusEntity
 WHERE timelineUserId = :accountId AND serverId IN (
    SELECT serverId FROM (
        WITH statuses(serverId) AS (
            -- Statuses that are not associated with a notification.
            -- Left join with notifications, filter to statuses where the
            -- join returns a NULL notification ID (because the status has
            -- no associated notification)
            SELECT s.serverId
              FROM TimelineStatusEntity s
         LEFT JOIN NotificationEntity n ON (s.serverId = n.statusServerId AND s.timelineUserId = n.pachliAccountId)
             WHERE n.statusServerId IS NULL AND s.timelineUserId = :accountId
        )
        -- Calculate the row number for each row, and exclude rows where
        -- the row number < limit
        SELECT t1.serverId, COUNT(t2.serverId) AS rownum
          FROM statuses t1
          LEFT JOIN statuses t2
            ON LENGTH(t1.serverId) < LENGTH(t2.serverId)
               OR (LENGTH(t1.serverId) = LENGTH(t2.serverId) AND t1.serverId < t2.serverId)
         GROUP BY t1.serverId
        HAVING rownum > :keep
    )
)
        """,
    )
    abstract suspend fun cleanupStatuses(accountId: Long, keep: Int)

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer referenced in the TimelineStatusEntity table
     * @param accountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """
DELETE
  FROM TimelineAccountEntity
 WHERE timelineUserId = :accountId
        AND serverId NOT IN (
            SELECT authorServerId
              FROM TimelineStatusEntity
             WHERE timelineUserId = :accountId
        )
        AND serverId NOT IN (
            SELECT reblogAccountId
              FROM TimelineStatusEntity
             WHERE timelineUserId = :accountId AND reblogAccountId IS NOT NULL
        )
        AND serverId NOT IN (
            SELECT accountServerId
              FROM NotificationEntity
             WHERE pachliAccountId = :accountId
        )
""",
    )
    abstract suspend fun cleanupAccounts(accountId: Long)

    /**
     * Cleans the StatusViewDataEntity table of old view data, keeping the most recent [limit]
     * entries.
     */
    @Query(
        """DELETE
             FROM StatusViewDataEntity
            WHERE timelineUserId = :accountId
              AND serverId NOT IN (
                SELECT serverId FROM StatusViewDataEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :limit
              )
        """,
    )
    abstract suspend fun cleanupStatusViewData(accountId: Long, limit: Int)

    /**
     * Cleans the TranslatedStatusEntity table of old data, keeping the most recent [limit]
     * entries.
     */
    @Query(
        """DELETE
             FROM TranslatedStatusEntity
            WHERE timelineUserId = :accountId
              AND serverId NOT IN (
                SELECT serverId FROM TranslatedStatusEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :limit
              )
        """,
    )
    abstract suspend fun cleanupTranslatedStatus(accountId: Long, limit: Int)

    @Query(
        """UPDATE TimelineStatusEntity SET poll = :poll
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""",
    )
    abstract suspend fun setVoted(accountId: Long, statusId: String, poll: Poll)

    @Upsert
    abstract suspend fun upsertStatusViewData(svd: StatusViewDataEntity)

    /**
     * @param accountId the accountId to query
     * @param serverIds the IDs of the statuses to check
     * @return Map between serverIds and any cached viewdata for those statuses
     */
    @Query(
        """SELECT *
             FROM StatusViewDataEntity
            WHERE timelineUserId = :accountId
              AND serverId IN (:serverIds)""",
    )
    abstract suspend fun getStatusViewData(
        accountId: Long,
        serverIds: List<String>,
    ): Map<
        @MapColumn(columnName = "serverId")
        String,
        StatusViewDataEntity,
        >

    @Query(
        """UPDATE TimelineStatusEntity SET pinned = :pinned
WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)""",
    )
    abstract suspend fun setPinned(accountId: Long, statusId: String, pinned: Boolean)

    @Query(
        """DELETE FROM TimelineStatusEntity
WHERE timelineUserId = :accountId AND authorServerId IN (
SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
AND timelineUserId = :accountId
)""",
    )
    abstract suspend fun deleteAllFromInstance(accountId: Long, instanceDomain: String)

    @Query("UPDATE TimelineStatusEntity SET filtered = NULL WHERE timelineUserId = :accountId AND (serverId = :statusId OR reblogServerId = :statusId)")
    abstract suspend fun clearWarning(accountId: Long, statusId: String): Int

    @Query("SELECT COUNT(*) FROM TimelineStatusEntity WHERE timelineUserId = :accountId")
    abstract suspend fun getStatusCount(accountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query("SELECT serverId FROM TimelineStatusEntity WHERE timelineUserId = :accountId ORDER BY LENGTH(serverId) DESC, serverId DESC LIMIT :count")
    abstract suspend fun getMostRecentNStatusIds(accountId: Long, count: Int): List<String>

    /** @returns The [timeline accounts][TimelineAccountEntity] known by [pachliAccountId]. */
    @Deprecated("Do not use, only present for tests")
    @Query(
        """
        SELECT *
          FROM TimelineAccountEntity
         WHERE timelineUserId = :pachliAccountId
        """,
    )
    abstract suspend fun loadTimelineAccountsForAccount(pachliAccountId: Long): List<TimelineAccountEntity>
}
