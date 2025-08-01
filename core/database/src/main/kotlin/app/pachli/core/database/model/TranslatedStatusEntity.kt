/*
 * Copyright 2023 Pachli Association
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

import androidx.room.Entity
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.TranslatedAttachment
import app.pachli.core.model.TranslatedPoll
import app.pachli.core.model.Translation
import app.pachli.core.model.asNetworkModel
import app.pachli.core.model.translation.TranslatedStatus

/**
 * Translated version of a status, see https://docs.joinmastodon.org/entities/Translation/.
 *
 * There is *no* foreignkey relationship between this and [StatusEntity], as the
 * translation data is kept even if the status is deleted from the local cache (e.g., during
 * a refresh operation).
 */
@Entity(
    primaryKeys = ["serverId", "timelineUserId"],
)
@TypeConverters(Converters::class)
data class TranslatedStatusEntity(
    /** ID of the status as it appeared on the original server */
    val serverId: String,

    /** Pachli ID for the logged in user, in case there are multiple accounts per instance */
    val timelineUserId: Long,

    /** The translated text of the status (HTML), equivalent to [Status.content] */
    val content: String,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /**
     * The translated spoiler text of the status (text), if it exists, equivalent to
     * [app.pachli.core.network.model.Status.spoilerText]
     */
    val spoilerText: String,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /**
     * The translated poll (if it exists). Does not contain all the poll data, only the
     * translated text. Vote counts and other metadata has to be determined from the original
     * poll object.
     */
    val poll: TranslatedPoll?,

    // Not documented, see https://github.com/mastodon/documentation/issues/1248
    /**
     * Translated descriptions for media attachments, if any were attached. Other metadata has
     * to be determined from the original attachment.
     */
    val attachments: List<TranslatedAttachment>,

    /** The service that provided the machine translation */
    val provider: String,
)

fun Translation.toEntity(pachliAccountId: Long, serverId: String) = TranslatedStatusEntity(
    serverId = serverId,
    timelineUserId = pachliAccountId,
    content = content,
    spoilerText = spoilerText,
    poll = poll,
    attachments = attachments,
    provider = provider,
)

fun TranslatedStatus.toEntity(pachliAccountId: Long, serverId: String) = TranslatedStatusEntity(
    serverId = serverId,
    timelineUserId = pachliAccountId,
    content = content,
    spoilerText = spoilerText,
    poll = poll?.asNetworkModel(),
    attachments = attachments.map { it.asNetworkModel() },
    provider = provider,
)
