/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import app.pachli.R
import app.pachli.components.notifications.CHANNEL_MENTION
import app.pachli.components.notifications.KEY_DRAFT
import app.pachli.components.notifications.KEY_REPLY
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_FULL_NAME
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_ID
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_IDENTIFIER
import app.pachli.components.notifications.KEY_SERVER_NOTOFICATION_ID
import app.pachli.components.notifications.REPLY_ACTION
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.string.randomAlphanumericString
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.DraftsRepository
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Draft
import app.pachli.core.sendstatus.SendStatusService
import app.pachli.core.sendstatus.model.StatusToSend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SendStatusBroadcastReceiver : BroadcastReceiver() {
    @Inject
    @ApplicationScope
    lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var draftsRepository: DraftsRepository

    override fun onReceive(context: Context, intent: Intent) {
        // Bail unless the user used the "quick reply" feature on a notification.
        if (intent.action != REPLY_ACTION) return

        val serverNotificationId = intent.getStringExtra(KEY_SERVER_NOTOFICATION_ID)
        val senderId = intent.getLongExtra(KEY_SENDER_ACCOUNT_ID, -1)
        val senderIdentifier = intent.getStringExtra(KEY_SENDER_ACCOUNT_IDENTIFIER)
        val senderFullName = intent.getStringExtra(KEY_SENDER_ACCOUNT_FULL_NAME)
        val draft = intent.getParcelableExtra<Draft>(KEY_DRAFT)

        val account = accountManager.getAccountById(senderId)

        val notificationManager = NotificationManagerCompat.from(context)

        if (account == null) {
            Timber.w("Account \"$senderId\" not found in database. Aborting quick reply!")
            showQuickReplyErrorNotification(senderId, context, senderIdentifier, senderFullName, notificationManager, serverNotificationId)
            return
        }

        if (draft == null) {
            Timber.w("Quick reply when `draft` == null. Aborting quick reply!")
            showQuickReplyErrorNotification(senderId, context, senderIdentifier, senderFullName, notificationManager, serverNotificationId)
            return
        }

        val pendingResult = goAsync()
        externalScope.launch(Dispatchers.IO) {
            val finalDraft = draftsRepository.upsertDraft(
                account.id,
                draft.copy(
                    content = draft.content + (RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY, "") ?: ""),
                ),
            )

            val sendIntent = SendStatusService.sendStatusIntent(
                context,
                StatusToSend(
                    draft = finalDraft,
                    media = emptyList(),
                    pachliAccountId = account.id,
                    idempotencyKey = randomAlphanumericString(16),
                    retries = 0,
                ),
            )

            context.startService(sendIntent)

            if (ActivityCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
                return@launch
            }

            // Can't cancel the QuickReply notification, replace it with one that
            // auto-cancels.
            val notification = NotificationCompat.Builder(context, CHANNEL_MENTION + senderIdentifier)
                .setSmallIcon(app.pachli.core.common.R.drawable.ic_notify)
                .setColor(context.getColor(DR.color.notification_color))
                .setGroup(senderFullName)
                .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
                .setOnlyAlertOnce(true)
                .setContentTitle(context.getString(app.pachli.core.sendstatus.R.string.send_post_notification_title))
                .setSubText(senderFullName)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setTimeoutAfter(5000)
                .build()

            notificationManager.notify(serverNotificationId, senderId.toInt(), notification)
        }.invokeOnCompletion { pendingResult.finish() }
    }

    private fun showQuickReplyErrorNotification(
        senderId: Long,
        context: Context,
        senderIdentifier: String?,
        senderFullName: String?,
        notificationManager: NotificationManagerCompat,
        serverNotificationId: String?,
    ) {
        if (ActivityCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MENTION + senderIdentifier)
            .setSmallIcon(app.pachli.core.common.R.drawable.ic_notify)
            .setColor(context.getColor(DR.color.notification_color))
            .setGroup(senderFullName)
            .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
            .setContentTitle(context.getString(app.pachli.core.ui.R.string.error_generic))
            .setContentText(context.getString(R.string.error_sender_account_gone))
            .setSubText(senderFullName)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(serverNotificationId, senderId.toInt(), notification)
    }
}
