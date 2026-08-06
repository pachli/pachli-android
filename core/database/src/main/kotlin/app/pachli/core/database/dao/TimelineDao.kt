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
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.REPLACE
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import app.pachli.core.database.Converters
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithQuote

@Dao
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
abstract class TimelineDao {
    @Upsert
    abstract suspend fun upsertStatuses(entities: List<TimelineStatusEntity>)

    @Delete
    abstract suspend fun delete(entity: TimelineStatusEntity)

    @Delete
    abstract suspend fun delete(entities: List<TimelineStatusEntity>)

    @Query(
        """
SELECT *
FROM AccountEntity
WHERE pachliAccountId = :pachliAccountId AND accountId = :accountId
    """,
    )
    abstract suspend fun getAccount(pachliAccountId: Long, accountId: String): AccountEntity?

    @Query(
        """
SELECT *
FROM AccountEntity
WHERE pachliAccountId = :pachliAccountId AND accountId IN (:accountIds)
        """,
    )
    abstract suspend fun getAccounts(pachliAccountId: Long, accountIds: Collection<String>): List<AccountEntity>

    @Insert(onConflict = REPLACE)
    abstract suspend fun insertAccount(timelineAccountEntity: TimelineAccountEntity): Long

    @Upsert
    abstract suspend fun upsertAccount(accounts: AccountEntity)

    @Upsert
    abstract suspend fun upsertAccounts(accounts: Collection<AccountEntity>)

    @Upsert
    abstract suspend fun upsertTimelineAccounts(timelineAccounts: Collection<TimelineAccountEntity>)

    @Query(
        """
SELECT s.*
  FROM TimelineStatusEntity AS t
LEFT JOIN TimelineStatusWithAccount AS s
    ON (t.pachliAccountId = :account AND (s.pachliAccountId = :account AND t.statusId = s.statusId))
WHERE t.kind = :timelineKind AND t.pachliAccountId = :account
ORDER BY LENGTH(s.statusId) DESC, s.statusId DESC
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
    s.statusId AS 's_statusId',
    s.url AS 's_url',
    s.pachliAccountId AS 's_pachliAccountId',
    s.accountId AS 's_accountId',
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
    s.reblogStatusId AS 's_reblogStatusId',
    s.reblogAccountId AS 's_reblogAccountId',
    s.content AS 's_content',
    s.attachments AS 's_attachments',
    s.poll AS 's_poll',
    s.card AS 's_card',
    s.muted AS 's_muted',
    s.pinned AS 's_pinned',
    s.language AS 's_language',
    s.filtered AS 's_filtered',
    s.taggedCollections AS 's_taggedCollections',
    s.quoteState AS 's_quoteState',
    s.quoteStatusId AS 's_quoteStatusId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_accountId AS 's_a_accountId',
    s.a_pachliAccountId AS 's_a_pachliAccountId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_roles AS 's_a_roles',
    s.a_pronouns AS 's_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_accountId AS 's_rb_accountId',
    s.rb_pachliAccountId AS 's_rb_pachliAccountId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_roles AS 's_rb_roles',
    s.rb_pronouns AS 's_rb_pronouns',

    -- Status view data
    s.svd_statusId AS 's_svd_statusId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_statusId AS 's_t_statusId',
    s.t_pachliAccountId AS 's_t_pachliAccountId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Reply account
    s.reply_accountId AS 's_reply_accountId',
    s.reply_pachliAccountId AS 's_reply_pachliAccountId',
    s.reply_localUsername AS 's_reply_localUsername',
    s.reply_username AS 's_reply_username',
    s.reply_displayName AS 's_reply_displayName',
    s.reply_url AS 's_reply_url',
    s.reply_avatar AS 's_reply_avatar',
    s.reply_emojis AS 's_reply_emojis',
    s.reply_bot AS 's_reply_bot',
    s.reply_createdAt AS 's_reply_createdAt',
    s.reply_limited AS 's_reply_limited',
    s.reply_roles AS 's_reply_roles',
    s.reply_pronouns AS 's_reply_pronouns',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.statusId AS 'q_statusId',
    q.url AS 'q_url',
    q.pachliAccountId AS 'q_pachliAccountId',
    q.accountId AS 'q_accountId',
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
    q.reblogStatusId AS 'q_reblogStatusId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.taggedCollections AS 'q_taggedCollections',
    q.quoteState AS 'q_quoteState',
    q.quoteStatusId AS 'q_quoteStatusId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_accountId AS 'q_a_accountId',
    q.a_pachliAccountId AS 'q_a_pachliAccountId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_roles AS 'q_a_roles',
    q.a_pronouns AS 'q_a_pronouns',

    -- The status's reblog account (if any)
    q.rb_accountId AS 'q_rb_accountId',
    q.rb_pachliAccountId AS 'q_rb_pachliAccountId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_roles AS 'q_rb_roles',
    q.rb_pronouns AS 'q_rb_pronouns',

    -- Status view data
    q.svd_statusId AS 'q_svd_statusId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_statusId AS 'q_t_statusId',
    q.t_pachliAccountId AS 'q_t_pachliAccountId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider',

    -- Reply account
    q.reply_accountId AS 'q_reply_accountId',
    q.reply_pachliAccountId AS 'q_reply_pachliAccountId',
    q.reply_localUsername AS 'q_reply_localUsername',
    q.reply_username AS 'q_reply_username',
    q.reply_displayName AS 'q_reply_displayName',
    q.reply_url AS 'q_reply_url',
    q.reply_avatar AS 'q_reply_avatar',
    q.reply_emojis AS 'q_reply_emojis',
    q.reply_bot AS 'q_reply_bot',
    q.reply_createdAt AS 'q_reply_createdAt',
    q.reply_limited AS 'q_reply_limited',
    q.reply_roles AS 'q_reply_roles',
    q.reply_pronouns AS 'q_reply_pronouns'

  FROM TimelineStatusEntity AS t
  JOIN TimelineStatusWithAccount AS s
    ON (t.pachliAccountId = :account AND (s.pachliAccountId = :account AND t.statusId = s.statusId))
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (t.pachliAccountId = :account AND (q.pachliAccountId = :account AND s.quoteStatusId = q.statusId))
 WHERE t.kind = :timelineKind AND t.pachliAccountId = :account
 ORDER BY LENGTH(s.statusId) DESC, s.statusId DESC
        """,
    )
    abstract fun getStatusesWithQuote(
        account: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): PagingSource<Int, TimelineStatusWithQuote>

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
    WITH statuses (pachliAccountId, statusId) AS (
        SELECT
            s.pachliAccountId,
            s.statusId
        FROM TimelineStatusEntity AS t
        LEFT JOIN StatusEntity AS s ON (t.statusId = s.statusId)
        WHERE t.kind = :timelineKind AND t.pachliAccountId = :pachliAccountId
    )
    SELECT
        t1.pachliAccountId,
        t1.statusId,
        COUNT(t2.statusId) - 1 AS rownum
    FROM statuses AS t1
    INNER JOIN
        statuses AS t2
        ON
            t1.pachliAccountId = t2.pachliAccountId
            AND (LENGTH(t1.statusId) <= LENGTH(t2.statusId) AND t1.statusId <= t2.statusId)
    WHERE t1.pachliAccountId = :pachliAccountId
    GROUP BY t1.statusId
    ORDER BY LENGTH(t1.statusId) DESC, t1.statusId DESC
)
WHERE statusId = :statusId
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
     -- TimelineStatusWithAccount
    s.statusId AS 's_statusId',
    s.url AS 's_url',
    s.pachliAccountId AS 's_pachliAccountId',
    s.accountId AS 's_accountId',
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
    s.reblogStatusId AS 's_reblogStatusId',
    s.reblogAccountId AS 's_reblogAccountId',
    s.content AS 's_content',
    s.attachments AS 's_attachments',
    s.poll AS 's_poll',
    s.card AS 's_card',
    s.muted AS 's_muted',
    s.pinned AS 's_pinned',
    s.language AS 's_language',
    s.filtered AS 's_filtered',
    s.taggedCollections AS 's_taggedCollections',
    s.quoteState AS 's_quoteState',
    s.quoteStatusId AS 's_quoteStatusId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_accountId AS 's_a_accountId',
    s.a_pachliAccountId AS 's_a_pachliAccountId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_roles AS 's_a_roles',
    s.a_pronouns AS 's_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_accountId AS 's_rb_accountId',
    s.rb_pachliAccountId AS 's_rb_pachliAccountId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_roles AS 's_rb_roles',
    s.rb_pronouns AS 's_rb_pronouns',

    -- Status view data
    s.svd_statusId AS 's_svd_statusId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_statusId AS 's_t_statusId',
    s.t_pachliAccountId AS 's_t_pachliAccountId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Reply account
    s.reply_accountId AS 's_reply_accountId',
    s.reply_pachliAccountId AS 's_reply_pachliAccountId',
    s.reply_localUsername AS 's_reply_localUsername',
    s.reply_username AS 's_reply_username',
    s.reply_displayName AS 's_reply_displayName',
    s.reply_url AS 's_reply_url',
    s.reply_avatar AS 's_reply_avatar',
    s.reply_emojis AS 's_reply_emojis',
    s.reply_bot AS 's_reply_bot',
    s.reply_createdAt AS 's_reply_createdAt',
    s.reply_limited AS 's_reply_limited',
    s.reply_roles AS 's_reply_roles',
    s.reply_pronouns AS 's_reply_pronouns',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.statusId AS 'q_statusId',
    q.url AS 'q_url',
    q.pachliAccountId AS 'q_pachliAccountId',
    q.accountId AS 'q_accountId',
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
    q.reblogStatusId AS 'q_reblogStatusId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.taggedCollections AS 'q_taggedCollections',
    q.quoteState AS 'q_quoteState',
    q.quoteStatusId AS 'q_quoteStatusId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_accountId AS 'q_a_accountId',
    q.a_pachliAccountId AS 'q_a_pachliAccountId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_roles AS 'q_a_roles',
    q.a_pronouns AS 'q_a_pronouns',

    -- The status's reblog account (if any)
    q.rb_accountId AS 'q_rb_accountId',
    q.rb_pachliAccountId AS 'q_rb_pachliAccountId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_roles AS 'q_rb_roles',
    q.rb_pronouns AS 'q_rb_pronouns',

    -- Status view data
    q.svd_statusId AS 'q_svd_statusId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_statusId AS 'q_t_statusId',
    q.t_pachliAccountId AS 'q_t_pachliAccountId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider',

    -- Reply account
    q.reply_accountId AS 'q_reply_accountId',
    q.reply_pachliAccountId AS 'q_reply_pachliAccountId',
    q.reply_localUsername AS 'q_reply_localUsername',
    q.reply_username AS 'q_reply_username',
    q.reply_displayName AS 'q_reply_displayName',
    q.reply_url AS 'q_reply_url',
    q.reply_avatar AS 'q_reply_avatar',
    q.reply_emojis AS 'q_reply_emojis',
    q.reply_bot AS 'q_reply_bot',
    q.reply_createdAt AS 'q_reply_createdAt',
    q.reply_limited AS 'q_reply_limited',
    q.reply_roles AS 'q_reply_roles',
    q.reply_pronouns AS 'q_reply_pronouns'
FROM TimelineStatusWithAccount AS s
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (s.pachliAccountId = q.pachliAccountId AND s.quoteStatusId = q.statusId)
 WHERE
    s.pachliAccountId == :pachliAccountId
    AND (s.statusId = :statusId OR s.reblogStatusId = :statusId)
    AND s.accountId IS NOT NULL
""",
    )
    abstract suspend fun getStatusWithQuote(pachliAccountId: Long, statusId: String): TimelineStatusWithQuote?

    /**
     * Like [getStatusWithQuote], but only returns that status with ID [actionableStatusId]
     * (i.e., ignores boosts of [actionableStatusId]).
     */
    @Query(
        """
SELECT
     -- TimelineStatusWithAccount
    s.statusId AS 's_statusId',
    s.url AS 's_url',
    s.pachliAccountId AS 's_pachliAccountId',
    s.accountId AS 's_accountId',
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
    s.reblogStatusId AS 's_reblogStatusId',
    s.reblogAccountId AS 's_reblogAccountId',
    s.content AS 's_content',
    s.attachments AS 's_attachments',
    s.poll AS 's_poll',
    s.card AS 's_card',
    s.muted AS 's_muted',
    s.pinned AS 's_pinned',
    s.language AS 's_language',
    s.filtered AS 's_filtered',
    s.taggedCollections AS 's_taggedCollections',
    s.quoteState AS 's_quoteState',
    s.quoteStatusId AS 's_quoteStatusId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_accountId AS 's_a_accountId',
    s.a_pachliAccountId AS 's_a_pachliAccountId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_roles AS 's_a_roles',
    s.a_pronouns AS 's_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_accountId AS 's_rb_accountId',
    s.rb_pachliAccountId AS 's_rb_pachliAccountId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_roles AS 's_rb_roles',
    s.rb_pronouns AS 's_rb_pronouns',

    -- Status view data
    s.svd_statusId AS 's_svd_statusId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_statusId AS 's_t_statusId',
    s.t_pachliAccountId AS 's_t_pachliAccountId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Reply account
    s.reply_accountId AS 's_reply_accountId',
    s.reply_pachliAccountId AS 's_reply_pachliAccountId',
    s.reply_localUsername AS 's_reply_localUsername',
    s.reply_username AS 's_reply_username',
    s.reply_displayName AS 's_reply_displayName',
    s.reply_url AS 's_reply_url',
    s.reply_avatar AS 's_reply_avatar',
    s.reply_emojis AS 's_reply_emojis',
    s.reply_bot AS 's_reply_bot',
    s.reply_createdAt AS 's_reply_createdAt',
    s.reply_limited AS 's_reply_limited',
    s.reply_roles AS 's_reply_roles',
    s.reply_pronouns AS 's_reply_pronouns',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.statusId AS 'q_statusId',
    q.url AS 'q_url',
    q.pachliAccountId AS 'q_pachliAccountId',
    q.accountId AS 'q_accountId',
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
    q.reblogStatusId AS 'q_reblogStatusId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.taggedCollections AS 'q_taggedCollections',
    q.quoteState AS 'q_quoteState',
    q.quoteStatusId AS 'q_quoteStatusId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_accountId AS 'q_a_accountId',
    q.a_pachliAccountId AS 'q_a_pachliAccountId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_roles AS 'q_a_roles',
    q.a_pronouns AS 'q_a_pronouns',

    -- The status's reblog account (if any)
    q.rb_accountId AS 'q_rb_accountId',
    q.rb_pachliAccountId AS 'q_rb_pachliAccountId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_roles AS 'q_rb_roles',
    q.rb_pronouns AS 'q_rb_pronouns',

    -- Status view data
    q.svd_statusId AS 'q_svd_statusId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_statusId AS 'q_t_statusId',
    q.t_pachliAccountId AS 'q_t_pachliAccountId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider',

    -- Reply account
    q.reply_accountId AS 'q_reply_accountId',
    q.reply_pachliAccountId AS 'q_reply_pachliAccountId',
    q.reply_localUsername AS 'q_reply_localUsername',
    q.reply_username AS 'q_reply_username',
    q.reply_displayName AS 'q_reply_displayName',
    q.reply_url AS 'q_reply_url',
    q.reply_avatar AS 'q_reply_avatar',
    q.reply_emojis AS 'q_reply_emojis',
    q.reply_bot AS 'q_reply_bot',
    q.reply_createdAt AS 'q_reply_createdAt',
    q.reply_limited AS 'q_reply_limited',
    q.reply_roles AS 'q_reply_roles',
    q.reply_pronouns AS 'q_reply_pronouns'
FROM TimelineStatusWithAccount AS s
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (s.pachliAccountId = q.pachliAccountId AND s.quoteStatusId = q.statusId)
 WHERE
    s.pachliAccountId == :pachliAccountId
    AND s.statusId = :actionableStatusId
    AND s.accountId IS NOT NULL
""",
    )
    abstract suspend fun getActionableStatusQ(pachliAccountId: Long, actionableStatusId: String): TimelineStatusWithQuote?

    @Query(
        """
DELETE
FROM StatusEntity
WHERE pachliAccountId = :pachliAccountId
    AND (LENGTH(statusId) < LENGTH(:maxId) OR LENGTH(statusId) == LENGTH(:maxId) AND statusId <= :maxId)
    AND (LENGTH(statusId) > LENGTH(:minId) OR LENGTH(statusId) == LENGTH(:minId) AND statusId >= :minId)
""",
    )
    // TODO: Needs to use TimelineStatus, only used in developer tools
    abstract suspend fun deleteRange(pachliAccountId: Long, minId: String, maxId: String): Int

    @Query(
        """
DELETE
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :pachliAccountId
    AND statusId IN (
        SELECT statusId
        FROM StatusEntity
        WHERE
            pachliAccountId = :pachliAccountId
            AND (accountId = :accountId OR reblogAccountId = :accountId)
    )
""",
    )
    abstract suspend fun removeAllByAccount(
        pachliAccountId: Long,
        accountId: String,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    )

    /**
     * Removes all statuses from [timelineKind] for [pachliAccountId]
     */
    @Query(
        """
DELETE
FROM TimelineStatusEntity
WHERE
    pachliAccountId = :pachliAccountId
    AND kind = :timelineKind
""",
    )
    abstract suspend fun deleteAllStatusesForAccountOnTimeline(pachliAccountId: Long, timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home)

    @Query(
        """
DELETE
FROM StatusViewDataEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    abstract suspend fun removeAllStatusViewData(pachliAccountId: Long)

    /**
     * Removes cached data that is not part of any timeline.
     *
     * @param accountId id of the account for which to clean tables
     */
    @Transaction
    open suspend fun cleanup(pachliAccountId: Long): Long {
        val countStatus = cleanupStatuses(pachliAccountId)
        val countStatusViewData = cleanupStatusViewData(pachliAccountId)
        val countTranslatedStatus = cleanupTranslatedStatus(pachliAccountId)
        val countTimelineAccounts = cleanupTimelineAccountEntity(pachliAccountId)
        val countAccounts = cleanupAccountEntity(pachliAccountId)
        return countStatus + countStatusViewData + countTranslatedStatus + countTimelineAccounts + countAccounts + 0L
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
WHERE
    StatusEntity.pachliAccountId = :pachliAccountId
    AND NOT EXISTS (
        SELECT 1
        FROM ReferencedStatusId AS r
        WHERE
            r.pachliAccountId = :pachliAccountId
            AND StatusEntity.pachliAccountId = r.pachliAccountId
            AND StatusEntity.statusId = r.statusId
    )
""",
    )
    abstract suspend fun cleanupStatuses(pachliAccountId: Long): Int

    /**
     * Cleans the TimelineAccountEntity table from accounts that are no longer
     * referenced in other tables.
     *
     * @param pachliAccountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """
DELETE
FROM TimelineAccountEntity
WHERE
    pachliAccountId = :pachliAccountId
    AND accountId NOT IN (
        SELECT accountId
        FROM StatusEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT reblogAccountId
        FROM StatusEntity
        WHERE pachliAccountId = :pachliAccountId AND reblogAccountId IS NOT NULL
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM NotificationEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM CollectionEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM CollectionItemEntity
        WHERE pachliAccountId = :pachliAccountId
    )
""",
    )
    abstract suspend fun cleanupTimelineAccountEntity(pachliAccountId: Long): Int

    /**
     * Cleans the AccountEntity table from accounts that are no longer
     * referenced in other tables.
     *
     * @param pachliAccountId id of the user account for which to clean timeline accounts
     */
    @Query(
        """
DELETE
FROM AccountEntity
WHERE
    pachliAccountId = :pachliAccountId
    AND accountId NOT IN (
        SELECT accountId
        FROM StatusEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT reblogAccountId
        FROM StatusEntity
        WHERE pachliAccountId = :pachliAccountId AND reblogAccountId IS NOT NULL
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM NotificationEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM CollectionEntity
        WHERE pachliAccountId = :pachliAccountId
    )
    AND accountId NOT IN (
        SELECT accountId
        FROM CollectionItemEntity
        WHERE pachliAccountId = :pachliAccountId
    )
""",
    )
    abstract suspend fun cleanupAccountEntity(pachliAccountId: Long): Int

    /**
     * Removes rows from StatusViewDataEntity that reference statuses are that not
     * part of any timeline.
     */
    @Query(
        """
DELETE
FROM StatusViewDataEntity
WHERE
    StatusViewDataEntity.pachliAccountId = :pachliAccountId
    AND NOT EXISTS (
        SELECT 1
        FROM ReferencedStatusId AS r
        WHERE
            r.pachliAccountId = :pachliAccountId
            AND StatusViewDataEntity.pachliAccountId = r.pachliAccountId
            AND StatusViewDataEntity.statusId = r.statusId
    )
""",
    )
    abstract suspend fun cleanupStatusViewData(pachliAccountId: Long): Int

    /**
     * Removes rows from TranslatedStatusEntity that reference statuses that are not
     * part of any timeline.
     */
    @Query(
        """
DELETE
FROM TranslatedStatusEntity
WHERE
    TranslatedStatusEntity.pachliAccountId = :pachliAccountId
    AND NOT EXISTS (
        SELECT 1
        FROM ReferencedStatusId AS r
        WHERE
            r.pachliAccountId = :pachliAccountId
            AND TranslatedStatusEntity.pachliAccountId = r.pachliAccountId
            AND TranslatedStatusEntity.statusId = r.statusId
    )
""",
    )
    abstract suspend fun cleanupTranslatedStatus(pachliAccountId: Long): Int

    @Query(
        """
WITH statuses (statusId) AS (
    -- IDs of statuses written by accounts from :instanceDomain
    SELECT s.statusId
    FROM StatusEntity AS s
    LEFT JOIN
        TimelineAccountEntity AS a
        ON (s.pachliAccountId = a.pachliAccountId AND (s.accountId = a.accountId OR s.reblogAccountId = a.accountId))
    WHERE s.pachliAccountId = :pachliAccountId AND a.username LIKE '%@' || :instanceDomain
)

DELETE
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :pachliAccountId
    AND statusId IN (
        SELECT statusId
        FROM statuses
    )
""",
    )
    abstract suspend fun deleteAllFromInstance(
        pachliAccountId: Long,
        instanceDomain: String,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    )

    @Query(
        """
SELECT COUNT(*)
FROM TimelineStatusEntity
WHERE
    kind = :timelineKind
    AND pachliAccountId = :pachliAccountId
""",
    )
    abstract suspend fun getStatusCount(
        pachliAccountId: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        """
SELECT statusId
FROM StatusEntity
WHERE pachliAccountId = :pachliAccountId
ORDER BY LENGTH(statusId) DESC, statusId DESC
LIMIT :count
""",
    )
    abstract suspend fun getMostRecentNStatusIds(pachliAccountId: Long, count: Int): List<String>

    /** @returns The [timeline accounts][TimelineAccountEntity] known by [pachliAccountId]. */
    @Deprecated("Do not use, only present for tests")
    @Query(
        """
SELECT *
FROM TimelineAccountEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    abstract suspend fun loadTimelineAccountsForAccount(pachliAccountId: Long): List<TimelineAccountEntity>

    // Debug queries
    //
    // Used in feature.about.DatabaseFragment

    /**
     * Variant of [getStatusesWithQuote] that returns all statuses as a [List]
     * instead of a [PagingSource].
     */
    @Query(
        """
 SELECT
     -- TimelineStatusWithAccount
    s.statusId AS 's_statusId',
    s.url AS 's_url',
    s.pachliAccountId AS 's_pachliAccountId',
    s.accountId AS 's_accountId',
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
    s.reblogStatusId AS 's_reblogStatusId',
    s.reblogAccountId AS 's_reblogAccountId',
    s.content AS 's_content',
    s.attachments AS 's_attachments',
    s.poll AS 's_poll',
    s.card AS 's_card',
    s.muted AS 's_muted',
    s.pinned AS 's_pinned',
    s.language AS 's_language',
    s.filtered AS 's_filtered',
    s.taggedCollections AS 's_taggedCollections',
    s.quoteState AS 's_quoteState',
    s.quoteStatusId AS 's_quoteStatusId',
    s.quoteApproval AS 's_quoteApproval',

    -- The status' account (if any)
    s.a_accountId AS 's_a_accountId',
    s.a_pachliAccountId AS 's_a_pachliAccountId',
    s.a_localUsername AS 's_a_localUsername',
    s.a_username AS 's_a_username',
    s.a_displayName AS 's_a_displayName',
    s.a_url AS 's_a_url',
    s.a_avatar AS 's_a_avatar',
    s.a_emojis AS 's_a_emojis',
    s.a_bot AS 's_a_bot',
    s.a_createdAt AS 's_a_createdAt',
    s.a_limited AS 's_a_limited',
    s.a_roles AS 's_a_roles',
    s.a_pronouns AS 's_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_accountId AS 's_rb_accountId',
    s.rb_pachliAccountId AS 's_rb_pachliAccountId',
    s.rb_localUsername AS 's_rb_localUsername',
    s.rb_username AS 's_rb_username',
    s.rb_displayName AS 's_rb_displayName',
    s.rb_url AS 's_rb_url',
    s.rb_avatar AS 's_rb_avatar',
    s.rb_emojis AS 's_rb_emojis',
    s.rb_bot AS 's_rb_bot',
    s.rb_createdAt AS 's_rb_createdAt',
    s.rb_limited AS 's_rb_limited',
    s.rb_roles AS 's_rb_roles',
    s.rb_pronouns AS 's_rb_pronouns',

    -- Status view data
    s.svd_statusId AS 's_svd_statusId',
    s.svd_pachliAccountId AS 's_svd_pachliAccountId',
    s.svd_expanded AS 's_svd_expanded',
    s.svd_contentCollapsed AS 's_svd_contentCollapsed',
    s.svd_translationState AS 's_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_svd_attachmentDisplayAction',

    -- Translation
    s.t_statusId AS 's_t_statusId',
    s.t_pachliAccountId AS 's_t_pachliAccountId',
    s.t_content AS 's_t_content',
    s.t_spoilerText AS 's_t_spoilerText',
    s.t_poll AS 's_t_poll',
    s.t_attachments AS 's_t_attachments',
    s.t_provider AS 's_t_provider',

    -- Reply account
    s.reply_accountId AS 's_reply_accountId',
    s.reply_pachliAccountId AS 's_reply_pachliAccountId',
    s.reply_localUsername AS 's_reply_localUsername',
    s.reply_username AS 's_reply_username',
    s.reply_displayName AS 's_reply_displayName',
    s.reply_url AS 's_reply_url',
    s.reply_avatar AS 's_reply_avatar',
    s.reply_emojis AS 's_reply_emojis',
    s.reply_bot AS 's_reply_bot',
    s.reply_createdAt AS 's_reply_createdAt',
    s.reply_limited AS 's_reply_limited',
    s.reply_roles AS 's_reply_roles',
    s.reply_pronouns AS 's_reply_pronouns',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.statusId AS 'q_statusId',
    q.url AS 'q_url',
    q.pachliAccountId AS 'q_pachliAccountId',
    q.accountId AS 'q_accountId',
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
    q.reblogStatusId AS 'q_reblogStatusId',
    q.reblogAccountId AS 'q_reblogAccountId',
    q.content AS 'q_content',
    q.attachments AS 'q_attachments',
    q.poll AS 'q_poll',
    q.card AS 'q_card',
    q.muted AS 'q_muted',
    q.pinned AS 'q_pinned',
    q.language AS 'q_language',
    q.filtered AS 'q_filtered',
    q.taggedCollections AS 'q_taggedCollections',
    q.quoteState AS 'q_quoteState',
    q.quoteStatusId AS 'q_quoteStatusId',
    q.quoteApproval AS 'q_quoteApproval',

    -- The status' account (if any)
    q.a_accountId AS 'q_a_accountId',
    q.a_pachliAccountId AS 'q_a_pachliAccountId',
    q.a_localUsername AS 'q_a_localUsername',
    q.a_username AS 'q_a_username',
    q.a_displayName AS 'q_a_displayName',
    q.a_url AS 'q_a_url',
    q.a_avatar AS 'q_a_avatar',
    q.a_emojis AS 'q_a_emojis',
    q.a_bot AS 'q_a_bot',
    q.a_createdAt AS 'q_a_createdAt',
    q.a_limited AS 'q_a_limited',
    q.a_roles AS 'q_a_roles',
    q.a_pronouns AS 'q_a_pronouns',

    -- The status's reblog account (if any)
    q.rb_accountId AS 'q_rb_accountId',
    q.rb_pachliAccountId AS 'q_rb_pachliAccountId',
    q.rb_localUsername AS 'q_rb_localUsername',
    q.rb_username AS 'q_rb_username',
    q.rb_displayName AS 'q_rb_displayName',
    q.rb_url AS 'q_rb_url',
    q.rb_avatar AS 'q_rb_avatar',
    q.rb_emojis AS 'q_rb_emojis',
    q.rb_bot AS 'q_rb_bot',
    q.rb_createdAt AS 'q_rb_createdAt',
    q.rb_limited AS 'q_rb_limited',
    q.rb_roles AS 'q_rb_roles',
    q.rb_pronouns AS 'q_rb_pronouns',

    -- Status view data
    q.svd_statusId AS 'q_svd_statusId',
    q.svd_pachliAccountId AS 'q_svd_pachliAccountId',
    q.svd_expanded AS 'q_svd_expanded',
    q.svd_contentCollapsed AS 'q_svd_contentCollapsed',
    q.svd_translationState AS 'q_svd_translationState',
    q.svd_attachmentDisplayAction AS 'q_svd_attachmentDisplayAction',

    -- Translation
    q.t_statusId AS 'q_t_statusId',
    q.t_pachliAccountId AS 'q_t_pachliAccountId',
    q.t_content AS 'q_t_content',
    q.t_spoilerText AS 'q_t_spoilerText',
    q.t_poll AS 'q_t_poll',
    q.t_attachments AS 'q_t_attachments',
    q.t_provider AS 'q_t_provider',

    -- Reply account
    q.reply_accountId AS 'q_reply_accountId',
    q.reply_pachliAccountId AS 'q_reply_pachliAccountId',
    q.reply_localUsername AS 'q_reply_localUsername',
    q.reply_username AS 'q_reply_username',
    q.reply_displayName AS 'q_reply_displayName',
    q.reply_url AS 'q_reply_url',
    q.reply_avatar AS 'q_reply_avatar',
    q.reply_emojis AS 'q_reply_emojis',
    q.reply_bot AS 'q_reply_bot',
    q.reply_createdAt AS 'q_reply_createdAt',
    q.reply_limited AS 'q_reply_limited',
    q.reply_roles AS 'q_reply_roles',
    q.reply_pronouns AS 'q_reply_pronouns'
  FROM TimelineStatusEntity AS t
 LEFT JOIN TimelineStatusWithAccount AS s
    ON (t.pachliAccountId = :account AND (s.pachliAccountId = :account AND t.statusId = s.statusId))
 LEFT JOIN TimelineStatusWithAccount AS q
    ON (t.pachliAccountId = :account AND (q.pachliAccountId = :account AND s.quoteStatusId = q.statusId))
 WHERE t.kind = :timelineKind AND t.pachliAccountId = :account
 ORDER BY LENGTH(s.statusId) DESC, s.statusId DESC
        """,
    )
    abstract suspend fun debugGetStatusesWithQuote(
        account: Long,
        timelineKind: TimelineStatusEntity.Kind = TimelineStatusEntity.Kind.Home,
    ): List<TimelineStatusWithQuote>
}
