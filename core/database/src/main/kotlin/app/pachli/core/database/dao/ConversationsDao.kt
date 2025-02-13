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
import androidx.room.Upsert
import app.pachli.core.database.model.ConversationData
import app.pachli.core.database.model.ConversationEntity

@Dao
interface ConversationsDao {
    @Upsert
    suspend fun upsert(conversations: Collection<ConversationEntity>)

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

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

    -- The last status
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
    t.provider AS 's_t_provider'

FROM ConversationEntity AS c
LEFT JOIN StatusEntity AS s ON (c.pachliAccountId = s.timelineUserId AND c.lastStatusServerId = s.serverId)
LEFT JOIN TimelineAccountEntity AS sa ON (c.pachliAccountId = sa.timelineUserId AND s.authorServerId = sa.serverId)
LEFT JOIN TimelineAccountEntity AS rb ON (c.pachliAccountId = rb.timelineUserId AND s.reblogAccountId = rb.serverId)
LEFT JOIN
    StatusViewDataEntity AS svd
    ON (c.pachliAccountId = svd.pachliAccountId AND (s.serverId = svd.serverId OR s.reblogServerId = svd.serverId))
LEFT JOIN
    TranslatedStatusEntity AS t
    ON (c.pachliAccountId = t.timelineUserId AND (s.serverId = t.serverId OR s.reblogServerId = t.serverId))
WHERE c.pachliAccountId = :accountId
ORDER BY s.createdAt DESC
""",
    )
    fun conversationsForAccount(accountId: Long): PagingSource<Int, ConversationData>

    @Deprecated("Use conversationsForAccount, this is only for use in tests")
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
}
