/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.network.model

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.HasDefault
import app.pachli.core.network.model.AccountSource.QuotePolicy
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.util.Date

/** Like [Account], but with the additional [source] and [role] properties. */
@JsonClass(generateAdapter = true)
data class CredentialAccount(
    val id: String,
    @Json(name = "username") val localUsername: String,
    @Json(name = "acct") val username: String,
    // should never be null per API definition, but some servers break the contract
    @Json(name = "display_name") val displayName: String?,
    val note: String,
    val source: AccountSource,
    val url: String,
    val avatar: String,
    @Json(name = "avatar_static") val avatarStatic: String? = null,
    val header: String = "",
    @Json(name = "header_static") val headerStatic: String? = null,
    val locked: Boolean = false,
    @DefaultIfNull
    val emojis: List<Emoji> = emptyList(),
    @DefaultIfNull
    val fields: List<Field> = emptyList(),
    val bot: Boolean = false,
    @DefaultIfNull
    val discoverable: Boolean? = false,
    @DefaultIfNull
    @Json(name = "noindex") val noIndex: Boolean = true,
    val moved: Account? = null,
    @DefaultIfNull
    val suspended: Boolean = false,
    @DefaultIfNull
    val limited: Boolean = false,
    // Should never be null, but some servers break the contract.
    val createdAt: Instant?,
    @Json(name = "last_status_at") val lastStatusAt: Date? = null,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "statuses_count") val statusesCount: Int = 0,
    val role: CredentialedRole? = null,
    val roles: List<Role>? = emptyList(),
) {
    val name: String
        get() = if (displayName.isNullOrEmpty()) {
            localUsername
        } else {
            displayName
        }

    fun asModel() = app.pachli.core.model.CredentialAccount(
        id = id,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        note = note,
        source = source.asModel(),
        url = url,
        avatar = avatar,
        avatarStatic = avatarStatic,
        header = header,
        headerStatic = headerStatic,
        locked = locked,
        emojis = emojis.asModel(),
        fields = fields.asModel(),
        bot = bot,
        discoverable = discoverable,
        noIndex = noIndex,
        moved = moved?.asModel(),
        suspended = suspended,
        limited = limited,
        createdAt = createdAt,
        lastStatusAt = lastStatusAt,
        followersCount = followersCount,
        followingCount = followingCount,
        statusesCount = statusesCount,
        role = role?.asModel(),
        roles = roles?.asModel(),
    )
}

@JsonClass(generateAdapter = true)
data class AccountSource(
    val privacy: Status.Visibility? = null,
    val sensitive: Boolean? = null,
    val note: String? = null,
    @DefaultIfNull
    val fields: List<StringField> = emptyList(),
    val language: String? = null,
    @DefaultIfNull
    @Json(name = "attribution_domains") val attributionDomains: List<String> = emptyList(),
    @Json(name = "quote_policy")
    val quotePolicy: QuotePolicy? = QuotePolicy.NOBODY,
) {
    @HasDefault
    enum class QuotePolicy {
        @Json(name = "public")
        PUBLIC,

        @Json(name = "followers")
        FOLLOWERS,

        @Json(name = "nobody")
        @Default
        NOBODY,

        ;

        fun asModel() = when (this) {
            PUBLIC -> app.pachli.core.model.AccountSource.QuotePolicy.PUBLIC
            FOLLOWERS -> app.pachli.core.model.AccountSource.QuotePolicy.FOLLOWERS
            NOBODY -> app.pachli.core.model.AccountSource.QuotePolicy.NOBODY
        }

        fun asFormValue() = when (this) {
            QuotePolicy.PUBLIC -> "public"
            QuotePolicy.FOLLOWERS -> "followers"
            QuotePolicy.NOBODY -> "nobody"
        }
    }

    fun asModel() = app.pachli.core.model.AccountSource(
        privacy = privacy?.asModel(),
        sensitive = sensitive,
        note = note,
        fields = fields.asModel(),
        language = language,
        attributionDomains = attributionDomains,
        quotePolicy = quotePolicy?.asModel() ?: QuotePolicy.NOBODY.asModel(),
    )
}

fun app.pachli.core.model.AccountSource.QuotePolicy.asNetworkModel() = when (this) {
    app.pachli.core.model.AccountSource.QuotePolicy.PUBLIC -> QuotePolicy.PUBLIC
    app.pachli.core.model.AccountSource.QuotePolicy.FOLLOWERS -> QuotePolicy.FOLLOWERS
    app.pachli.core.model.AccountSource.QuotePolicy.NOBODY -> QuotePolicy.NOBODY
}

@JsonClass(generateAdapter = true)
data class CredentialedRole(
    val id: String,
    val name: String,
    val color: String,
    val permissions: String,
    val highlighted: Boolean,
) {
    fun asModel() = app.pachli.core.model.CredentialedRole(
        name = name,
        color = color,
        permissions = permissions,
        highlighted = highlighted,
    )
}
