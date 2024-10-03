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

package app.pachli.components.notifications.domain

import android.content.Context
import androidx.preference.PreferenceManager
import app.pachli.components.notifications.chooseUnifiedPushDistributor
import app.pachli.components.notifications.disableAllNotifications
import app.pachli.components.notifications.enablePullNotifications
import app.pachli.components.notifications.hasPushScope
import app.pachli.core.activity.NotificationConfig
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import javax.inject.Inject
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

class EnableAllNotificationsUseCase @Inject constructor(
    private val api: MastodonApi,
    private val accountManager: AccountManager,
) {
    suspend operator fun invoke(context: Context) {
        // Start from a clean slate.
        disableAllNotifications(context, api, accountManager)

        // Launch a single pull worker to periodically get notifications from all accounts,
        // irrespective of whether or not UnifiedPush is configured.
        enablePullNotifications(context)

        // If no accounts have push scope there's nothing to do.
        val accountsWithPushScope = accountManager.accounts.filter { it.hasPushScope }
        if (accountsWithPushScope.isEmpty()) {
            Timber.Forest.d("No accounts have push scope, skipping UnifiedPush reconfiguration")
            return
        }

        // If no UnifiedPush distributors are installed then there's nothing more to do.
        NotificationConfig.unifiedPushAvailable = false

        // Get the UnifiedPush distributor to use, possibly falling back to the user's previous
        // choice if it's still on the device.
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val usePreviousDistributor = prefs.getBoolean(PrefKeys.USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR, true)
        if (!usePreviousDistributor) {
            prefs.edit().apply {
                putBoolean(PrefKeys.USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR, true)
            }.apply()
        }

        val distributor = chooseUnifiedPushDistributor(context, usePreviousDistributor)
        if (distributor == null) {
            Timber.Forest.d("No UnifiedPush distributor installed, skipping UnifiedPush reconfiguration")

            UnifiedPush.safeRemoveDistributor(context)
            return
        }
        Timber.Forest.d("Chose %s as UnifiedPush distributor", distributor)
        NotificationConfig.unifiedPushAvailable = true

        UnifiedPush.saveDistributor(context, distributor)
        accountsWithPushScope.forEach {
            Timber.Forest.d("Registering instance %s, %s with %s", it.unifiedPushInstance, it.fullName, distributor)
            UnifiedPush.registerApp(context, it.unifiedPushInstance, messageForDistributor = it.fullName)
        }
    }
}
