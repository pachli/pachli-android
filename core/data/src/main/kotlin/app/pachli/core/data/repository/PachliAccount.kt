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

package app.pachli.core.data.repository

import app.pachli.core.database.model.FollowingAccountEntity
import app.pachli.core.database.model.PachliAccountEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.Announcement
import app.pachli.core.model.Emoji
import app.pachli.core.model.Hashtag
import app.pachli.core.model.MastodonList
import app.pachli.core.model.Server
import app.pachli.core.model.ServerKind
import io.github.z4kn4fein.semver.Version

/**
 * A single Pachli account with all the information associated with it.
 *
 * @property id Account's unique local database ID.
 * @property entity [PachliAccountEntity] from the local database.
 * @property lists Account's lists.
 * @property emojis Server's emojis. Use [entity.emojis][PachliAccountEntity.emojis]
 * for the account's specific emojis.
 * @property server Details about the account's server.
 * @property contentFilters Account's content filters.
 * @property announcements Announcements from the account's server.
 * @property following Accounts this account is following.
 * @property followedHashtags Map of hashtags this account is following. The
 * key is the hashtag's name, without the leading `#`.
 */
// TODO: Still not sure if it's better to have one class that contains everything,
// or provide dedicated functions that return specific flows for the different
// things, parameterised by the account ID.
data class PachliAccount(
    val id: Long,
    // TODO: Should be a core.data type
    val entity: PachliAccountEntity,
    val lists: List<MastodonList>,
    val emojis: List<Emoji>,
    val server: Server,
    val contentFilters: ContentFilters,
    val announcements: List<Announcement>,
    val following: List<FollowingAccountEntity>,
    val followedHashtags: Map<String, Hashtag>,
)

fun app.pachli.core.database.model.PachliAccount.asModel() = PachliAccount(
    id = pachliAccountEntity.id,
    entity = pachliAccountEntity,
    lists = lists.orEmpty().map { it.asModel() },
    emojis = emojis?.emojiList.orEmpty(),
    server = server?.asModel() ?: Server(ServerKind.MASTODON, Version(4, 0, 0), rawVersion = "4.0.0"),
    contentFilters = contentFilters?.let { ContentFilters.from(it) } ?: ContentFilters.EMPTY,
    announcements = announcements.orEmpty().map { it.announcement },
    following = following,
    followedHashtags = followedHashtags.asModel().associateBy { it.name },
)
