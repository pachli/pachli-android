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

package app.pachli.feature.login

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.network.model.AccessToken
import app.pachli.core.network.retrofit.apiresult.ApiError

/** Actions the user can take from the UI. */
internal sealed interface UiAction

internal sealed interface FallibleUiAction : UiAction {
    /** Verify the account has working credentials and add to local database. */
    data class VerifyAndAddAccount(
        val accessToken: AccessToken,
        val domain: String,
        val clientId: String,
        val clientSecret: String,
        val oAuthScopes: String,
    ) : FallibleUiAction
}

/** Actions that succeeded. */
internal sealed interface UiSuccess {
    val action: FallibleUiAction

    /** @see [FallibleUiAction.VerifyAndAddAccount]. */
    data class VerifyAndAddAccount(
        override val action: FallibleUiAction.VerifyAndAddAccount,
        val accountId: Long,
    ) : UiSuccess
}

/** Actions that failed. */
internal sealed class UiError(
    @StringRes override val resourceId: Int,
    open val action: UiAction,
    override val cause: PachliError,
    override val formatArgs: Array<out String>? = null,
) : PachliError {
    /** @see [FallibleUiAction.VerifyAndAddAccount]. */
    data class VerifyAndAddAccount(
        override val action: FallibleUiAction.VerifyAndAddAccount,
        override val cause: ApiError,
    ) : UiError(R.string.error_loading_account_details_fmt, action, cause)
}
