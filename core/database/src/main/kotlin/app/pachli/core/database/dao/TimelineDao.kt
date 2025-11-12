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
import androidx.room.DatabaseView
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TSQ
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.model.Attachment
import app.pachli.core.model.Card
import app.pachli.core.model.Emoji
import app.pachli.core.model.HashTag
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import java.util.Date

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
SELECT s.*
  FROM TimelineStatusEntity AS t
LEFT JOIN TimelineStatusWithAccount AS s
    ON (t.pachliAccountId = :account AND (s.timelineUserId = :account AND t.statusId = s.serverId))
WHERE t.kind = :timelineKind AND t.pachliAccountId = :account
ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC
        """,
    )
    abstract fun getStatuses(
        account: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): PagingSource<Int, TimelineStatusWithAccount>

    @Query(
        """
 SELECT
     -- TimelineStatusWithAccount
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
    s.quotesCount AS 's_quotesCount',
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
    s.quoteState AS 's_quoteState',
    s.quoteServerId AS 's_quoteServerId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_serverId AS 's_a_serverId',
    s.a_timelineUserId AS 's_a_timelineUserId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_note AS 's_a_note',
    s.a_roles AS 's_a_roles',

    -- The status's reblog account (if any)
    s.rb_serverId AS 's_rb_serverId',
    s.rb_timelineUserId AS 's_rb_timelineUserId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_note AS 's_rb_note',
    s.rb_roles AS 's_rb_roles',

    -- Status view data
    s.svd_serverId AS 's_svd_serverId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_serverId AS 's_t_serverId',
    s.t_timelineUserId AS 's_t_timelineUserId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.serverId AS 'q_serverId',
    q.url AS 'q_url',
    q.timelineUserId AS 'q_timelineUserId',
    q.authorServerId AS 'q_authorServerId',
    q.inReplyToId AS 'q_inReplyToId',
    q.inReplyToAccountId AS 'q_inReplyToAccountId',
    q.createdAt AS 'q_createdAt',
    q.editedAt AS 'q_editedAt',
    q.emojis AS 'q_emojis',
    q.reblogsCount AS 'q_reblogsCount',
    q.favouritesCount AS 'q_favouritesCount',
    q.repliesCount AS 'q_repliesCount',
    q.quotesCount AS 'q_quotesCount',
    q.reblogged AS 'q_reblogged',
    q.favourited AS 'q_favourited',
    q.bookmarked AS 'q_bookmarked',
    q.sensitive AS 'q_sensitive',
    q.spoilerText AS 'q_spoilerText',
    q.visibility AS 'q_visibility',
    q.mentions AS 'q_mentions',
    q.tags AS 'q_tags',
    q.application AS 'q_application',
    q.reblogServerId AS 'q_reblogServerId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.quoteState AS 'q_quoteState',
    q.quoteServerId AS 'q_quoteServerId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_serverId AS 'q_a_serverId',
    q.a_timelineUserId AS 'q_a_timelineUserId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_note AS 'q_a_note',
    q.a_roles AS 'q_a_roles',

    -- The status's reblog account (if any)
    q.rb_serverId AS 'q_rb_serverId',
    q.rb_timelineUserId AS 'q_rb_timelineUserId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_note AS 'q_rb_note',
    q.rb_roles AS 'q_rb_roles',

    -- Status view data
    q.svd_serverId AS 'q_svd_serverId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_serverId AS 'q_t_serverId',
    q.t_timelineUserId AS 'q_t_timelineUserId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider'
  FROM TimelineStatusEntity AS t
 LEFT JOIN TimelineStatusWithAccount AS s
    ON (t.pachliAccountId = :account AND (s.timelineUserId = :account AND t.statusId = s.serverId))
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (t.pachliAccountId = :account AND (q.timelineUserId = :account AND s.quoteServerId = q.serverId))
 WHERE t.kind = :timelineKind AND t.pachliAccountId = :account
 ORDER BY LENGTH(s.serverId) DESC, s.serverId DESC
        """,
    )
    abstract fun getStatusesQ(
        account: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): PagingSource<Int, TSQ>

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
SELECT *
FROM TimelineStatusWithAccount
WHERE
    timelineUserId == :pachliAccountId
    AND (serverId = :statusId OR reblogServerId = :statusId)
    AND authorServerId IS NOT NULL
""",
    )
    // TODO: Probably doesn't need to use TimelineStatus.
    abstract suspend fun getStatus(pachliAccountId: Long, statusId: String): TimelineStatusWithAccount?

    @Query(
        """
SELECT
     -- TimelineStatusWithAccount
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
    s.quotesCount AS 's_quotesCount',
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
    s.quoteState AS 's_quoteState',
    s.quoteServerId AS 's_quoteServerId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_serverId AS 's_a_serverId',
    s.a_timelineUserId AS 's_a_timelineUserId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_note AS 's_a_note',
    s.a_roles AS 's_a_roles',
    s.a_pronouns AS 's_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_serverId AS 's_rb_serverId',
    s.rb_timelineUserId AS 's_rb_timelineUserId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_note AS 's_rb_note',
    s.rb_roles AS 's_rb_roles',
    s.rb_pronouns AS 's_rb_pronouns',

    -- Status view data
    s.svd_serverId AS 's_svd_serverId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_serverId AS 's_t_serverId',
    s.t_timelineUserId AS 's_t_timelineUserId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.serverId AS 'q_serverId',
    q.url AS 'q_url',
    q.timelineUserId AS 'q_timelineUserId',
    q.authorServerId AS 'q_authorServerId',
    q.inReplyToId AS 'q_inReplyToId',
    q.inReplyToAccountId AS 'q_inReplyToAccountId',
    q.createdAt AS 'q_createdAt',
    q.editedAt AS 'q_editedAt',
    q.emojis AS 'q_emojis',
    q.reblogsCount AS 'q_reblogsCount',
    q.favouritesCount AS 'q_favouritesCount',
    q.repliesCount AS 'q_repliesCount',
    q.quotesCount AS 'q_quotesCount',
    q.reblogged AS 'q_reblogged',
    q.favourited AS 'q_favourited',
    q.bookmarked AS 'q_bookmarked',
    q.sensitive AS 'q_sensitive',
    q.spoilerText AS 'q_spoilerText',
    q.visibility AS 'q_visibility',
    q.mentions AS 'q_mentions',
    q.tags AS 'q_tags',
    q.application AS 'q_application',
    q.reblogServerId AS 'q_reblogServerId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.quoteState AS 'q_quoteState',
    q.quoteServerId AS 'q_quoteServerId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_serverId AS 'q_a_serverId',
    q.a_timelineUserId AS 'q_a_timelineUserId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_note AS 'q_a_note',
    q.a_roles AS 'q_a_roles',
    q.a_pronouns AS 'q_a_pronouns',

    -- The status's reblog account (if any)
    q.rb_serverId AS 'q_rb_serverId',
    q.rb_timelineUserId AS 'q_rb_timelineUserId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_note AS 'q_rb_note',
    q.rb_roles AS 'q_rb_roles',
    q.rb_pronouns AS 'q_rb_pronouns',

    -- Status view data
    q.svd_serverId AS 'q_svd_serverId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_serverId AS 'q_t_serverId',
    q.t_timelineUserId AS 'q_t_timelineUserId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider'
FROM TimelineStatusWithAccount AS s
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (s.timelineUserId = q.timelineUserId AND s.quoteServerId = q.serverId)
 WHERE
    s.timelineUserId == :pachliAccountId
    AND (s.serverId = :statusId OR s.reblogServerId = :statusId)
    AND s.authorServerId IS NOT NULL
""",
    )
    abstract suspend fun getStatusQ(pachliAccountId: Long, statusId: String): TSQ?

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
WHERE pachliAccountId = :accountId
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
    UNION
    SELECT lastStatusServerId
    FROM ConversationEntity
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
    pachliAccountId = :accountId
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

@DatabaseView(
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
    s.quotesCount,
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
    s.quoteState,
    s.quoteServerId,
    s.quoteApproval,
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
    a.roles AS 'a_roles',
    a.pronouns AS 'a_pronouns',
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
    rb.roles AS 'rb_roles',
    rb.pronouns AS 'rb_pronouns',
    svd.serverId AS 'svd_serverId',
    svd.pachliAccountId AS 'svd_pachliAccountId',
    svd.expanded AS 'svd_expanded',
    svd.contentCollapsed AS 'svd_contentCollapsed',
    svd.translationState AS 'svd_translationState',
    svd.attachmentDisplayAction AS 'svd_attachmentDisplayAction',
    tr.serverId AS 't_serverId',
    tr.timelineUserId AS 't_timelineUserId',
    tr.content AS 't_content',
    tr.spoilerText AS 't_spoilerText',
    tr.poll AS 't_poll',
    tr.attachments AS 't_attachments',
    tr.provider AS 't_provider',
    reply.serverId AS 'reply_serverId',
    reply.timelineUserId AS 'reply_timelineUserId',
    reply.localUsername AS 'reply_localUsername',
    reply.username AS 'reply_username',
    reply.displayName AS 'reply_displayName',
    reply.url AS 'reply_url',
    reply.avatar AS 'reply_avatar',
    reply.emojis AS 'reply_emojis',
    reply.bot AS 'reply_bot',
    reply.createdAt AS 'reply_createdAt',
    reply.limited AS 'reply_limited',
    reply.note AS 'reply_note',
    reply.roles AS 'reply_roles',
    reply.pronouns AS 'reply_pronouns'
FROM StatusEntity AS s
LEFT JOIN TimelineAccountEntity AS a ON (s.timelineUserId = a.timelineUserId AND s.authorServerId = a.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (s.timelineUserId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (s.timelineUserId = svd.pachliAccountId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS tr
    ON (s.timelineUserId = tr.timelineUserId AND (s.serverId = tr.serverId OR s.reblogServerId = tr.serverId))
LEFT JOIN TimelineAccountEntity AS reply ON (s.timelineUserId = reply.timelineUserId AND s.inReplyToAccountId = reply.serverId)
""",
)
data class TimelineStatusWithAccount(
    @Embedded
    val status: StatusEntity,
    @Embedded(prefix = "a_")
    val account: TimelineAccountEntity,
    @Embedded(prefix = "rb_")
    val reblogAccount: TimelineAccountEntity? = null,
    @Embedded(prefix = "svd_")
    val viewData: StatusViewDataEntity? = null,
    @Embedded(prefix = "t_")
    val translatedStatus: TranslatedStatusEntity? = null,
    @Embedded(prefix = "reply_")
    val replyAccount: TimelineAccountEntity? = null,
) {
    fun toStatus(): Status {
        val attachments: List<Attachment> = status.attachments.orEmpty()
        val mentions: List<Status.Mention> = status.mentions.orEmpty()
        val tags: List<HashTag>? = status.tags
        val application = status.application
        val emojis: List<Emoji> = status.emojis.orEmpty()
        val poll: Poll? = status.poll
        val card: Card? = status.card

        val reblog = status.reblogServerId?.let { id ->
            Status(
                statusId = id,
                url = status.url,
                account = account.asModel(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                quotesCount = status.quotesCount,
                reblogged = status.reblogged,
                favourited = status.favourited,
                bookmarked = status.bookmarked,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = attachments,
                mentions = mentions,
                tags = tags,
                application = application,
                pinned = false,
                muted = status.muted,
                poll = poll,
                card = card,
                // TODO: Is this right?
                quote = null,
                quoteApproval = Status.QuoteApproval(),
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
        return if (reblog != null) {
            Status(
                statusId = status.serverId,
                // no url for reblogs
                url = null,
                account = reblogAccount!!.asModel(),
                inReplyToId = null,
                inReplyToAccountId = null,
                reblog = reblog,
                content = "",
                // lie but whatever?
                createdAt = Date(status.createdAt),
                editedAt = null,
                emojis = listOf(),
                reblogsCount = 0,
                favouritesCount = 0,
                quotesCount = 0,
                reblogged = false,
                favourited = false,
                bookmarked = false,
                sensitive = false,
                spoilerText = "",
                visibility = status.visibility,
                attachments = listOf(),
                mentions = listOf(),
                tags = listOf(),
                application = null,
                pinned = status.pinned,
                muted = status.muted,
                poll = null,
                card = null,
                // TODO: Is this right?
                quote = null,
                quoteApproval = Status.QuoteApproval(),
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        } else {
            Status(
                statusId = status.serverId,
                url = status.url,
                account = account.asModel(),
                inReplyToId = status.inReplyToId,
                inReplyToAccountId = status.inReplyToAccountId,
                reblog = null,
                content = status.content.orEmpty(),
                createdAt = Date(status.createdAt),
                editedAt = status.editedAt?.let { Date(it) },
                emojis = emojis,
                reblogsCount = status.reblogsCount,
                favouritesCount = status.favouritesCount,
                quotesCount = 0,
                reblogged = status.reblogged,
                favourited = status.favourited,
                bookmarked = status.bookmarked,
                sensitive = status.sensitive,
                spoilerText = status.spoilerText,
                visibility = status.visibility,
                attachments = attachments,
                mentions = mentions,
                tags = tags,
                application = application,
                pinned = status.pinned,
                muted = status.muted,
                poll = poll,
                card = card,
                // TODO: Is this right?
                quote = null,
                quoteApproval = Status.QuoteApproval(),
                repliesCount = status.repliesCount,
                language = status.language,
                filtered = status.filtered,
            )
        }
    }
}
