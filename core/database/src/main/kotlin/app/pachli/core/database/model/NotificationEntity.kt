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
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import java.time.Instant

// TOOD: Move to core.data? No, it's returned by dao method pagingSource()
// TOOD: Sealed class, by type? Get rid of the NotificationType enum, and the
// nullable properties which are non-null depending on the type.
/**
 * Data about a notification.
 *
 * Collates data from the different notification tables into a single type.
 *
 * @param notification The notification. * @param account Account that sent the notification.
 * @param status (optional) Status associated with the notification.
 * @param viewData (optional) Local view data for the notification.
 */
data class NotificationData(
    @Embedded val notification: NotificationEntity,
    @Embedded(prefix = "a_") val account: TimelineAccountEntity,
    @Embedded(prefix = "s_") val status: TimelineStatusWithAccount?,
    @Embedded(prefix = "nvd_") val viewData: NotificationViewDataEntity?,
    @Embedded(prefix = "report_") val report: NotificationReportEntity?,
    @Embedded(prefix = "rse_") val relationshipSeveranceEvent: NotificationRelationshipSeveranceEventEntity?,
) {
    companion object
}

/**
 * Pachli-specific viewdata for the notification.
 *
 * @param pachliAccountId
 * @param serverId Notification's remote server ID.
 * @param contentFilterAction The user's choice of [FilterAction] for
 * this notification (which may not match the inherent action if they have
 * chosen to show the notification).
 * @param accountFilterDecision The user's [AccountFilterDecision] for
 * this notification (which may not match the inherent decision if they
 * have chosen to show the notification).
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
)
@TypeConverters(Converters::class)
data class NotificationViewDataEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val contentFilterAction: FilterAction? = null,
    val accountFilterDecision: AccountFilterDecision? = null,
)

/**
 * Partial entity to update [NotificationViewDataEntity.contentFilterAction].
 */
@Entity
data class FilterActionUpdate(
    val pachliAccountId: Long,
    val serverId: String,
    val contentFilterAction: FilterAction?,
)

/**
 * Partial entity to update [NotificationViewDataEntity.accountFilterDecision].
 */
@Entity
data class AccountFilterDecisionUpdate(
    val pachliAccountId: Long,
    val serverId: String,
    val accountFilterDecision: AccountFilterDecision?,
)

/**
 * Cached copy of a notification.
 *
 * @param pachliAccountId
 * @param serverId Server's ID for this notification
 * @param type
 * @param createdAt
 * @param accountServerId ID of the account that generated this notification.
 * @param statusServerId (optional) ID of the status this notification is about.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@TypeConverters(Converters::class)
data class NotificationEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val type: Type,
    val createdAt: Instant,
    val accountServerId: String,
    val statusServerId: String?,
) {
    enum class Type {
        /** Unknown notification. */
        UNKNOWN,

        /** Someone mentioned you */
        MENTION,

        /** Someone boosted one of your statuses */
        REBLOG,

        /** Someone favourited one of your statuses */
        FAVOURITE,

        /** Someone followed you */
        FOLLOW,

        /** Someone requested to follow you */
        FOLLOW_REQUEST,

        /** A poll you have voted in or created has ended */
        POLL,

        /** Someone you enabled notifications for has posted a status */
        STATUS,

        /** Someone signed up (optionally sent to admins) */
        SIGN_UP,

        /** A status you reblogged has been updated */
        UPDATE,

        /** A new report has been filed */
        REPORT,

        /** Some of your follow relationships have been severed as a result of a moderation or block event */
        SEVERED_RELATIONSHIPS,

        ;

        companion object
    }

    companion object
}

/**
 * Data about a report associated with a notification.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = NotificationEntity::class,
                parentColumns = ["pachliAccountId", "serverId"],
                childColumns = ["pachliAccountId", "serverId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@TypeConverters(Converters::class)
data class NotificationReportEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val actionTaken: Boolean,
    val actionTakenAt: Instant?,
    val category: Category,
    val comment: String,
    val forwarded: Boolean,
    val createdAt: Instant,
    val statusIds: List<String>?,
    val ruleIds: List<String>?,
    @Embedded(prefix = "target_") val targetAccount: TimelineAccountEntity,
) {
    enum class Category {
        SPAM,
        VIOLATION,
        OTHER,
    }

    companion object
}

@Entity(
    primaryKeys = ["pachliAccountId", "serverId", "eventId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = NotificationEntity::class,
                parentColumns = ["pachliAccountId", "serverId"],
                childColumns = ["pachliAccountId", "serverId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@TypeConverters(Converters::class)
data class NotificationRelationshipSeveranceEventEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val eventId: String,
    val type: Type,
    val purged: Boolean,
    val followersCount: Int,
    val followingCount: Int,
    val createdAt: Instant,
) {
    enum class Type {
        DOMAIN_BLOCK,
        USER_DOMAIN_BLOCK,
        ACCOUNT_SUSPENSION,
        UNKNOWN,
    }

    companion object
}
