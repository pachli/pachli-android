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

package app.pachli.core.data.model

import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AccountWarning
import app.pachli.core.model.RelationshipSeveranceEvent
import app.pachli.core.model.Status
import app.pachli.core.model.TimelineAccount

/**
 * Data necessary to show a single notification.
 *
 * See [NotificationViewData.WithStatus] for notifications that reference a
 * status.
 *
 * @property pachliAccountId
 * @property localDomain Local domain of the logged in user's account (e.g., "mastodon.social").
 * @property id Notification's server ID.
 * @property account Account that triggered the notification.
 * @property isAboutSelf True if the account that triggered the notification is the user's
 * account.
 * @property accountFilterDecision How/if to filter this notification, based on the account
 * that triggered it.
 */
sealed interface NotificationViewData {
    val pachliAccountId: Long
    val localDomain: String
    val id: String
    val account: TimelineAccount
    val isAboutSelf: Boolean
    val accountFilterDecision: AccountFilterDecision

    /** Fallback for notifications of unknown type. */
    data class UnknownNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
    ) : NotificationViewData

    /**
     * Additional data to show a notification that references a [Status].
     *
     * @property statusViewData [StatusViewData] for the referenced [Status].
     */
    sealed interface WithStatus : NotificationViewData, IStatusViewData {
        val statusViewData: StatusViewData

        /**
         * [account] posted [statusViewData] mentioning the user.
         *
         * @property statusViewData Status containing the mention.
         */
        data class MentionNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData

        /**
         * Notification that one of the user's statuses has been reblogged
         * by [account].
         *
         * @property statusViewData Status being reblogged.
         */
        data class ReblogNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData {
            override val rebloggedAvatar: String
                get() = account.avatar
        }

        /**
         * One of the user's statuses has been favourited by [account].
         *
         * @property statusViewData Status being favourited.
         */
        data class FavouriteNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData {
            override val rebloggedAvatar: String
                get() = account.avatar
        }

        /**
         * A poll the user voted in or created has ended.
         *
         * @property statusViewData Status containing the poll.
         */
        data class PollNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData

        /**
         * An [account] the user enabled notifications for has posted a status.
         *
         * @property statusViewData Newly posted status.
         */
        data class StatusNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData

        /**
         * A status the user reblogged has been edited.
         *
         * @property statusViewData Latest version of the edited status.
         */
        data class UpdateNotificationViewData(
            override val pachliAccountId: Long,
            override val localDomain: String,
            override val id: String,
            override val account: TimelineAccount,
            override val isAboutSelf: Boolean,
            override val accountFilterDecision: AccountFilterDecision,
            override val statusViewData: StatusViewData,
        ) : WithStatus, IStatusViewData by statusViewData
    }

    /** An [account] has followed the user. */
    data class FollowNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
    ) : NotificationViewData

    /** An [account] has requested to follow the user. */
    data class FollowRequestNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
    ) : NotificationViewData

    /** A new [account] has signed up. */
    data class SignupNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
    ) : NotificationViewData

    /**
     * A new moderation [report] has been filed.
     *
     * @property report Moderation report.
     */
    data class ReportNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
        val report: NotificationReportEntity,
    ) : NotificationViewData

    /**
     * Some of the user's follow relationships have been severed as a result
     * of a moderator- or user-initiated block.
     *
     * @property relationshipSeveranceEvent Details of the severed relationships.
     */
    data class SeveredRelationshipsNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
        val relationshipSeveranceEvent: RelationshipSeveranceEvent,
    ) : NotificationViewData

    /**
     * A moderator has taken action against the user's account or sent
     * a warning.
     *
     * @property accountWarning Details of the action taken.
     */
    data class ModerationWarningNotificationViewData(
        override val pachliAccountId: Long,
        override val localDomain: String,
        override val id: String,
        override val account: TimelineAccount,
        override val isAboutSelf: Boolean,
        override val accountFilterDecision: AccountFilterDecision,
        val accountWarning: AccountWarning,
    ) : NotificationViewData

    companion object
}
