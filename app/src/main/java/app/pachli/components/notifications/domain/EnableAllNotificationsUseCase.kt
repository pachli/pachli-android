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
import app.pachli.components.notifications.chooseUnifiedPushDistributor
import app.pachli.components.notifications.disablePullNotifications
import app.pachli.components.notifications.enablePullNotifications
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.domain.notifications.DisablePushNotificationsForAccountUseCase
import app.pachli.core.domain.notifications.NotificationConfig
import app.pachli.core.domain.notifications.hasPushScope
import app.pachli.core.preferences.SharedPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

class EnableAllNotificationsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountManager: AccountManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val disablePushNotificationsForAccount: DisablePushNotificationsForAccountUseCase,
) {
    suspend operator fun invoke() {
        // Start from a clean slate.
        disableAllNotifications()

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
        val usePreviousDistributor = sharedPreferencesRepository.usePreviousUnifiedPushDistributor
        if (!usePreviousDistributor) {
            sharedPreferencesRepository.usePreviousUnifiedPushDistributor = true
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

    /**
     * Disables all notifications.
     *
     * - Cancels notification workers
     * - Unregisters instances from the UnifiedPush distributor
     */
    private suspend fun disableAllNotifications() {
        Timber.d("Disabling all notifications")
        disablePushNotifications()
        disablePullNotifications(context)
    }

    /**
     * Disables push notifications for each account.
     */
    private suspend fun disablePushNotifications() {
        accountManager.accounts.forEach { disablePushNotificationsForAccount(it) }
    }
}
