/*
 * Copyright 2018 Conny Duck
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
import app.pachli.core.database.model.ConversationAccountFilterDecisionUpdate
import app.pachli.core.database.model.ConversationContentFilterActionUpdate
import app.pachli.core.database.model.ConversationData
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.ConversationViewDataEntity

@Dao
@TypeConverters(Converters::class)
interface ConversationsDao {
    @Upsert
    suspend fun upsert(conversations: Collection<ConversationEntity>)

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsert(conversationViewData: ConversationViewDataEntity)

    @Upsert(entity = ConversationViewDataEntity::class)
    suspend fun upsert(conversationContentFilterActionUpdate: ConversationContentFilterActionUpdate)

    @Upsert(entity = ConversationViewDataEntity::class)
    suspend fun upsert(conversationAccountFilterDecisionUpdate: ConversationAccountFilterDecisionUpdate)

    @Query(
        """
DELETE
FROM ConversationEntity
WHERE id = :id AND pachliAccountId = :accountId
""",
    )
    suspend fun delete(id: String, accountId: Long)

    @Query(
        """
SELECT
    -- Conversation info
    c.pachliAccountId,
    c.id,
    c.accounts,
    c.unread,
    c.isConversationStarter,

    -- TimelineStatusWithAccount
    s.serverId AS 's_s_serverId',
    s.url AS 's_s_url',
    s.timelineUserId AS 's_s_timelineUserId',
    s.authorServerId AS 's_s_authorServerId',
    s.inReplyToId AS 's_s_inReplyToId',
    s.inReplyToAccountId AS 's_s_inReplyToAccountId',
    s.createdAt AS 's_s_createdAt',
    s.editedAt AS 's_s_editedAt',
    s.emojis AS 's_s_emojis',
    s.reblogsCount AS 's_s_reblogsCount',
    s.favouritesCount AS 's_s_favouritesCount',
    s.repliesCount AS 's_s_repliesCount',
    s.quotesCount AS 's_s_quotesCount',
    s.reblogged AS 's_s_reblogged',
    s.favourited AS 's_s_favourited',
    s.bookmarked AS 's_s_bookmarked',
    s.sensitive AS 's_s_sensitive',
    s.spoilerText AS 's_s_spoilerText',
    s.visibility AS 's_s_visibility',
    s.mentions AS 's_s_mentions',
    s.tags AS 's_s_tags',
    s.application AS 's_s_application',
    s.reblogServerId AS 's_s_reblogServerId',
    s.reblogAccountId AS 's_s_reblogAccountId',
    s.content AS 's_s_content',
    s.attachments AS 's_s_attachments',
    s.poll AS 's_s_poll',
    s.card AS 's_s_card',
    s.muted AS 's_s_muted',
    s.pinned AS 's_s_pinned',
    s.language AS 's_s_language',
    s.filtered AS 's_s_filtered',
    s.quoteState AS 's_s_quoteState',
    s.quoteServerId AS 's_s_quoteServerId',
    s.quoteApproval AS 's_s_quoteApproval',

    -- The status' account (if any)
    s.a_serverId AS 's_s_a_serverId',
    s.a_timelineUserId AS 's_s_a_timelineUserId',
    s.a_localUsername AS 's_s_a_localUsername',
    s.a_username AS 's_s_a_username',
    s.a_displayName AS 's_s_a_displayName',
    s.a_url AS 's_s_a_url',
    s.a_avatar AS 's_s_a_avatar',
    s.a_emojis AS 's_s_a_emojis',
    s.a_bot AS 's_s_a_bot',
    s.a_createdAt AS 's_s_a_createdAt',
    s.a_limited AS 's_s_a_limited',
    s.a_note AS 's_s_a_note',
    s.a_roles AS 's_s_a_roles',
    s.a_pronouns AS 's_s_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_serverId AS 's_s_rb_serverId',
    s.rb_timelineUserId AS 's_s_rb_timelineUserId',
    s.rb_localUsername AS 's_s_rb_localUsername',
    s.rb_username AS 's_s_rb_username',
    s.rb_displayName AS 's_s_rb_displayName',
    s.rb_url AS 's_s_rb_url',
    s.rb_avatar AS 's_s_rb_avatar',
    s.rb_emojis AS 's_s_rb_emojis',
    s.rb_bot AS 's_s_rb_bot',
    s.rb_createdAt AS 's_s_rb_createdAt',
    s.rb_limited AS 's_s_rb_limited',
    s.rb_note AS 's_s_rb_note',
    s.rb_roles AS 's_s_rb_roles',
    s.rb_pronouns AS 's_s_rb_pronouns',

    -- Status view data
    s.svd_serverId AS 's_s_svd_serverId',
    s.svd_pachliAccountId AS 's_s_svd_pachliAccountId',
    s.svd_expanded AS 's_s_svd_expanded',
    s.svd_contentCollapsed AS 's_s_svd_contentCollapsed',
    s.svd_translationState AS 's_s_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_s_svd_attachmentDisplayAction',

    -- Translation
    s.t_serverId AS 's_s_t_serverId',
    s.t_timelineUserId AS 's_s_t_timelineUserId',
    s.t_content AS 's_s_t_content',
    s.t_spoilerText AS 's_s_t_spoilerText',
    s.t_poll AS 's_s_t_poll',
    s.t_attachments AS 's_s_t_attachments',
    s.t_provider AS 's_s_t_provider',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.serverId AS 's_q_serverId',
    q.url AS 's_q_url',
    q.timelineUserId AS 's_q_timelineUserId',
    q.authorServerId AS 's_q_authorServerId',
    q.inReplyToId AS 's_q_inReplyToId',
    q.inReplyToAccountId AS 's_q_inReplyToAccountId',
    q.createdAt AS 's_q_createdAt',
    q.editedAt AS 's_q_editedAt',
    q.emojis AS 's_q_emojis',
    q.reblogsCount AS 's_q_reblogsCount',
    q.favouritesCount AS 's_q_favouritesCount',
    q.repliesCount AS 's_q_repliesCount',
    q.quotesCount AS 's_q_quotesCount',
    q.reblogged AS 's_q_reblogged',
    q.favourited AS 's_q_favourited',
    q.bookmarked AS 's_q_bookmarked',
    q.sensitive AS 's_q_sensitive',
    q.spoilerText AS 's_q_spoilerText',
    q.visibility AS 's_q_visibility',
    q.mentions AS 's_q_mentions',
    q.tags AS 's_q_tags',
    q.application AS 's_q_application',
    q.reblogServerId AS 's_q_reblogServerId',
    q.reblogAccountId AS 's_q_reblogAccountId',
    q.content AS 's_q_content',
    q.attachments AS 's_q_attachments',
    q.poll AS 's_q_poll',
    q.card AS 's_q_card',
    q.muted AS 's_q_muted',
    q.pinned AS 's_q_pinned',
    q.language AS 's_q_language',
    q.filtered AS 's_q_filtered',
    q.quoteState AS 's_q_quoteState',
    q.quoteServerId AS 's_q_quoteServerId',
    q.quoteApproval AS 's_q_quoteApproval',

    -- The status' account (if any)
    q.a_serverId AS 's_q_a_serverId',
    q.a_timelineUserId AS 's_q_a_timelineUserId',
    q.a_localUsername AS 's_q_a_localUsername',
    q.a_username AS 's_q_a_username',
    q.a_displayName AS 's_q_a_displayName',
    q.a_url AS 's_q_a_url',
    q.a_avatar AS 's_q_a_avatar',
    q.a_emojis AS 's_q_a_emojis',
    q.a_bot AS 's_q_a_bot',
    q.a_createdAt AS 's_q_a_createdAt',
    q.a_limited AS 's_q_a_limited',
    q.a_note AS 's_q_a_note',
    q.a_roles AS 's_q_a_roles',

    -- The status's reblog account (if any)
    q.rb_serverId AS 's_q_rb_serverId',
    q.rb_timelineUserId AS 's_q_rb_timelineUserId',
    q.rb_localUsername AS 's_q_rb_localUsername',
    q.rb_username AS 's_q_rb_username',
    q.rb_displayName AS 's_q_rb_displayName',
    q.rb_url AS 's_q_rb_url',
    q.rb_avatar AS 's_q_rb_avatar',
    q.rb_emojis AS 's_q_rb_emojis',
    q.rb_bot AS 's_q_rb_bot',
    q.rb_createdAt AS 's_q_rb_createdAt',
    q.rb_limited AS 's_q_rb_limited',
    q.rb_note AS 's_q_rb_note',
    q.rb_roles AS 's_q_rb_roles',

    -- Status view data
    q.svd_serverId AS 's_q_svd_serverId',
    q.svd_pachliAccountId AS 's_q_svd_pachliAccountId',
    q.svd_expanded AS 's_q_svd_expanded',
    q.svd_contentCollapsed AS 's_q_svd_contentCollapsed',
    q.svd_translationState AS 's_q_svd_translationState',
    q.svd_attachmentDisplayAction AS 's_q_svd_attachmentDisplayAction',

    -- Translation
    q.t_serverId AS 's_q_t_serverId',
    q.t_timelineUserId AS 's_q_t_timelineUserId',
    q.t_content AS 's_q_t_content',
    q.t_spoilerText AS 's_q_t_spoilerText',
    q.t_poll AS 's_q_t_poll',
    q.t_attachments AS 's_q_t_attachments',
    q.t_provider AS 's_q_t_provider',

    -- ConversationViewDataEntity
    cvd.pachliAccountId AS 'cvd_pachliAccountId',
    cvd.serverId AS 'cvd_serverId',
    cvd.contentFilterAction AS 'cvd_contentFilterAction',
    cvd.accountFilterDecision AS 'cvd_accountFilterDecision'
FROM ConversationEntity AS c
LEFT JOIN TimelineStatusWithAccount AS s ON (c.pachliAccountId = s.timelineUserId AND c.lastStatusServerId = s.serverId)
LEFT JOIN TimelineStatusWithAccount AS q ON (c.pachliAccountId = q.timelineUserId AND s.quoteServerId = q.serverId)
LEFT JOIN
    ConversationViewDataEntity AS cvd
    ON c.pachliAccountId = cvd.pachliAccountId AND c.id = cvd.serverId
WHERE c.pachliAccountId = :accountId
ORDER BY s.createdAt DESC
""",
    )
    fun getConversationsWithQuote(accountId: Long): PagingSource<Int, ConversationData>

    @Deprecated("Use getConversationsWithQuote, this is only for use in tests")
    @Query(
        """
SELECT *
FROM ConversationEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun loadAllForAccount(pachliAccountId: Long): List<ConversationEntity>

    @Query(
        """
DELETE
FROM ConversationEntity
WHERE pachliAccountId = :accountId
""",
    )
    suspend fun deleteForAccount(accountId: Long)

    // Debug queries
    //
    // Used in feature.about.DatabaseFragment

    /**
     * Variant of [getConversationsWithQuote] that returns all conversations as a
     * [List] instead of a [PagingSource].
     */
    @Query(
        """
SELECT
    -- Conversation info
    c.pachliAccountId,
    c.id,
    c.accounts,
    c.unread,
    c.isConversationStarter,

    -- TimelineStatusWithAccount
    s.serverId AS 's_s_serverId',
    s.url AS 's_s_url',
    s.timelineUserId AS 's_s_timelineUserId',
    s.authorServerId AS 's_s_authorServerId',
    s.inReplyToId AS 's_s_inReplyToId',
    s.inReplyToAccountId AS 's_s_inReplyToAccountId',
    s.createdAt AS 's_s_createdAt',
    s.editedAt AS 's_s_editedAt',
    s.emojis AS 's_s_emojis',
    s.reblogsCount AS 's_s_reblogsCount',
    s.favouritesCount AS 's_s_favouritesCount',
    s.repliesCount AS 's_s_repliesCount',
    s.quotesCount AS 's_s_quotesCount',
    s.reblogged AS 's_s_reblogged',
    s.favourited AS 's_s_favourited',
    s.bookmarked AS 's_s_bookmarked',
    s.sensitive AS 's_s_sensitive',
    s.spoilerText AS 's_s_spoilerText',
    s.visibility AS 's_s_visibility',
    s.mentions AS 's_s_mentions',
    s.tags AS 's_s_tags',
    s.application AS 's_s_application',
    s.reblogServerId AS 's_s_reblogServerId',
    s.reblogAccountId AS 's_s_reblogAccountId',
    s.content AS 's_s_content',
    s.attachments AS 's_s_attachments',
    s.poll AS 's_s_poll',
    s.card AS 's_s_card',
    s.muted AS 's_s_muted',
    s.pinned AS 's_s_pinned',
    s.language AS 's_s_language',
    s.filtered AS 's_s_filtered',
    s.quoteState AS 's_s_quoteState',
    s.quoteServerId AS 's_s_quoteServerId',
    s.quoteApproval AS 's_s_quoteApproval',

    -- The status' account (if any)
    s.a_serverId AS 's_s_a_serverId',
    s.a_timelineUserId AS 's_s_a_timelineUserId',
    s.a_localUsername AS 's_s_a_localUsername',
    s.a_username AS 's_s_a_username',
    s.a_displayName AS 's_s_a_displayName',
    s.a_url AS 's_s_a_url',
    s.a_avatar AS 's_s_a_avatar',
    s.a_emojis AS 's_s_a_emojis',
    s.a_bot AS 's_s_a_bot',
    s.a_createdAt AS 's_s_a_createdAt',
    s.a_limited AS 's_s_a_limited',
    s.a_note AS 's_s_a_note',
    s.a_roles AS 's_s_a_roles',
    s.a_pronouns AS 's_s_a_pronouns',

    -- The status's reblog account (if any)
    s.rb_serverId AS 's_s_rb_serverId',
    s.rb_timelineUserId AS 's_s_rb_timelineUserId',
    s.rb_localUsername AS 's_s_rb_localUsername',
    s.rb_username AS 's_s_rb_username',
    s.rb_displayName AS 's_s_rb_displayName',
    s.rb_url AS 's_s_rb_url',
    s.rb_avatar AS 's_s_rb_avatar',
    s.rb_emojis AS 's_s_rb_emojis',
    s.rb_bot AS 's_s_rb_bot',
    s.rb_createdAt AS 's_s_rb_createdAt',
    s.rb_limited AS 's_s_rb_limited',
    s.rb_note AS 's_s_rb_note',
    s.rb_roles AS 's_s_rb_roles',
    s.rb_pronouns AS 's_s_rb_pronouns',

    -- Status view data
    s.svd_serverId AS 's_s_svd_serverId',
    s.svd_pachliAccountId AS 's_s_svd_pachliAccountId',
    s.svd_expanded AS 's_s_svd_expanded',
    s.svd_contentCollapsed AS 's_s_svd_contentCollapsed',
    s.svd_translationState AS 's_s_svd_translationState',
    s.svd_attachmentDisplayAction AS 's_s_svd_attachmentDisplayAction',

    -- Translation
    s.t_serverId AS 's_s_t_serverId',
    s.t_timelineUserId AS 's_s_t_timelineUserId',
    s.t_content AS 's_s_t_content',
    s.t_spoilerText AS 's_s_t_spoilerText',
    s.t_poll AS 's_s_t_poll',
    s.t_attachments AS 's_s_t_attachments',
    s.t_provider AS 's_s_t_provider',

    -- Quoted status (if any)
    -- TimelineStatusWithAccount
    q.serverId AS 's_q_serverId',
    q.url AS 's_q_url',
    q.timelineUserId AS 's_q_timelineUserId',
    q.authorServerId AS 's_q_authorServerId',
    q.inReplyToId AS 's_q_inReplyToId',
    q.inReplyToAccountId AS 's_q_inReplyToAccountId',
    q.createdAt AS 's_q_createdAt',
    q.editedAt AS 's_q_editedAt',
    q.emojis AS 's_q_emojis',
    q.reblogsCount AS 's_q_reblogsCount',
    q.favouritesCount AS 's_q_favouritesCount',
    q.repliesCount AS 's_q_repliesCount',
    q.quotesCount AS 's_q_quotesCount',
    q.reblogged AS 's_q_reblogged',
    q.favourited AS 's_q_favourited',
    q.bookmarked AS 's_q_bookmarked',
    q.sensitive AS 's_q_sensitive',
    q.spoilerText AS 's_q_spoilerText',
    q.visibility AS 's_q_visibility',
    q.mentions AS 's_q_mentions',
    q.tags AS 's_q_tags',
    q.application AS 's_q_application',
    q.reblogServerId AS 's_q_reblogServerId',
    q.reblogAccountId AS 's_q_reblogAccountId',
    q.content AS 's_q_content',
    q.attachments AS 's_q_attachments',
    q.poll AS 's_q_poll',
    q.card AS 's_q_card',
    q.muted AS 's_q_muted',
    q.pinned AS 's_q_pinned',
    q.language AS 's_q_language',
    q.filtered AS 's_q_filtered',
    q.quoteState AS 's_q_quoteState',
    q.quoteServerId AS 's_q_quoteServerId',
    q.quoteApproval AS 's_q_quoteApproval',

    -- The status' account (if any)
    q.a_serverId AS 's_q_a_serverId',
    q.a_timelineUserId AS 's_q_a_timelineUserId',
    q.a_localUsername AS 's_q_a_localUsername',
    q.a_username AS 's_q_a_username',
    q.a_displayName AS 's_q_a_displayName',
    q.a_url AS 's_q_a_url',
    q.a_avatar AS 's_q_a_avatar',
    q.a_emojis AS 's_q_a_emojis',
    q.a_bot AS 's_q_a_bot',
    q.a_createdAt AS 's_q_a_createdAt',
    q.a_limited AS 's_q_a_limited',
    q.a_note AS 's_q_a_note',
    q.a_roles AS 's_q_a_roles',

    -- The status's reblog account (if any)
    q.rb_serverId AS 's_q_rb_serverId',
    q.rb_timelineUserId AS 's_q_rb_timelineUserId',
    q.rb_localUsername AS 's_q_rb_localUsername',
    q.rb_username AS 's_q_rb_username',
    q.rb_displayName AS 's_q_rb_displayName',
    q.rb_url AS 's_q_rb_url',
    q.rb_avatar AS 's_q_rb_avatar',
    q.rb_emojis AS 's_q_rb_emojis',
    q.rb_bot AS 's_q_rb_bot',
    q.rb_createdAt AS 's_q_rb_createdAt',
    q.rb_limited AS 's_q_rb_limited',
    q.rb_note AS 's_q_rb_note',
    q.rb_roles AS 's_q_rb_roles',

    -- Status view data
    q.svd_serverId AS 's_q_svd_serverId',
    q.svd_pachliAccountId AS 's_q_svd_pachliAccountId',
    q.svd_expanded AS 's_q_svd_expanded',
    q.svd_contentCollapsed AS 's_q_svd_contentCollapsed',
    q.svd_translationState AS 's_q_svd_translationState',
    q.svd_attachmentDisplayAction AS 's_q_svd_attachmentDisplayAction',

    -- Translation
    q.t_serverId AS 's_q_t_serverId',
    q.t_timelineUserId AS 's_q_t_timelineUserId',
    q.t_content AS 's_q_t_content',
    q.t_spoilerText AS 's_q_t_spoilerText',
    q.t_poll AS 's_q_t_poll',
    q.t_attachments AS 's_q_t_attachments',
    q.t_provider AS 's_q_t_provider',

    -- ConversationViewDataEntity
    cvd.pachliAccountId AS 'cvd_pachliAccountId',
    cvd.serverId AS 'cvd_serverId',
    cvd.contentFilterAction AS 'cvd_contentFilterAction',
    cvd.accountFilterDecision AS 'cvd_accountFilterDecision'
FROM ConversationEntity AS c
LEFT JOIN TimelineStatusWithAccount AS s ON (c.pachliAccountId = s.timelineUserId AND c.lastStatusServerId = s.serverId)
LEFT JOIN TimelineStatusWithAccount AS q ON (c.pachliAccountId = q.timelineUserId AND s.quoteServerId = q.serverId)
LEFT JOIN
    ConversationViewDataEntity AS cvd
    ON c.pachliAccountId = cvd.pachliAccountId AND c.id = cvd.serverId
WHERE c.pachliAccountId = :accountId
ORDER BY s.createdAt DESC
""",
    )
    fun debugGetConversationsWithQuote(accountId: Long): List<ConversationData>
}
