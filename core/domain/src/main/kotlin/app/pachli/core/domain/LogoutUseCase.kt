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

package app.pachli.core.domain

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.pm.ShortcutManagerCompat
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.LogoutError
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val disablePushNotificationsForAccount: DisablePushNotificationsForAccountUseCase,
) {
    /**
     * Logs [account] out and clears all caches associated with it.
     *
     * @return [com.github.michaelbull.result.Result] with any error that occurred during
     * logout. The errors are ignored (local data is still deleted), but this allows the
     * caller to inform the user.
     */
    suspend operator fun invoke(account: AccountEntity): Result<Unit, LogoutError> {
        var result: Result<Unit, LogoutError> = Ok(Unit)

        disablePushNotificationsForAccount(account)
            .andThen {
                api.revokeOAuthToken(
                    auth = "Bearer ${account.accessToken}",
                    domain = account.domain,
                    clientId = account.clientId,
                    clientSecret = account.clientSecret,
                    token = account.accessToken,
                )
            }
            .onFailure { result = Err(LogoutError.Api(it)) }

        // Clear notification channels
        deleteNotificationChannelsForAccount(account, context)

        // Remove shortcut associated with the account
        ShortcutManagerCompat.disableShortcuts(context, listOf(account.id.toString()), null)
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(account.id.toString()))

        // return accountManager.logActiveAccountOut()
        accountManager.deleteAccount(account)
        return result
    }

    private fun deleteNotificationChannelsForAccount(account: AccountEntity, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannelGroup(account.identifier)
        }
    }
}
