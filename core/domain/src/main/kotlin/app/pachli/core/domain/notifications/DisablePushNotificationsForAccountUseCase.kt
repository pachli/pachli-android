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

package app.pachli.core.domain.notifications

import android.content.Context
import androidx.core.content.edit
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.unifiedpush.android.connector.PREF_MASTER
import org.unifiedpush.android.connector.PREF_MASTER_DISTRIBUTOR
import org.unifiedpush.android.connector.PREF_MASTER_DISTRIBUTOR_ACK
import org.unifiedpush.android.connector.UnifiedPush

class DisablePushNotificationsForAccountUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MastodonApi,
    private val accountManager: AccountManager,
) {
    /**
     * Disables UnifiedPush notifications for [account].
     *
     * - Calls the server to disable push notifications.
     * - Clears UnifiedPush related data from the account's data.
     * - Unregisters from the UnifiedPush provider.
     *
     * API errors that occur during the process are ignored, but are returned,
     * so the caller can inform the user of any problems.
     *
     * @param account
     */
    suspend operator fun invoke(account: AccountEntity): Result<Unit, ApiError> {
        if (account.notificationMethod != AccountNotificationMethod.PUSH) return Ok(Unit)

        var result: Result<Unit, ApiError> = Ok(Unit)

        // Try and unregister the endpoint from the server.
        api.unsubscribePushNotifications("Bearer ${account.accessToken}", account.domain)
            .onFailure { result = Err(it) }

        // Clear the push notification from the account.
        accountManager.clearPushNotificationData(account.id)
        NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.Pull

        // Unregister from the UnifiedPush provider.
        //
        // UnifiedPush.unregisterApp will try and remove the user's distributor choice (including
        // whether or not instances had acked it). Work around this bug by saving the values, and
        // restoring them afterwards.
        val prefs = context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)
        val savedDistributor = UnifiedPush.getSavedDistributor(context)
        val savedDistributorAck = prefs.getBoolean(PREF_MASTER_DISTRIBUTOR_ACK, false)

        UnifiedPush.unregisterApp(context, account.unifiedPushInstance)

        prefs.edit {
            putString(PREF_MASTER_DISTRIBUTOR, savedDistributor)
            putBoolean(PREF_MASTER_DISTRIBUTOR_ACK, savedDistributorAck)
        }

        return result
    }
}
