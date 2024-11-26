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

package app.pachli.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import java.time.Instant

enum class NotificationType {
    UNKNOWN,
    MENTION,
    REBLOG,
    FAVOURITE,
    FOLLOW,
    FOLLOW_REQUEST,
    POLL,
    STATUS,
    SIGN_UP,
    UPDATE,
    REPORT,
    SEVERED_RELATIONSHIPS,

    ;

    companion object
}

//
// 1:1 TimelineAccountEntity -> NotificationEntity
// 1:1

data class NotificationData(
    @Embedded val notification: NotificationEntity,
    @Embedded(prefix = "a_") val account: TimelineAccountEntity,
    @Embedded(prefix = "s_") val status: TimelineStatusWithAccount?,
) {
    companion object
}

@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
//    foreignKeys = (
//        [
//            ForeignKey(
//                entity = TimelineAccountEntity::class,
//                parentColumns = ["timelineUserId", "serverId"],
//                childColumns = ["pachliAccountId", "accountServerId"],
//                deferred = true,
//            ),
//            ForeignKey(
//                entity = TimelineStatusEntity::class,
//                parentColumns = ["timelineUserId", "serverId"],
//                childColumns = ["pachliAccountId", "statusServerId"],
//                deferred = true,
//            ),
//        ]
//        ),
)
@TypeConverters(Converters::class)
class NotificationEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val type: NotificationType,
    val createdAt: Instant,
    val accountServerId: String,
    val statusServerId: String?,

//    @Embedded(prefix = "source_") val account: TimelineAccountEntity,
//    @Embedded(prefix = "status_") val status: TimelineStatusWithAccount?,
//    @Embedded(prefix = "report_") val report: NotificationReportEntity?,
    // TODO:
    // RelationshipSeveranceEvent
    // AccountWarning
) {
    companion object
}

enum class NotificationReportCategory {
    SPAM,
    VIOLATION,
    OTHER,
}

@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
)
@TypeConverters(Converters::class)
class NotificationReportEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val actionTaken: Boolean,
    val actionTakenAt: Instant?,
    val category: NotificationReportCategory,
    val comment: String,
    val forwarded: Boolean,
    val createdAt: Instant,
    val statusIds: List<String>?,
    val ruleIds: List<String>?,
    @Embedded(prefix = "target_") val targetAccount: TimelineAccountEntity,
)
