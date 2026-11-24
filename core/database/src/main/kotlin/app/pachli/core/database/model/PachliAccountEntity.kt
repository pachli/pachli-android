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

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import app.pachli.core.database.Converters
import app.pachli.core.model.AccountSource
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Notification
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
    val notificationsEnabled: Boolean,
    val notificationsMentioned: Boolean,
    val notificationsFollowed: Boolean,
    val notificationsFollowRequested: Boolean,
    val notificationsReblogged: Boolean,
    val notificationsFavorited: Boolean,
    val notificationsPolls: Boolean,
    val notificationsSubscriptions: Boolean,
    val notificationsSignUps: Boolean,
    val notificationsUpdates: Boolean,
    val notificationsReports: Boolean,
    val notificationsSeveredRelationships: Boolean,
    val notificationsModerationWarnings: Boolean,
    val notificationsQuotes: Boolean,
    val notificationsQuotedUpdates: Boolean,
    val notificationsCollectionAdd: Boolean,
    val notificationsCollectionUpdate: Boolean,
    val notificationSound: Boolean,
    val notificationVibration: Boolean,
    val notificationLight: Boolean,
    val defaultPostPrivacy: Status.Visibility,
    val defaultMediaSensitivity: Boolean = false,
    val defaultPostLanguage: String = "",
    val defaultQuotePolicy: AccountSource.QuotePolicy,
    val alwaysShowSensitiveMedia: Boolean = false,
    val alwaysOpenSpoiler: Boolean = false,
    val mediaPreviewEnabled: Boolean,
    val notificationMarkerId: String,
    val tabPreferences: List<Timeline>,
    val notificationsFilter: Set<Notification.Type>,
    val oauthScopes: String = "",
    val unifiedPushUrl: String = "",
    val pushPubKey: String = "",
    val pushPrivKey: String = "",
    val pushAuth: String = "",
    val pushServerKey: String = "",
    var notificationAccountFilterNotFollowed: FilterAction,
    var notificationAccountFilterYounger30d: FilterAction,
    var notificationAccountFilterLimitedByServer: FilterAction,
    var conversationAccountFilterNotFollowed: FilterAction,
    var conversationAccountFilterYounger30d: FilterAction,
    var conversationAccountFilterLimitedByServer: FilterAction,
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
