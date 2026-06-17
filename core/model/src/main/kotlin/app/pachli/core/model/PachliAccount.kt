/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.model

/**
 * @property id
 * @property domain Domain of the account's server (e.g., "mastodon.social")
 * @property accessToken
 * @property clientId Client ID key used for obtaining OAuth tokens.
 * @property clientSecret Client secret key used for obtaining OAuth tokens.
 * @property isActive True if this is the "active" account.
 * @property accountId Account's remote (server) ID.
 * @property username User's local name without the leading `@` or the `@domain` portion
 * @property displayName
 * @property profilePictureUrl
 * @property profileHeaderPictureUrl
 * @property notificationsEnabled User wants Android notifications enabled for this account
 * @property notificationsMentioned
 * @property notificationsFollowed
 * @property notificationsFollowRequested
 * @property notificationsReblogged
 * @property notificationsFavorited
 * @property notificationsPolls
 * @property notificationsSubscriptions
 * @property notificationsSignUps
 * @property notificationsUpdates
 * @property notificationsReports
 * @property notificationsSeveredRelationships
 * @property notificationsModerationWarnings
 * @property notificationsQuotes
 * @property notificationsQuotedUpdates
 * @property notificationSound
 * @property notificationVibration
 * @property notificationLight
 * @property defaultPostPrivacy
 * @property defaultMediaSensitivity
 * @property defaultPostLanguage
 * @property defaultQuotePolicy
 * @property alwaysShowSensitiveMedia
 * @property alwaysOpenSpoiler True if content behind a content warning is shown
 * by default.
 * @property mediaPreviewEnabled True if the "Download media previews" preference
 * is true. This implies that media previews are shown as well as downloaded.
 * @property notificationMarkerId ID of the most recent Mastodon notification
 * that Pachli has fetched to show as an Android notification
 * @property emojis Emojis available for the account to use.
 * @property tabPreferences
 * @property notificationsFilter
 * @property oauthScopes
 * @property unifiedPushUrl
 * @property pushPubKey
 * @property pushPrivKey
 * @property pushAuth
 * @property pushServerKey
 * @property locked True if the account is locked (has to manually approve all
 * follow requests)
 * @property notificationAccountFilterNotFollowed [FilterAction] for
 * notifications from accounts this account does not follow.
 * @property notificationAccountFilterYounger30d [FilterAction] for
 * notifications from accounts younger than 30d.
 * @property notificationAccountFilterLimitedByServer [FilterAction] for
 * notifications from accounts limited by the server.
 * @property conversationAccountFilterNotFollowed [FilterAction] for
 * conversations from accounts this account does not follow.
 * @property conversationAccountFilterYounger30d [FilterAction] for
 * conversations from accounts younger than 30d.
 * @property conversationAccountFilterLimitedByServer [FilterAction] for
 * conversations from accounts limited by the server.
 * @property isBot True if the account is a bot.
 * @property identifier Unique identifier for the account.
 * @property fullName Full account name, of the form `@username@domain`
 * @property authHeader Value of the "Authorization" header for this account
 * ("Bearer $accessToken").
 * @property unifiedPushInstance UnifiedPush "instance" identifier for this account.
 * @property hasPushScope True if the account has the `push` OAuth scope.
 * @property notificationMethod The account's [AccountNotificationMethod].
 */
interface PachliAccount {
    val id: Long
    val domain: String
    val accessToken: String
    val clientId: String
    val clientSecret: String
    val isActive: Boolean
    val accountId: String
    val username: String
    val displayName: String
    val profilePictureUrl: String
    val profileHeaderPictureUrl: String
    val notificationsEnabled: Boolean
    val notificationsMentioned: Boolean
    val notificationsFollowed: Boolean
    val notificationsFollowRequested: Boolean
    val notificationsReblogged: Boolean
    val notificationsFavorited: Boolean
    val notificationsPolls: Boolean
    val notificationsSubscriptions: Boolean
    val notificationsSignUps: Boolean
    val notificationsUpdates: Boolean
    val notificationsReports: Boolean
    val notificationsSeveredRelationships: Boolean
    val notificationsModerationWarnings: Boolean
    val notificationsQuotes: Boolean
    val notificationsQuotedUpdates: Boolean
    val notificationSound: Boolean
    val notificationVibration: Boolean
    val notificationLight: Boolean
    val defaultPostPrivacy: Status.Visibility
    val defaultMediaSensitivity: Boolean
    val defaultPostLanguage: String
    val defaultQuotePolicy: AccountSource.QuotePolicy
    val alwaysShowSensitiveMedia: Boolean
    val alwaysOpenSpoiler: Boolean
    val mediaPreviewEnabled: Boolean
    val notificationMarkerId: String
    val emojis: List<Emoji>
    val tabPreferences: List<Timeline>
    val notificationsFilter: String
    val oauthScopes: String
    val unifiedPushUrl: String
    val pushPubKey: String
    val pushPrivKey: String
    val pushAuth: String
    val pushServerKey: String
    val locked: Boolean
    var notificationAccountFilterNotFollowed: FilterAction
    var notificationAccountFilterYounger30d: FilterAction
    var notificationAccountFilterLimitedByServer: FilterAction
    var conversationAccountFilterNotFollowed: FilterAction
    var conversationAccountFilterYounger30d: FilterAction
    var conversationAccountFilterLimitedByServer: FilterAction

    val isBot: Boolean

    val identifier: AccountIdentifier
        get() = AccountIdentifier(this)

    val fullName: String
        get() = "@$username@$domain"

    val authHeader: String
        get() = "Bearer $accessToken"

    val unifiedPushInstance: String
        get() = id.toString()

    val hasPushScope: Boolean
        get() = oauthScopes.contains("push")

    val notificationMethod: AccountNotificationMethod
        get() {
            if (unifiedPushUrl.isBlank()) return AccountNotificationMethod.PULL
            return AccountNotificationMethod.PUSH
        }
}
