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

package app.pachli.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.network.model.Conversation
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.TimelineAccount
import com.squareup.moshi.JsonClass
import java.time.Instant
import kotlin.contracts.ExperimentalContracts

/**
 * Data to show a conversation.
 *
 * The result of joining [ConversationEntity] with the last status and
 * any other necessary data.
 */
@TypeConverters(Converters::class)
data class ConversationData(
    val pachliAccountId: Long,
    val id: String,
    val accounts: List<ConversationAccount>,
    val unread: Boolean,
    @Embedded(prefix = "s_")
    val lastStatus: TimelineStatusWithAccount,
)

/**
 * Represents a [Conversation].
 *
 * @param pachliAccountId
 * @param id Conversation ID
 * @param accounts List of [ConversationAccount] in the conversation.
 * @param unread True if the conversation is currently marked unread
 * @param lastStatusServerId Server ID of the most recent status in the conversation
 */
@Entity(
    primaryKeys = ["id", "pachliAccountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["pachliAccountId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["pachliAccountId"])],
)
@TypeConverters(Converters::class)
data class ConversationEntity(
    val pachliAccountId: Long,
    val id: String,
    val accounts: List<ConversationAccount>,
    val unread: Boolean,
    @ColumnInfo(defaultValue = "")
    val lastStatusServerId: String,
) {
    companion object {
        @OptIn(ExperimentalContracts::class)
        fun from(
            conversation: Conversation,
            pachliAccountId: Long,
        ): ConversationEntity? {
            return conversation.lastStatus?.let {
                ConversationEntity(
                    pachliAccountId = pachliAccountId,
                    id = conversation.id,
                    accounts = conversation.accounts.map { ConversationAccount.from(it) },
                    unread = conversation.unread,
                    lastStatusServerId = it.id,
                )
            }
        }
    }
}

/**
 * Participants in a [ConversationData].
 */
@JsonClass(generateAdapter = true)
data class ConversationAccount(
    val id: String,
    val localUsername: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val emojis: List<Emoji>,
    val createdAt: Instant?,
) {

    companion object {
        fun from(timelineAccount: TimelineAccount) = ConversationAccount(
            id = timelineAccount.id,
            localUsername = timelineAccount.localUsername,
            username = timelineAccount.username,
            displayName = timelineAccount.name,
            avatar = timelineAccount.avatar,
            emojis = timelineAccount.emojis.orEmpty(),
            createdAt = timelineAccount.createdAt,
        )
    }
}
