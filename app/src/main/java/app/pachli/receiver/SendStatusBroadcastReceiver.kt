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
import app.pachli.components.notifications.KEY_CITED_STATUS_ID
import app.pachli.components.notifications.KEY_MENTIONS
import app.pachli.components.notifications.KEY_NOTIFICATION_ID
import app.pachli.components.notifications.KEY_REPLY
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_FULL_NAME
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_ID
import app.pachli.components.notifications.KEY_SENDER_ACCOUNT_IDENTIFIER
import app.pachli.components.notifications.KEY_SPOILER
import app.pachli.components.notifications.KEY_VISIBILITY
import app.pachli.components.notifications.REPLY_ACTION
import app.pachli.core.accounts.AccountManager
import app.pachli.core.common.string.randomAlphanumericString
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.Status
import app.pachli.service.SendStatusService
import app.pachli.service.StatusToSend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class SendStatusBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == REPLY_ACTION) {
            val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
            val senderId = intent.getLongExtra(KEY_SENDER_ACCOUNT_ID, -1)
            val senderIdentifier = intent.getStringExtra(KEY_SENDER_ACCOUNT_IDENTIFIER)
            val senderFullName = intent.getStringExtra(KEY_SENDER_ACCOUNT_FULL_NAME)
            val citedStatusId = intent.getStringExtra(KEY_CITED_STATUS_ID)
            val visibility = intent.getSerializableExtra(KEY_VISIBILITY) as Status.Visibility
            val spoiler = intent.getStringExtra(KEY_SPOILER).orEmpty()
            val mentions = intent.getStringArrayExtra(KEY_MENTIONS).orEmpty()

            val account = accountManager.getAccountById(senderId)

            val notificationManager = NotificationManagerCompat.from(context)

            val message = getReplyMessage(intent)

            if (account == null) {
                Timber.w("Account \"$senderId\" not found in database. Aborting quick reply!")

                if (ActivityCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
                    return
                }

                val builder = NotificationCompat.Builder(context, CHANNEL_MENTION + senderIdentifier)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(context.getColor(DR.color.tusky_blue))
                    .setGroup(senderFullName)
                    .setDefaults(0) // So it doesn't ring twice, notify only in Target callback

                builder.setContentTitle(context.getString(app.pachli.core.ui.R.string.error_generic))
                builder.setContentText(context.getString(R.string.error_sender_account_gone))

                builder.setSubText(senderFullName)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
                builder.setOnlyAlertOnce(true)

                notificationManager.notify(notificationId, builder.build())
            } else {
                val text = mentions.joinToString(" ", postfix = " ") { "@$it" } + message.toString()

                val sendIntent = SendStatusService.sendStatusIntent(
                    context,
                    StatusToSend(
                        text = text,
                        warningText = spoiler,
                        visibility = visibility.serverString(),
                        sensitive = false,
                        media = emptyList(),
                        scheduledAt = null,
                        inReplyToId = citedStatusId,
                        poll = null,
                        replyingStatusContent = null,
                        replyingStatusAuthorUsername = null,
                        accountId = account.id,
                        draftId = -1,
                        idempotencyKey = randomAlphanumericString(16),
                        retries = 0,
                        language = null,
                        statusId = null,
                    ),
                )

                context.startService(sendIntent)

                val builder = NotificationCompat.Builder(context, CHANNEL_MENTION + senderIdentifier)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(context.getColor(DR.color.notification_color))
                    .setGroup(senderFullName)
                    .setDefaults(0) // So it doesn't ring twice, notify only in Target callback

                builder.setContentTitle(context.getString(R.string.post_sent))
                builder.setContentText(context.getString(R.string.post_sent_long))

                builder.setSubText(senderFullName)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
                builder.setOnlyAlertOnce(true)

                // There is a separate "I am sending" notification, so simply remove the handled one.
                notificationManager.cancel(notificationId)
            }
        }
    }

    private fun getReplyMessage(intent: Intent): CharSequence {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)

        return remoteInput?.getCharSequence(KEY_REPLY, "") ?: ""
    }
}
