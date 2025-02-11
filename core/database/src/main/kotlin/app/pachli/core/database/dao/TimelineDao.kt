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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount

@Dao
@TypeConverters(Converters::class)
abstract class TimelineDao {
    @Upsert
    abstract suspend fun upsertStatuses(entities: List<TimelineStatusEntity>)

    @Delete
    abstract suspend fun delete(entity: TimelineStatusEntity)

    @Delete
    abstract suspend fun delete(entities: List<TimelineStatusEntity>)

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Upsert
    abstract suspend fun upsertAccounts(accounts: Collection<TimelineAccountEntity>)

    @Query(
        """
SELECT
    s.serverId,
    s.url,
    s.timelineUserId,
    s.authorServerId,
    s.inReplyToId,
    s.inReplyToAccountId,
    s.createdAt,
    s.editedAt,
    s.emojis,
    s.reblogsCount,
    s.favouritesCount,
    s.repliesCount,
    s.reblogged,
    s.favourited,
    s.bookmarked,
    s.sensitive,
    s.spoilerText,
    s.visibility,
    s.mentions,
    s.tags,
    s.application,
    s.reblogServerId,
    s.reblogAccountId,
    s.content,
    s.attachments,
    s.poll,
    s.card,
    s.muted,
    s.pinned,
    s.language,
    s.filtered,
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
    rb.serverId AS 'rb_serverId',
    rb.timelineUserId AS 'rb_timelineUserId',
    rb.localUsername AS 'rb_localUsername',
    rb.username AS 'rb_username',
    rb.displayName AS 'rb_displayName',
    rb.url AS 'rb_url',
    rb.avatar AS 'rb_avatar',
    rb.emojis AS 'rb_emojis',
    rb.bot AS 'rb_bot',
    rb.createdAt AS 'rb_createdAt',
    rb.limited AS 'rb_limited',
    rb.note AS 'rb_note',
    svd.serverId AS 'svd_serverId',
    svd.timelineUserId AS 'svd_timelineUserId',
    svd.expanded AS 'svd_expanded',
    svd.contentShowing AS 'svd_contentShowing',
    svd.contentCollapsed AS 'svd_contentCollapsed',
    svd.translationState AS 'svd_translationState',
    tr.serverId AS 't_serverId',
    tr.timelineUserId AS 't_timelineUserId',
    tr.content AS 't_content',
    tr.spoilerText AS 't_spoilerText',
    tr.poll AS 't_poll',
    tr.attachments AS 't_attachments',
    tr.provider AS 't_provider'
FROM TimelineStatusEntity AS t
LEFT JOIN
    StatusEntity AS s
    ON (t.pachliAccountId = :account AND (s.timelineUserId = :account AND t.statusId = s.serverId))
LEFT JOIN TimelineAccountEntity AS a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (s.timelineUserId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS tr
    ON (s.timelineUserId = tr.timelineUserId AND (s.serverId = tr.serverId OR s.reblogServerId = tr.serverId))
WHERE t.kind = :timelineKind AND t.pachliAccountId = :account --AND s.timelineUserId = :account
ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC
""",
    )
    abstract fun getStatuses(
        account: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): PagingSource<Int, TimelineStatusWithAccount>

    /**
     * @return Row number (0 based) of the status with ID [statusId] for [pachliAccountId]
     * on [timelineKind].
     *
     * Rows are ordered newest (0) to oldest.
     *
     * @see [app.pachli.components.timeline.viewmodel.CachedTimelineViewModel.statuses]
     */
    @Query(
        """
SELECT rownum
FROM (
    WITH statuses (timelineUserId, serverId) AS (
        SELECT
            s.timelineUserId,
            s.serverId
        FROM TimelineStatusEntity AS t
        LEFT JOIN StatusEntity AS s ON (t.statusId = s.serverId)
        WHERE t.kind = :timelineKind AND t.pachliAccountId = :pachliAccountId
    )
    SELECT
        t1.timelineUserId,
        t1.serverId,
        COUNT(t2.serverId) - 1 AS rownum
    FROM statuses AS t1
    INNER JOIN
        statuses AS t2
        ON
            t1.timelineUserId = t2.timelineUserId
            AND (LENGTH(t1.serverId) <= LENGTH(t2.serverId) AND t1.serverId <= t2.serverId)
    WHERE t1.timelineUserId = :pachliAccountId
    GROUP BY t1.serverId
    ORDER BY LENGTH(t1.serverId) DESC, t1.serverId DESC
)
WHERE serverId = :statusId
""",
    )
    abstract suspend fun getStatusRowNumber(
        pachliAccountId: Long,
        statusId: String,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): Int

    @Query(
        """
SELECT
    s.serverId,
    s.url,
    s.timelineUserId,
    s.authorServerId,
    s.inReplyToId,
    s.inReplyToAccountId,
    s.createdAt,
    s.editedAt,
    s.emojis,
    s.reblogsCount,
    s.favouritesCount,
    s.repliesCount,
    s.reblogged,
    s.favourited,
    s.bookmarked,
    s.sensitive,
    s.spoilerText,
    s.visibility,
    s.mentions,
    s.tags,
    s.application,
    s.reblogServerId,
    s.reblogAccountId,
    s.content,
    s.attachments,
    s.poll,
    s.card,
    s.muted,
    s.pinned,
    s.language,
    s.filtered,
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
    rb.serverId AS 'rb_serverId',
    rb.timelineUserId AS 'rb_timelineUserId',
    rb.localUsername AS 'rb_localUsername',
    rb.username AS 'rb_username',
    rb.displayName AS 'rb_displayName',
    rb.url AS 'rb_url',
    rb.avatar AS 'rb_avatar',
    rb.emojis AS 'rb_emojis',
    rb.bot AS 'rb_bot',
    rb.createdAt AS 'rb_createdAt',
    rb.limited AS 'rb_limited',
    rb.note AS 'rb_note',
    svd.serverId AS 'svd_serverId',
    svd.timelineUserId AS 'svd_timelineUserId',
    svd.expanded AS 'svd_expanded',
    svd.contentShowing AS 'svd_contentShowing',
    svd.contentCollapsed AS 'svd_contentCollapsed',
    svd.translationState AS 'svd_translationState',
    t.serverId AS 't_serverId',
    t.timelineUserId AS 't_timelineUserId',
    t.content AS 't_content',
    t.spoilerText AS 't_spoilerText',
    t.poll AS 't_poll',
    t.attachments AS 't_attachments',
    t.provider AS 't_provider'
FROM StatusEntity AS s
LEFT JOIN TimelineAccountEntity AS a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (s.timelineUserId = svd.timelineUserId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS t
    ON (s.timelineUserId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
WHERE
    (s.serverId = :statusId OR s.reblogServerId = :statusId)
    AND s.authorServerId IS NOT NULL
""",
    )
    // TODO: Probably doesn't need to use TimelineStatus. Does need a
    // pachliAccountId
    abstract suspend fun getStatus(statusId: String): TimelineStatusWithAccount?

    @Query(
        """
DELETE
FROM StatusEntity
WHERE timelineUserId = :accountId
    AND (LENGTH(serverId) < LENGTH(:maxId) OR LENGTH(serverId) == LENGTH(:maxId) AND serverId <= :maxId)
    AND (LENGTH(serverId) > LENGTH(:minId) OR LENGTH(serverId) == LENGTH(:minId) AND serverId >= :minId)
""",
    )
    // TODO: Needs to use TimelineStatus, only used in developer tools
    abstract suspend fun deleteRange(accountId: Long, minId: String, maxId: String): Int

    @Query(
        """
DELETE
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :pachliAccountId
    AND statusId IN (
        SELECT serverId
        FROM StatusEntity
        WHERE
            timelineUserId = :pachliAccountId
            AND (authorServerId = :userId OR reblogAccountId = :userId)
    )
""",
    )
    abstract suspend fun removeAllByUser(
        pachliAccountId: Long,
        userId: String,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    )

    /**
     * Removes all statuses from [timelineKind] for [accountId]
     */
    @Query(
        """
DELETE
FROM TimelineStatusEntity
WHERE
    pachliAccountId = :accountId
    AND kind = :timelineKind
""",
    )
    abstract suspend fun deleteAllStatusesForAccountOnTimeline(accountId: Long, timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home)

    @Query(
        """
DELETE
FROM StatusViewDataEntity
WHERE timelineUserId = :accountId
""",
    )
    abstract suspend fun removeAllStatusViewData(accountId: Long)

    @Query(
        """
DELETE
FROM TranslatedStatusEntity
WHERE timelineUserId = :accountId
""",
    )
    abstract suspend fun removeAllTranslatedStatus(accountId: Long)

    /**
     * Removes cached data that is not part of any timeline.
     *
     * @param accountId id of the account for which to clean tables
     */
    @Transaction
    open suspend fun cleanup(accountId: Long) {
        cleanupStatuses(accountId)
        cleanupAccounts(accountId)
        cleanupStatusViewData(accountId)
        cleanupTranslatedStatus(accountId)
    }

    /**
     * Removes rows from [StatusEntity] that are not referenced elsewhere.
     *
     * @param accountId id of the account for which to clean statuses
     */
    @Query(
        """
DELETE
FROM StatusEntity
WHERE timelineUserId = :accountId AND serverId NOT IN (
    SELECT statusId
    FROM TimelineStatusEntity
    WHERE pachliAccountId = :accountId
    UNION
    SELECT statusServerId
    FROM NotificationEntity
    WHERE pachliAccountId = :accountId
)
""",
    )
    abstract suspend fun cleanupStatuses(accountId: Long)

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer referenced in the StatusEntity table
     * @param accountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """
DELETE
FROM TimelineAccountEntity
WHERE
    timelineUserId = :accountId
    AND serverId NOT IN (
        SELECT authorServerId
        FROM StatusEntity
        WHERE timelineUserId = :accountId
    )
    AND serverId NOT IN (
        SELECT reblogAccountId
        FROM StatusEntity
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
     * Removes rows from StatusViewDataEntity that reference statuses are that not
     * part of any timeline.
     */
    @Query(
        """
DELETE
FROM StatusViewDataEntity
WHERE
    timelineUserId = :accountId
    AND serverId NOT IN (
        SELECT statusId
        FROM TimelineStatusEntity
        WHERE pachliAccountId = :accountId
        UNION
        SELECT statusServerId
        FROM NotificationEntity
        WHERE pachliAccountId = :accountId
    )
""",
    )
    abstract suspend fun cleanupStatusViewData(accountId: Long)

    /**
     * Removes rows from TranslatedStatusEntity that reference statuses that are not
     * part of any timeline.
     */
    @Query(
        """
DELETE
FROM TranslatedStatusEntity
WHERE
    timelineUserId = :accountId
    AND serverId NOT IN (
        SELECT statusId
        FROM TimelineStatusEntity
        WHERE pachliAccountId = :accountId
        UNION
        SELECT statusServerId
        FROM NotificationEntity
        WHERE pachliAccountId = :accountId
    )
""",
    )
    abstract suspend fun cleanupTranslatedStatus(accountId: Long)

    @Query(
        """
WITH statuses (serverId) AS (
    -- IDs of statuses written by accounts from :instanceDomain
    SELECT s.serverId
    FROM StatusEntity AS s
    LEFT JOIN
        TimelineAccountEntity AS a
        ON (s.timelineUserId = a.timelineUserId AND (s.authorServerId = a.serverId OR s.reblogAccountId = a.serverId))
    WHERE s.timelineUserId = :accountId AND a.username LIKE '%@' || :instanceDomain
)

DELETE
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :accountId
    AND statusId IN (
        SELECT serverId
        FROM statuses
    )
""",
    )
    abstract suspend fun deleteAllFromInstance(
        accountId: Long,
        instanceDomain: String,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    )

    @Query(
        """
SELECT COUNT(*)
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :accountId
""",
    )
    abstract suspend fun getStatusCount(
        accountId: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        """
SELECT serverId
FROM StatusEntity
WHERE timelineUserId = :accountId
ORDER BY LENGTH(serverId) DESC, serverId DESC
LIMIT :count
""",
    )
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
