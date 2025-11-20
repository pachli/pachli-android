/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.database.model

import androidx.room.DatabaseView

/**
 * StatusIDs that are actively referenced by other entities.
 *
 * Consists of statusIds that are directly referenced from:
 *
 * - TimelineStatusEntity
 * - NotificationEntity
 * - ConversationEntity
 * - DraftEntity
 *
 * and any status IDs referenced (recursively) as replies, reblogs,
 * or quotes in those statuses.
 */
@DatabaseView(
    """
WITH RECURSIVE
-- CTEs to normalise the column names and restrict to just rows with
-- non-null references to status IDs, for use in the refId CTE.
timelineStatusId(pachliAccountId, statusId) AS (
    SELECT
        pachliAccountId,
        statusId
    FROM TimelineStatusEntity
),

notificationStatusId(pachliAccountId, statusId) AS (
    SELECT
        pachliAccountId,
        statusServerId AS statusId
    FROM NotificationEntity
    WHERE statusServerId IS NOT NULL
),

conversationStatusId(pachliAccountId, statusId) AS (
    SELECT
        pachliAccountId,
        lastStatusServerId AS statusId
    FROM ConversationEntity
    WHERE lastStatusServerId IS NOT NULL
),

draftStatusId(pachliAccountId, statusId) AS (
    SELECT
        accountId AS pachliAccountId,
        inReplyToId AS statusId
    FROM DraftEntity
    WHERE inReplyToId IS NOT NULL
),

--
-- refId is a table of all referenced statusIds. A statusId is referenced
-- if it is either (a) directly referenced by one of the CTEs above, or
-- (b) referenced by a reply, reblog, or quote in any of the statuses
-- from "a".
--
refId(pachliAccountId, statusId) AS (
    --
    -- Find all the "root" statusId. These are the IDs that
    -- are referenced by tables containing "live" data.
    --
    SELECT
        pachliAccountId,
        statusId
    FROM timelineStatusId
    UNION
    SELECT
        pachliAccountId,
        statusId
    FROM notificationStatusId
    UNION
    SELECT
        pachliAccountId,
        statusId
    FROM conversationStatusId
    UNION
    SELECT
        pachliAccountId,
        statusId
    FROM draftStatusId

    -- Recursively chase down all the references to replies, reblogs, and
    -- quotes, emitting the `inReblogId`, `inReplyToId`, or `quoteServerId`
    -- columns renamed to `statusId` as extra rows.
    UNION
    SELECT
        s.timelineUserId AS pachliAccountId,
        s.reblogServerId AS statusId
    FROM StatusEntity AS s, refId AS r
    WHERE
        s.reblogServerId IS NOT NULL
        AND s.timelineUserId = r.pachliAccountId
        AND s.serverId = r.statusID

    UNION
    SELECT
        s.timelineUserId AS pachliAccountId,
        s.inReplyToId AS statusId
    FROM StatusEntity AS s, refId AS r
    WHERE
        s.inReplyToId IS NOT NULL
        AND s.timelineUserId = r.pachliAccountId
        AND s.serverId = r.statusID

    UNION
    SELECT
        s.timelineUserId AS pachliAccountId,
        s.quoteServerId AS statusId
    FROM StatusEntity AS s, refId AS r
    WHERE
        s.quoteServerId IS NOT NULL
        AND s.timelineUserId = r.pachliAccountId
        AND s.serverId = r.statusID
)

SELECT
    pachliAccountId,
    statusId
FROM refId
""",
)
data class ReferencedStatusId(
    val pachliAccountId: Long,
    val statusId: String,
)
