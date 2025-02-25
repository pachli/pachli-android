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
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.Status

@Entity(
    indices = [
        Index(
            value = ["domain", "accountId"],
            unique = true,
        ),
    ],
)
@TypeConverters(Converters::class)
data class AccountEntity(
    @field:PrimaryKey(autoGenerate = true) var id: Long,
    val domain: String,
    val accessToken: String,
    val clientId: String,
    val clientSecret: String,
    val isActive: Boolean,
    /** Account's remote (server) ID. */
    val accountId: String = "",
    /** User's local name, without the leading `@` or the `@domain` portion */
    val username: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val profileHeaderPictureUrl: String = "",
    /** User wants Android notifications enabled for this account */
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
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val notificationLight: Boolean = true,
    val defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
    val defaultMediaSensitivity: Boolean = false,
    val defaultPostLanguage: String = "",
    val alwaysShowSensitiveMedia: Boolean = false,
    /** True if content behind a content warning is shown by default */
    val alwaysOpenSpoiler: Boolean = false,

    /**
     * True if the "Download media previews" preference is true. This implies
     * that media previews are shown as well as downloaded.
     */
    val mediaPreviewEnabled: Boolean = true,
    /**
     *  ID of the most recent Mastodon notification that Pachli has fetched to show as an
     *  Android notification.
     */
    @ColumnInfo(defaultValue = "0")
    val notificationMarkerId: String = "0",
    val emojis: List<Emoji> = emptyList(),
    val tabPreferences: List<Timeline> = defaultTabs(),
    val notificationsFilter: String = "[\"follow_request\"]",
    // Scope cannot be changed without re-login, so store it in case
    // the scope needs to be changed in the future
    val oauthScopes: String = "",
    val unifiedPushUrl: String = "",
    val pushPubKey: String = "",
    val pushPrivKey: String = "",
    val pushAuth: String = "",
    val pushServerKey: String = "",

    /** True if the connected Mastodon account is locked (has to manually approve all follow requests **/
    @ColumnInfo(defaultValue = "0")
    val locked: Boolean = false,

    /** [FilterAction] for notifications from accounts this account does not follow. */
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,

    /** [FilterAction] for notifications from accounts younger than 30 days. */
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterYounger30d: FilterAction = FilterAction.NONE,

    /** [FilterAction] for notifications from account limited by the server. */
    @ColumnInfo(defaultValue = "NONE")
    var notificationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,

    /** [FilterAction] for conversations from accounts this account does not follow. */
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterNotFollowed: FilterAction = FilterAction.NONE,

    /** [FilterAction] for conversations from accounts younger than 30 days. */
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterYounger30d: FilterAction = FilterAction.NONE,

    /** [FilterAction] for conversations from account limited by the server. */
    @ColumnInfo(defaultValue = "NONE")
    var conversationAccountFilterLimitedByServer: FilterAction = FilterAction.NONE,

) {

    val identifier: String
        get() = "$domain:$accountId"

    /** Full account name, of the form `@username@domain` */
    val fullName: String
        get() = "@$username@$domain"

    /** UnifiedPush "instance" identifier for this account. */
    val unifiedPushInstance: String
        get() = id.toString()

    fun isLoggedIn() = accessToken.isNotEmpty()

    /** Value of the "Authorization" header for this account ("Bearer $accessToken"). */
    val authHeader: String
        get() = "Bearer $accessToken"
}

fun defaultTabs() = listOf(
    Timeline.Home,
    Timeline.Notifications,
    Timeline.PublicLocal,
    Timeline.Conversations,
)
