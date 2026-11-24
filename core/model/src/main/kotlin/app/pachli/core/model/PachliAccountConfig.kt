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
 * @property pachliAccountId
 * @property domain Domain of the account's server (e.g., "mastodon.social")
 * @property accessToken
 * @property clientId Client ID key used for obtaining OAuth tokens.
 * @property clientSecret Client secret key used for obtaining OAuth tokens.
 * @property isActive True if this is the "active" account.
 * @property accountId Account's remote (server) ID.
 * @property username User's local name without the leading `@` or the `@domain` portion
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
 * @property notificationsCollectionAdd User wants Android notifications when added
 * to a collection.
 * @property notificationsCollectionUpdate User wants Android notifications when a
 * collection they're in is updated.
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
 * @property tabPreferences
 * @property notificationsFilter
 * @property oauthScopes
 * @property unifiedPushUrl
 * @property pushPubKey
 * @property pushPrivKey
 * @property pushAuth
 * @property pushServerKey
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
 * @property identifier Unique identifier for the account.
 * @property handle Full account name, of the form `@username@domain`
 * @property authHeader Value of the "Authorization" header for this account
 * ("Bearer $accessToken").
 * @property unifiedPushInstance UnifiedPush "instance" identifier for this account.
 * @property hasPushScope True if the account has the `push` OAuth scope.
 * @property notificationMethod The account's [AccountNotificationMethod].
 */
data class PachliAccountConfig(
    val pachliAccountId: Long,
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
    val notificationsFollowRequested: Boolean = true,
    val notificationsReblogged: Boolean = true,
    val notificationsFavorited: Boolean = true,
    val notificationsPolls: Boolean = true,
    val notificationsSubscriptions: Boolean = true,
    val notificationsSignUps: Boolean = true,
    val notificationsUpdates: Boolean = true,
    val notificationsReports: Boolean = true,
    val notificationsSeveredRelationships: Boolean = true,
    val notificationsModerationWarnings: Boolean = true,
    val notificationsQuotes: Boolean = true,
    val notificationsQuotedUpdates: Boolean = true,
    val notificationsCollectionAdd: Boolean = true,
    val notificationsCollectionUpdate: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val notificationLight: Boolean = true,
    val defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
    val defaultMediaSensitivity: Boolean = false,
    val defaultPostLanguage: String = "",
    val defaultQuotePolicy: AccountSource.QuotePolicy = AccountSource.QuotePolicy.NOBODY,
    val alwaysShowSensitiveMedia: Boolean = false,
    val alwaysOpenSpoiler: Boolean = false,
    val mediaPreviewEnabled: Boolean = true,
    val notificationMarkerId: String = "0",
    val tabPreferences: List<Timeline> = defaultTabs(),
    // TODO: This is being serialized/deserialized at the wrong level (see NotificationsViewModel)
    // for calls.
    val notificationsFilter: String = "[\"follow_request\"]",
    val oauthScopes: String = "",
    val unifiedPushUrl: String = "",
    val pushPubKey: String = "",
    val pushPrivKey: String = "",
    val pushAuth: String = "",
    val pushServerKey: String = "",
    var notificationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    var notificationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    var notificationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
    var conversationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    var conversationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    var conversationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
) {
    val identifier: AccountIdentifier
        get() = AccountIdentifier(this)

    /** The account's handle, `@$username@$domain`. */
    val handle: String
        get() = "@$username@$domain"

    val authHeader: String
        get() = "Bearer $accessToken"

    val unifiedPushInstance: String
        get() = pachliAccountId.toString()

    val hasPushScope: Boolean
        get() = oauthScopes.contains("push")

    val notificationMethod: AccountNotificationMethod
        get() {
            if (unifiedPushUrl.isBlank()) return AccountNotificationMethod.PULL
            return AccountNotificationMethod.PUSH
        }

    companion object {
        fun defaultTabs() = listOf(
            Timeline.Home,
            Timeline.Notifications,
            Timeline.TrendingStatuses,
            Timeline.Conversations,
        )
    }
}
