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

package app.pachli.core.model

import java.time.Instant
import java.util.Date

/** Like [Account], but with the additional [source] and [role] properties. */
data class CredentialAccount(
    val id: String,
    val localUsername: String,
    val username: String,
    // should never be null per API definition, but some servers break the contract
    val displayName: String?,
    val note: String,
    val source: AccountSource,
    val url: String,
    val avatar: String,
    val avatarStatic: String? = null,
    val header: String = "",
    val headerStatic: String? = null,
    val locked: Boolean = false,
    val emojis: List<Emoji> = emptyList(),
    val fields: List<Field> = emptyList(),
    val bot: Boolean = false,
    val discoverable: Boolean? = false,
    val noIndex: Boolean = true,
    val moved: Account? = null,
    val suspended: Boolean = false,
    val limited: Boolean = false,
    // Should never be null, but some servers break the contract.
    val createdAt: Instant?,
    val lastStatusAt: Date? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val statusesCount: Int = 0,
    val role: CredentialedRole? = null,
    val roles: List<Role>? = emptyList(),
)

data class AccountSource(
    val privacy: Status.Visibility? = null,
    val sensitive: Boolean? = null,
    val note: String? = null,
    val fields: List<StringField> = emptyList(),
    val language: String? = null,
    val attributionDomains: List<String> = emptyList(),
    val quotePolicy: QuotePolicy = QuotePolicy.PUBLIC,
) {
    enum class QuotePolicy {
        PUBLIC,
        FOLLOWERS,
        NOBODY,
    }
}

/**
 * Convert a [Status.QuoteApproval] to a [QuotePolicy].
 *
 * Required when editing a status, [StatusSource] does not contain the original
 * [AccountSource.QuotePolicy], so we have to try and figure it out.
 *
 * The imperfect heuristic is:
 *
 * - If PUBLIC is automatically approved then the original policy was "public".
 * - If FOLLOWERS is automatically approved then the original policy "followers".
 * - Anything else means the original policy was "NOBODY".
 */
fun Status.QuoteApproval.asQuotePolicy(): AccountSource.QuotePolicy {
    if (this.automatic.contains(Status.QuoteApproval.QuoteApprovalAutomatic.PUBLIC)) return AccountSource.QuotePolicy.PUBLIC
    if (this.automatic.contains(Status.QuoteApproval.QuoteApprovalAutomatic.FOLLOWERS)) return AccountSource.QuotePolicy.FOLLOWERS
    return AccountSource.QuotePolicy.NOBODY
}

data class CredentialedRole(
    val name: String,
    val color: String,
    val permissions: String,
    val highlighted: Boolean,
)
