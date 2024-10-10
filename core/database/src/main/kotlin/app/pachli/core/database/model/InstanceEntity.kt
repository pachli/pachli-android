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

package app.pachli.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.common.extensions.MiB
import app.pachli.core.database.Converters
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2

// data class InstanceInfo(
//    @Embedded val instanceInfo: InstanceInfoEntity,
// //    @Relation(
// //        parentColumn = "instance",
// //        entityColumn = "instance",
// //    )
//    val emojis: EmojisEntity,
// )

@Entity
@TypeConverters(Converters::class)
data class InstanceInfoEntity(
    @PrimaryKey val instance: String,
    val maxPostCharacters: Int?,
    val maxPollOptions: Int?,
    val maxPollOptionLength: Int?,
    val minPollDuration: Int?,
    val maxPollDuration: Long?,
    val charactersReservedPerUrl: Int?,
    val version: String?,
    val videoSizeLimit: Long?,
    val imageSizeLimit: Long?,
    val imageMatrixLimit: Int?,
    val maxMediaAttachments: Int?,
    val maxFields: Int?,
    val maxFieldNameLength: Int?,
    val maxFieldValueLength: Int?,
    @ColumnInfo(defaultValue = "0")
    val enabledTranslation: Boolean = false,
) {
    companion object {
        private const val DEFAULT_CHARACTER_LIMIT = 500
        private const val DEFAULT_MAX_OPTION_COUNT = 4
        private const val DEFAULT_MAX_OPTION_LENGTH = 50
        private const val DEFAULT_MIN_POLL_DURATION = 300
        private const val DEFAULT_MAX_POLL_DURATION = 604800L

        private val DEFAULT_VIDEO_SIZE_LIMIT = 40L.MiB
        private val DEFAULT_IMAGE_SIZE_LIMIT = 10L.MiB
        private const val DEFAULT_IMAGE_MATRIX_LIMIT = 4096 * 4096

        // Mastodon only counts URLs as this long in terms of status character limits
        private const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        private const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4
        private const val DEFAULT_MAX_ACCOUNT_FIELDS = 4

        fun defaultForDomain(domain: String) = InstanceInfoEntity(
            instance = domain,
            maxPostCharacters = DEFAULT_CHARACTER_LIMIT,
            maxPollOptions = DEFAULT_MAX_OPTION_COUNT,
            maxPollOptionLength = DEFAULT_MAX_OPTION_LENGTH,
            minPollDuration = DEFAULT_MIN_POLL_DURATION,
            maxPollDuration = DEFAULT_MAX_POLL_DURATION,
            charactersReservedPerUrl = DEFAULT_CHARACTERS_RESERVED_PER_URL,
            videoSizeLimit = DEFAULT_VIDEO_SIZE_LIMIT,
            imageSizeLimit = DEFAULT_IMAGE_SIZE_LIMIT,
            imageMatrixLimit = DEFAULT_IMAGE_MATRIX_LIMIT,
            maxMediaAttachments = DEFAULT_MAX_MEDIA_ATTACHMENTS,
            maxFields = DEFAULT_MAX_ACCOUNT_FIELDS,
            maxFieldNameLength = null,
            maxFieldValueLength = null,
            version = "(Pachli defaults)",
        )

        fun make(domain: String, instance: InstanceV1): InstanceInfoEntity {
            return InstanceInfoEntity(
                instance = domain,
                maxPostCharacters = instance.configuration.statuses.maxCharacters ?: instance.maxTootChars ?: DEFAULT_CHARACTER_LIMIT,
                maxPollOptions = instance.configuration.polls.maxOptions,
                maxPollOptionLength = instance.configuration.polls.maxCharactersPerOption,
                minPollDuration = instance.configuration.polls.minExpiration,
                maxPollDuration = instance.configuration.polls.maxExpiration.toLong(),
                charactersReservedPerUrl = instance.configuration.statuses.charactersReservedPerUrl,
                version = instance.version,
                videoSizeLimit = instance.configuration.mediaAttachments.videoSizeLimit,
                imageSizeLimit = instance.configuration.mediaAttachments.imageSizeLimit,
                imageMatrixLimit = instance.configuration.mediaAttachments.imageMatrixLimit,
                maxMediaAttachments = instance.configuration.statuses.maxMediaAttachments,
                maxFields = instance.pleroma?.metadata?.fieldLimits?.maxFields,
                maxFieldNameLength = instance.pleroma?.metadata?.fieldLimits?.nameLength,
                maxFieldValueLength = instance.pleroma?.metadata?.fieldLimits?.valueLength,
            )
        }

        fun make(domain: String, instance: InstanceV2): InstanceInfoEntity {
            return InstanceInfoEntity(
                instance = domain,
                maxPostCharacters = instance.configuration.statuses.maxCharacters,
                maxPollOptions = instance.configuration.polls.maxOptions,
                maxPollOptionLength = instance.configuration.polls.maxCharactersPerOption,
                minPollDuration = instance.configuration.polls.minExpiration,
                maxPollDuration = instance.configuration.polls.maxExpiration,
                charactersReservedPerUrl = instance.configuration.statuses.charactersReservedPerUrl,
                version = instance.version,
                videoSizeLimit = instance.configuration.mediaAttachments.videoSizeLimit,
                imageSizeLimit = instance.configuration.mediaAttachments.imageSizeLimit,
                imageMatrixLimit = instance.configuration.mediaAttachments.imageMatrixLimit,
                maxMediaAttachments = instance.configuration.statuses.maxMediaAttachments,
                maxFields = DEFAULT_MAX_ACCOUNT_FIELDS,
                maxFieldNameLength = null,
                maxFieldValueLength = null,
                enabledTranslation = instance.configuration.translation.enabled,
            )
        }
    }
}

@Entity(
    primaryKeys = ["accountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@TypeConverters(Converters::class)
data class EmojisEntity(
    val accountId: Long,
    val emojiList: List<Emoji>,
)

// // Version of InstanceEntity without emojiList for partial updates and fetches
// // (obsolete)
// data class InstanceInfoEntity(
//    @PrimaryKey val instance: String,
//    val maximumTootCharacters: Int,
//    val maxPollOptions: Int,
//    val maxPollOptionLength: Int,
//    val minPollDuration: Int,
//    val maxPollDuration: Int,
//    val charactersReservedPerUrl: Int,
//    val version: String,
//    val videoSizeLimit: Long,
//    val imageSizeLimit: Long,
//    val imageMatrixLimit: Int,
//    val maxMediaAttachments: Int,
//    val maxFields: Int?,
//    val maxFieldNameLength: Int?,
//    val maxFieldValueLength: Int?,
// )
