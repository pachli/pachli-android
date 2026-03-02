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

package app.pachli.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.domain.RegisterUnifiedPushEndpointUseCase
import app.pachli.core.domain.notifications.DisablePushNotificationsForAccountUseCase
import app.pachli.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

@AndroidEntryPoint
class UnifiedPushService : PushService() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var registerUnifiedPushEndpoint: RegisterUnifiedPushEndpointUseCase

    @Inject
    lateinit var disablePushNotificationsForAccount: DisablePushNotificationsForAccountUseCase

    @Inject
    @ApplicationContext
    lateinit var context: Context

    override fun onMessage(message: PushMessage, instance: String) {
        Timber.d("onMessage")
        Timber.d("New message received for account %s", instance)
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Start a worker just for this account
            .setInputData(NotificationWorker.data(instance.toLong()))
            .build()
        workManager.enqueue(request)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Timber.d("onNewEndpoint for instance $instance")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            Timber.d("Endpoint available for account %s: %s", account, instance)
            // Launch the coroutine in global scope -- it is short and we don't want to lose the registration event
            // and there is no saner way to use structured concurrency in a receiver
            GlobalScope.launch { registerUnifiedPushEndpoint(account, endpoint) }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Timber.d("onRegistrationFailed")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            Timber.d("Could not register ${account.displayName}")
        }
    }

    override fun onUnregistered(instance: String) {
        Timber.d("onUnregistered with instance $instance")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            GlobalScope.launch {
                disablePushNotificationsForAccount(account)
            }
        }
    }
}
