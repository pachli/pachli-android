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

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.Notification
import java.time.Instant

/**
 * Data about a notification.
 *
 * Collates data from the different notification tables into a single type.
 *
 * @property notification The notification.
 * @property account Account that sent the notification.
 * @property status (optional) Status associated with the notification.
 * @property viewData (optional) Local view data for the notification.
 */
data class NotificationData(
    @Embedded val notification: NotificationEntity,
    @Embedded(prefix = "a_") val account: TimelineAccountEntity,
    @Embedded(prefix = "s_") val status: TimelineStatusWithQuote?,
    @Embedded(prefix = "nvd_") val viewData: NotificationViewDataEntity?,
    @Embedded(prefix = "timelineCollection_") val timelineCollection: TimelineCollectionEntity?,
    @Embedded(prefix = "collectionViewData_") val collectionViewData: CollectionViewDataEntity?,
) {
    fun asModel(): Notification? {
        // TODO: Shouldn't need to return null here, as this should be restoring
        // data stored by NotificationsRemoteMediator. But there are occasional
        // reports of NPEs when `status` is asserted non-null with `!!`, so err
        // on the side of caution and return null in these and similar cases.
        return when (notification.type) {
            NotificationEntity.Type.UNKNOWN -> Notification.Unknown(
                id = notification.serverId,
                createdAt = notification.createdAt,
                account = account.asModel(),
                // TODO: This is wrong, the remoteType is not currently persisted.
                networkType = notification.type.toString(),
            )

            NotificationEntity.Type.MENTION -> status?.let {
                Notification.Mention(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.REBLOG -> status?.let {
                Notification.Reblog(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.FAVOURITE -> status?.let {
                Notification.Favourite(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.FOLLOW -> Notification.Follow(
                id = notification.serverId,
                createdAt = notification.createdAt,
                account = account.asModel(),
                note = notification.note.orEmpty(),
            )

            NotificationEntity.Type.FOLLOW_REQUEST -> Notification.FollowRequest(
                id = notification.serverId,
                createdAt = notification.createdAt,
                account = account.asModel(),
                note = notification.note.orEmpty(),
            )

            NotificationEntity.Type.POLL -> status?.let {
                Notification.Poll(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.STATUS -> status?.let {
                Notification.Status(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.SIGN_UP -> Notification.SignUp(
                id = notification.serverId,
                createdAt = notification.createdAt,
                account = account.asModel(),
            )

            NotificationEntity.Type.UPDATE -> status?.let {
                Notification.Update(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.REPORT -> notification.report?.let {
                Notification.Report(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    report = it.asModel(),
                )
            }

            NotificationEntity.Type.SEVERED_RELATIONSHIPS -> notification.relationshipSeveranceEvent?.let {
                Notification.SeveredRelationships(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    relationshipSeveranceEvent = it.asModel(),
                )
            }

            NotificationEntity.Type.MODERATION_WARNING -> notification.accountWarning?.let {
                Notification.ModerationWarning(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    accountWarning = it.asModel(),
                )
            }

            NotificationEntity.Type.QUOTE -> status?.let {
                Notification.Quote(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.QUOTED_UPDATE -> status?.let {
                Notification.QuotedUpdate(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    status = status.toStatus(),
                )
            }

            NotificationEntity.Type.COLLECTION_ADD -> timelineCollection?.let {
                Notification.CollectionAdd(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    collection = timelineCollection.asCollectionModel(),
                )
            }

            NotificationEntity.Type.COLLECTION_UPDATE -> timelineCollection?.let {
                Notification.CollectionUpdate(
                    id = notification.serverId,
                    createdAt = notification.createdAt,
                    account = account.asModel(),
                    collection = timelineCollection!!.asCollectionModel(),
                )
            }
        }
    }

    companion object
}

/**
 * Pachli-specific viewdata for the notification.
 *
 * @property pachliAccountId
 * @property serverId Notification's remote server ID.
 * @property accountFilterDecision The user's [AccountFilterDecision] for
 * this notification (which may not match the inherent decision if they
 * have chosen to show the notification).
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ]
        ),
)
@TypeConverters(Converters::class)
data class NotificationViewDataEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val accountFilterDecision: AccountFilterDecision? = null,
)

/**
 * Partial entity to update [NotificationViewDataEntity.accountFilterDecision].
 */
data class NotificationAccountFilterDecisionUpdate(
    val pachliAccountId: Long,
    val serverId: String,
    val accountFilterDecision: AccountFilterDecision?,
)

/**
 * Cached copy of a notification.
 *
 * @property pachliAccountId
 * @property serverId Server's ID for this notification.
 * @property type Notifications [NotificationEntity.Type].
 * @property createdAt When the notification was created.
 * @property accountServerId ID of the account that generated this notification.
 * @property statusServerId (optional) ID of the status this notification is about.
 * Null if the notification is not about a particular status.
 * @property note (optional) Note/bio for the account identified by accountServerId.
 * The account will be a TimelineAccount which has no note property. The note is
 * only needed for [NotificationEntity.Type.FOLLOW] and
 * [NotificationEntity.Type.FOLLOW_REQUEST], so is only persisted for those types.
 * @property collectionServerId (optional) ID of the collection contained in this
 * notification. Null if this notification does not reference a collection.
 */
@Entity(
    primaryKeys = ["pachliAccountId", "serverId"],
    foreignKeys = (
        [
            ForeignKey(
                entity = PachliAccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["pachliAccountId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
            ForeignKey(
                entity = TimelineAccountEntity::class,
                parentColumns = ["pachliAccountId", "serverId"],
                childColumns = ["pachliAccountId", "accountServerId"],
                deferred = true,
            ),
        ]
        ),
    indices = [Index(value = ["accountServerId", "pachliAccountId"])],
)
@TypeConverters(Converters::class)
data class NotificationEntity(
    val pachliAccountId: Long,
    val serverId: String,
    val type: Type,
    val createdAt: Instant,
    val accountServerId: String,
    val statusServerId: String?,
    val note: String?,

    @Embedded(prefix = "report_") val report: NotificationReport?,
    @Embedded(prefix = "rse_") val relationshipSeveranceEvent: NotificationRelationshipSeveranceEvent?,
    @Embedded(prefix = "warn_") val accountWarning: NotificationAccountWarning?,
    val collectionServerId: String?,
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

        /** A moderator has taken action against your account or has sent you a warning. */
        MODERATION_WARNING,

        /** Someone quoted one of your posts. */
        QUOTE,

        /** A post you quoted has been updated. */
        QUOTED_UPDATE,

        /** Added to a collection. */
        COLLECTION_ADD,

        /** Collection was updated. */
        COLLECTION_UPDATE,
        ;

        companion object
    }

    companion object
}

/**
 * Data about a report associated with a notification.
 *
 * @property reportId Server ID for the report
 * @property actionTaken True if action has been taken about this report.
 * @property actionTakenAt When action was taken. Null if no action has been taken.
 * @property category The [Category][NotificationReport.Category] for the report.
 * @property comment The reason for the report.
 * @property forwarded True if the report was forwarded to the remote domain.
 * @property createdAt When the report was created.
 * @property statusIds Optional list of status IDs referenced in the report. Null if no
 * statuses were listed.
 * @property ruleIds Optional list of server rule IDs referenced in the report. Null if
 * no rules were listed.
 */
@TypeConverters(Converters::class)
data class NotificationReport(
    val reportId: String,
    val actionTaken: Boolean,
    val actionTakenAt: Instant?,
    val category: Category,
    val comment: String,
    val forwarded: Boolean,
    val createdAt: Instant,
    val statusIds: List<String>?,
    val ruleIds: List<String>?,
    @Embedded(prefix = "target_")
    val targetAccount: AccountEntity,
) {
    enum class Category {
        /** Unwanted or repetitive content. */
        SPAM,

        /** A specific rule was violated. */
        VIOLATION,

        /** Some other reason. */
        OTHER,

        ;

        fun asModel() = when (this) {
            SPAM -> app.pachli.core.model.Report.Category.SPAM
            VIOLATION -> app.pachli.core.model.Report.Category.VIOLATION
            OTHER -> app.pachli.core.model.Report.Category.OTHER
        }
    }

    fun asModel() = app.pachli.core.model.Report(
        serverId = reportId,
        category = category.asModel(),
        actionTaken = actionTaken,
        actionTakenAt = actionTakenAt,
        comment = comment,
        forwarded = forwarded,
        statusIds = statusIds,
        createdAt = createdAt,
        ruleIds = ruleIds,
        targetAccount = targetAccount.asModel(),
    )

    companion object
}

/**
 * Data about a relationship severance event.
 *
 * @property eventId Server's ID for this severance event.
 * @property type The event's [Type][NotificationRelationshipSeveranceEvent.Type].
 * @property purged True if the list of severed relationships is unavailable.
 * @property followersCount How many follower relationships are broken due to this event.
 * @property followingCount How many following relationships are broken due to this event.
 * @property createdAt When the relationships were severed.
 */
@TypeConverters(Converters::class)
data class NotificationRelationshipSeveranceEvent(
    val eventId: String,
    val type: Type,
    val purged: Boolean,
    @ColumnInfo(defaultValue = "")
    val targetName: String,
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

    fun asModel() = app.pachli.core.model.RelationshipSeveranceEvent(
        id = eventId,
        type = when (type) {
            Type.DOMAIN_BLOCK -> app.pachli.core.model.RelationshipSeveranceEvent.Type.DOMAIN_BLOCK
            Type.USER_DOMAIN_BLOCK -> app.pachli.core.model.RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK
            Type.ACCOUNT_SUSPENSION -> app.pachli.core.model.RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION
            Type.UNKNOWN -> app.pachli.core.model.RelationshipSeveranceEvent.Type.UNKNOWN
        },
        purged = purged,
        targetName = targetName,
        followersCount = followersCount,
        followingCount = followingCount,
        createdAt = createdAt,
    )

    companion object
}

/**
 * Note: Should only be used as an @Embedded entity, as the primary key will not
 * distinguish warnings across different servers.
 */
data class NotificationAccountWarning(
    val accountWarningId: String,
    val text: String,
    val action: Action,
    val createdAt: Instant,
) {
    enum class Action {
        NONE,
        DISABLE,
        MARK_STATUSES_AS_SENSITIVE,
        DELETE_STATUSES,
        SILENCE,
        SUSPEND,
        UNKNOWN,
        ;

        fun asModel() = when (this) {
            NONE -> app.pachli.core.model.AccountWarning.Action.NONE
            DISABLE -> app.pachli.core.model.AccountWarning.Action.DISABLE
            MARK_STATUSES_AS_SENSITIVE -> app.pachli.core.model.AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE
            DELETE_STATUSES -> app.pachli.core.model.AccountWarning.Action.DELETE_STATUSES
            SILENCE -> app.pachli.core.model.AccountWarning.Action.SILENCE
            SUSPEND -> app.pachli.core.model.AccountWarning.Action.SUSPEND
            UNKNOWN -> app.pachli.core.model.AccountWarning.Action.UNKNOWN
        }
    }

    fun asModel() = app.pachli.core.model.AccountWarning(
        id = accountWarningId,
        action = action.asModel(),
        text = text,
        createdAt = createdAt,
    )

    companion object
}
