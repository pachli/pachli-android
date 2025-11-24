/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.preferences

import app.pachli.core.model.AccountSource

/** See [AccountSource.QuotePolicy] */
enum class QuotePolicy(override val displayResource: Int, override val value: String? = null) : PreferenceEnum {
    PUBLIC(R.string.pref_quote_policy_public),
    FOLLOWERS(R.string.pref_quote_policy_followers),
    NOBODY(R.string.pref_quote_policy_nobody),

    ;

    fun asModel() = when (this) {
        PUBLIC -> AccountSource.QuotePolicy.PUBLIC
        FOLLOWERS -> AccountSource.QuotePolicy.FOLLOWERS
        NOBODY -> AccountSource.QuotePolicy.NOBODY
    }

    fun iconRes(): Int = when (this) {
        PUBLIC -> app.pachli.core.designsystem.R.drawable.ic_public_24dp
        FOLLOWERS -> app.pachli.core.designsystem.R.drawable.ic_lock_open_24dp
        NOBODY -> app.pachli.core.designsystem.R.drawable.ic_lock_24dp
    }
}

fun AccountSource.QuotePolicy.asPreference() = when (this) {
    AccountSource.QuotePolicy.PUBLIC -> QuotePolicy.PUBLIC
    AccountSource.QuotePolicy.FOLLOWERS -> QuotePolicy.FOLLOWERS
    AccountSource.QuotePolicy.NOBODY -> QuotePolicy.NOBODY
}
