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
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountSource
import app.pachli.core.model.Emoji
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline

@Entity(
    indices = [
        Index(
            value = ["domain", "accountId"],
            unique = true,
        ),
    ],
)
@TypeConverters(Converters::class)
data class PachliAccountEntity(
    @field:PrimaryKey(autoGenerate = true) override var id: Long,
    override val domain: String,
    override val accessToken: String,
    override val clientId: String,
    override val clientSecret: String,
    override val isActive: Boolean,
    override val accountId: String = "",
    override val username: String = "",
    override val displayName: String = "",
    override val profilePictureUrl: String = "",
    @ColumnInfo(defaultValue = "")
    override val profileHeaderPictureUrl: String = "",
    override val notificationsEnabled: Boolean = true,
    override val notificationsMentioned: Boolean = true,
    override val notificationsFollowed: Boolean = true,
    override val notificationsFollowRequested: Boolean = false,
    override val notificationsReblogged: Boolean = true,
    override val notificationsFavorited: Boolean = true,
    override val notificationsPolls: Boolean = true,
    override val notificationsSubscriptions: Boolean = true,
    override val notificationsSignUps: Boolean = true,
    override val notificationsUpdates: Boolean = true,
    override val notificationsReports: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsSeveredRelationships: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsModerationWarnings: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsQuotes: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsQuotedUpdates: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsCollectionAdd: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    override val notificationsCollectionUpdate: Boolean = true,
    override val notificationSound: Boolean = true,
    override val notificationVibration: Boolean = true,
    override val notificationLight: Boolean = true,
    override val defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
    override val defaultMediaSensitivity: Boolean = false,
    override val defaultPostLanguage: String = "",
    @ColumnInfo(defaultValue = "NOBODY")
    override val defaultQuotePolicy: AccountSource.QuotePolicy = AccountSource.QuotePolicy.NOBODY,
    override val alwaysShowSensitiveMedia: Boolean = false,
    override val alwaysOpenSpoiler: Boolean = false,
    override val mediaPreviewEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    override val notificationMarkerId: String = "0",
    override val emojis: List<Emoji> = emptyList(),
    override val tabPreferences: List<Timeline> = defaultTabs(),
    override val notificationsFilter: String = "[\"follow_request\"]",
    override val oauthScopes: String = "",
    override val unifiedPushUrl: String = "",
    override val pushPubKey: String = "",
    override val pushPrivKey: String = "",
    override val pushAuth: String = "",
    override val pushServerKey: String = "",
    @ColumnInfo(defaultValue = "0")
    override val locked: Boolean = false,
    @ColumnInfo(defaultValue = "NONE")
    override var notificationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    override var notificationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    override var notificationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    override var conversationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    override var conversationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    override var conversationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "0")
    override val isBot: Boolean = false,
) : app.pachli.core.model.PachliAccount

fun defaultTabs() = listOf(
    Timeline.Home,
    Timeline.Notifications,
    Timeline.TrendingStatuses,
    Timeline.Conversations,
)
