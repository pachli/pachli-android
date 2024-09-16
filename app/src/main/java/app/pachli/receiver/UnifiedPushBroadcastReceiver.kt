/* Copyright 2022 Tusky contributors
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

package app.pachli.receiver

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.pachli.components.notifications.disablePushNotificationsForAccount
import app.pachli.components.notifications.registerUnifiedPushEndpoint
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver
import timber.log.Timber

@AndroidEntryPoint
class UnifiedPushBroadcastReceiver : MessagingReceiver() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Timber.d("onMessage")
        Timber.d("New message received for account %s", instance)
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Start a worker just for this account
            .setInputData(NotificationWorker.data(instance.toLong()))
            .build()
        workManager.enqueue(request)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Timber.d("onNewEndpoint for instance $instance")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            Timber.d("Endpoint available for account %s: %s", account, instance)
            // Launch the coroutine in global scope -- it is short and we don't want to lose the registration event
            // and there is no saner way to use structured concurrency in a receiver
            GlobalScope.launch { registerUnifiedPushEndpoint(context, mastodonApi, accountManager, account, endpoint) }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Timber.d("onRegistrationFailed")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            Timber.d("Could not register ${account.displayName}")
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        Timber.d("onUnregistered with instance $instance")
        accountManager.getAccountById(instance.toLong())?.let { account ->
            GlobalScope.launch {
                disablePushNotificationsForAccount(context, mastodonApi, accountManager, account)
            }
        }
    }
}
