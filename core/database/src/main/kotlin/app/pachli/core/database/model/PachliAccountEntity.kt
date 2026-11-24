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

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountSource
import app.pachli.core.model.FilterAction
import app.pachli.core.model.PachliAccountConfig
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
@ColumnTypeConverters(Converters::class)
data class PachliAccountEntity(
    @field:PrimaryKey(autoGenerate = true) var pachliAccountId: Long,
    val domain: String,
    val accessToken: String,
    val clientId: String,
    val clientSecret: String,
    val isActive: Boolean,
    val accountId: String,
    val username: String,
    val notificationsEnabled: Boolean = true,
    val notificationsMentioned: Boolean = true,
    val notificationsFollowed: Boolean = true,
    val notificationsFollowRequested: Boolean = false,
    val notificationsReblogged: Boolean = true,
    val notificationsFavorited: Boolean = true,
    val notificationsPolls: Boolean = true,
    val notificationsSubscriptions: Boolean = true,
    val notificationsSignUps: Boolean = true,
    val notificationsUpdates: Boolean = true,
    val notificationsReports: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsSeveredRelationships: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsModerationWarnings: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsQuotes: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsQuotedUpdates: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsCollectionAdd: Boolean = true,
    @ColumnInfo(defaultValue = "true")
    val notificationsCollectionUpdate: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val notificationLight: Boolean = true,
    val defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
    val defaultMediaSensitivity: Boolean = false,
    val defaultPostLanguage: String = "",
    @ColumnInfo(defaultValue = "NOBODY")
    val defaultQuotePolicy: AccountSource.QuotePolicy = AccountSource.QuotePolicy.NOBODY,
    val alwaysShowSensitiveMedia: Boolean = false,
    val alwaysOpenSpoiler: Boolean = false,
    val mediaPreviewEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val notificationMarkerId: String = "0",
    val tabPreferences: List<Timeline> = defaultTabs(),
    val notificationsFilter: String = "[\"follow_request\"]",
    val oauthScopes: String = "",
    val unifiedPushUrl: String = "",
    val pushPubKey: String = "",
    val pushPrivKey: String = "",
    val pushAuth: String = "",
    val pushServerKey: String = "",
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
) {
    fun asModel() = PachliAccountConfig(
        pachliAccountId = pachliAccountId,
        domain = domain,
        accessToken = accessToken,
        clientId = clientId,
        clientSecret = clientSecret,
        isActive = isActive,
        accountId = accountId,
        username = username,
        notificationsEnabled = notificationsEnabled,
        notificationsMentioned = notificationsMentioned,
        notificationsFollowed = notificationsFollowed,
        notificationsFollowRequested = notificationsFollowRequested,
        notificationsReblogged = notificationsReblogged,
        notificationsFavorited = notificationsFavorited,
        notificationsPolls = notificationsPolls,
        notificationsSubscriptions = notificationsSubscriptions,
        notificationsSignUps = notificationsSignUps,
        notificationsUpdates = notificationsUpdates,
        notificationsReports = notificationsReports,
        notificationsSeveredRelationships = notificationsSeveredRelationships,
        notificationsModerationWarnings = notificationsModerationWarnings,
        notificationsQuotes = notificationsQuotes,
        notificationsQuotedUpdates = notificationsQuotedUpdates,
        notificationsCollectionAdd = notificationsCollectionAdd,
        notificationsCollectionUpdate = notificationsCollectionUpdate,
        notificationSound = notificationSound,
        notificationVibration = notificationVibration,
        notificationLight = notificationLight,
        defaultPostPrivacy = defaultPostPrivacy,
        defaultMediaSensitivity = defaultMediaSensitivity,
        defaultPostLanguage = defaultPostLanguage,
        defaultQuotePolicy = defaultQuotePolicy,
        alwaysShowSensitiveMedia = alwaysShowSensitiveMedia,
        alwaysOpenSpoiler = alwaysOpenSpoiler,
        mediaPreviewEnabled = mediaPreviewEnabled,
        notificationMarkerId = notificationMarkerId,
        tabPreferences = tabPreferences,
        notificationsFilter = notificationsFilter,
        oauthScopes = oauthScopes,
        unifiedPushUrl = unifiedPushUrl,
        pushPubKey = pushPubKey,
        pushPrivKey = pushPrivKey,
        pushAuth = pushAuth,
        pushServerKey = pushServerKey,
        notificationAccountFilterNotFollowed = notificationAccountFilterNotFollowed,
        notificationAccountFilterYounger30d = notificationAccountFilterYounger30d,
        notificationAccountFilterLimitedByServer = notificationAccountFilterLimitedByServer,
        conversationAccountFilterNotFollowed = conversationAccountFilterNotFollowed,
        conversationAccountFilterYounger30d = conversationAccountFilterYounger30d,
        conversationAccountFilterLimitedByServer = conversationAccountFilterLimitedByServer,
    )
}

fun PachliAccountConfig.asEntity() = PachliAccountEntity(
    pachliAccountId = pachliAccountId,
    domain = domain,
    accessToken = accessToken,
    clientId = clientId,
    clientSecret = clientSecret,
    isActive = isActive,
    accountId = accountId,
    username = username,
    notificationsEnabled = notificationsEnabled,
    notificationsMentioned = notificationsMentioned,
    notificationsFollowed = notificationsFollowed,
    notificationsFollowRequested = notificationsFollowRequested,
    notificationsReblogged = notificationsReblogged,
    notificationsFavorited = notificationsFavorited,
    notificationsPolls = notificationsPolls,
    notificationsSubscriptions = notificationsSubscriptions,
    notificationsSignUps = notificationsSignUps,
    notificationsUpdates = notificationsUpdates,
    notificationsReports = notificationsReports,
    notificationsSeveredRelationships = notificationsSeveredRelationships,
    notificationsModerationWarnings = notificationsModerationWarnings,
    notificationsQuotes = notificationsQuotes,
    notificationsQuotedUpdates = notificationsQuotedUpdates,
    notificationsCollectionAdd = notificationsCollectionAdd,
    notificationsCollectionUpdate = notificationsCollectionUpdate,
    notificationSound = notificationSound,
    notificationVibration = notificationVibration,
    notificationLight = notificationLight,
    defaultPostPrivacy = defaultPostPrivacy,
    defaultMediaSensitivity = defaultMediaSensitivity,
    defaultPostLanguage = defaultPostLanguage,
    defaultQuotePolicy = defaultQuotePolicy,
    alwaysShowSensitiveMedia = alwaysShowSensitiveMedia,
    alwaysOpenSpoiler = alwaysOpenSpoiler,
    mediaPreviewEnabled = mediaPreviewEnabled,
    notificationMarkerId = notificationMarkerId,
    tabPreferences = tabPreferences,
    notificationsFilter = notificationsFilter,
    oauthScopes = oauthScopes,
    unifiedPushUrl = unifiedPushUrl,
    pushPubKey = pushPubKey,
    pushPrivKey = pushPrivKey,
    pushAuth = pushAuth,
    pushServerKey = pushServerKey,
    notificationAccountFilterNotFollowed = notificationAccountFilterNotFollowed,
    notificationAccountFilterYounger30d = notificationAccountFilterYounger30d,
    notificationAccountFilterLimitedByServer = notificationAccountFilterLimitedByServer,
    conversationAccountFilterNotFollowed = conversationAccountFilterNotFollowed,
    conversationAccountFilterYounger30d = conversationAccountFilterYounger30d,
    conversationAccountFilterLimitedByServer = conversationAccountFilterLimitedByServer,
)

fun defaultTabs() = listOf(
    Timeline.Home,
    Timeline.Notifications,
    Timeline.TrendingStatuses,
    Timeline.Conversations,
)
