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
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.pachli.components.notifications.registerUnifiedPushEndpoint
import app.pachli.components.notifications.unregisterUnifiedPushEndpoint
import app.pachli.core.accounts.AccountManager
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver
import timber.log.Timber

@DelicateCoroutinesApi
@AndroidEntryPoint
class UnifiedPushBroadcastReceiver : MessagingReceiver() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Timber.d("New message received for account $instance")
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequest.from(NotificationWorker::class.java)
        workManager.enqueue(request)
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Timber.d("Endpoint available for account $instance: $endpoint")
        accountManager.getAccountById(instance.toLong())?.let {
            // Launch the coroutine in global scope -- it is short and we don't want to lose the registration event
            // and there is no saner way to use structured concurrency in a receiver
            GlobalScope.launch { registerUnifiedPushEndpoint(context, mastodonApi, accountManager, it, endpoint) }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) = Unit

    override fun onUnregistered(context: Context, instance: String) {
        Timber.d("Endpoint unregistered for account $instance")
        accountManager.getAccountById(instance.toLong())?.let {
            // It's fine if the account does not exist anymore -- that means it has been logged out
            GlobalScope.launch { unregisterUnifiedPushEndpoint(mastodonApi, accountManager, it) }
        }
    }
}
