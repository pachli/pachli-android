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
 * @property fullName Full account name, of the form `@username@domain`
 * @property authHeader Value of the "Authorization" header for this account
 * ("Bearer $accessToken").
 * @property unifiedPushInstance UnifiedPush "instance" identifier for this account.
 * @property hasPushScope True if the account has the `push` OAuth scope.
 * @property notificationMethod The account's [AccountNotificationMethod].
 */
interface IPachliAccountConfig {
    val pachliAccountId: Long
    val domain: String
    val accessToken: String
    val clientId: String
    val clientSecret: String
    val isActive: Boolean
    val accountId: String
    val username: String
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
    val notificationsCollectionAdd: Boolean
    val notificationsCollectionUpdate: Boolean
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
    val tabPreferences: List<Timeline>
    val notificationsFilter: String
    val oauthScopes: String
    val unifiedPushUrl: String
    val pushPubKey: String
    val pushPrivKey: String
    val pushAuth: String
    val pushServerKey: String
    var notificationAccountFilterNotFollowed: FilterAction
    var notificationAccountFilterYounger30d: FilterAction
    var notificationAccountFilterLimitedByServer: FilterAction
    var conversationAccountFilterNotFollowed: FilterAction
    var conversationAccountFilterYounger30d: FilterAction
    var conversationAccountFilterLimitedByServer: FilterAction

    val identifier: AccountIdentifier
        get() = AccountIdentifier(this)

    val fullName: String
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
}

data class PachliAccountConfig(
    override val pachliAccountId: Long,
    override val domain: String,
    override val accessToken: String,
    override val clientId: String,
    override val clientSecret: String,
    override val isActive: Boolean,
    override val accountId: String,
    override val username: String,
    override val notificationsEnabled: Boolean = true,
    override val notificationsMentioned: Boolean = true,
    override val notificationsFollowed: Boolean = true,
    override val notificationsFollowRequested: Boolean = true,
    override val notificationsReblogged: Boolean = true,
    override val notificationsFavorited: Boolean = true,
    override val notificationsPolls: Boolean = true,
    override val notificationsSubscriptions: Boolean = true,
    override val notificationsSignUps: Boolean = true,
    override val notificationsUpdates: Boolean = true,
    override val notificationsReports: Boolean = true,
    override val notificationsSeveredRelationships: Boolean = true,
    override val notificationsModerationWarnings: Boolean = true,
    override val notificationsQuotes: Boolean = true,
    override val notificationsQuotedUpdates: Boolean = true,
    override val notificationsCollectionAdd: Boolean = true,
    override val notificationsCollectionUpdate: Boolean = true,
    override val notificationSound: Boolean = true,
    override val notificationVibration: Boolean = true,
    override val notificationLight: Boolean = true,
    override val defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
    override val defaultMediaSensitivity: Boolean = false,
    override val defaultPostLanguage: String = "",
    override val defaultQuotePolicy: AccountSource.QuotePolicy = AccountSource.QuotePolicy.NOBODY,
    override val alwaysShowSensitiveMedia: Boolean = false,
    override val alwaysOpenSpoiler: Boolean = false,
    override val mediaPreviewEnabled: Boolean = true,
    override val notificationMarkerId: String = "0",
    override val tabPreferences: List<Timeline> = defaultTabs(),
    // TODO: This is being serialized/deserialized at the wrong level (see NotificationsViewModel)
    // for calls.
    override val notificationsFilter: String = "[\"follow_request\"]",
    override val oauthScopes: String = "",
    override val unifiedPushUrl: String = "",
    override val pushPubKey: String = "",
    override val pushPrivKey: String = "",
    override val pushAuth: String = "",
    override val pushServerKey: String = "",
    override var notificationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    override var notificationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    override var notificationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
    override var conversationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,
    override var conversationAccountFilterYounger30d: FilterAction = FilterAction.NONE,
    override var conversationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,
) : IPachliAccountConfig {
    companion object {
        fun defaultTabs() = listOf(
            Timeline.Home,
            Timeline.Notifications,
            Timeline.TrendingStatuses,
            Timeline.Conversations,
        )
    }
}
