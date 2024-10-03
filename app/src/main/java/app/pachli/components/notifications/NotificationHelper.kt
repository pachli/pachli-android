/* Copyright 2018 Jeremiasz Nelz <remi6397(a)gmail.com>
 * Copyright 2017 Andrew Dawson
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
package app.pachli.components.notifications

import app.pachli.core.designsystem.R as DR
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import app.pachli.BuildConfig
import app.pachli.MainActivity
import app.pachli.R
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.NotificationConfig
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.receiver.SendStatusBroadcastReceiver
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import app.pachli.worker.NotificationWorker
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** ID of notification shown when fetching notifications  */
const val NOTIFICATION_ID_FETCH_NOTIFICATION = 0

/** ID of notification shown when pruning the cache  */
const val NOTIFICATION_ID_PRUNE_CACHE = 1

/** Dynamic notification IDs start here  */
private var notificationId = NOTIFICATION_ID_PRUNE_CACHE + 1

const val REPLY_ACTION = "REPLY_ACTION"
const val KEY_REPLY = "KEY_REPLY"
const val KEY_SENDER_ACCOUNT_ID = "KEY_SENDER_ACCOUNT_ID"
const val KEY_SENDER_ACCOUNT_IDENTIFIER = "KEY_SENDER_ACCOUNT_IDENTIFIER"
const val KEY_SENDER_ACCOUNT_FULL_NAME = "KEY_SENDER_ACCOUNT_FULL_NAME"
const val KEY_NOTIFICATION_ID = "KEY_NOTIFICATION_ID"
const val KEY_CITED_STATUS_ID = "KEY_CITED_STATUS_ID"
const val KEY_VISIBILITY = "KEY_VISIBILITY"
const val KEY_SPOILER = "KEY_SPOILER"
const val KEY_MENTIONS = "KEY_MENTIONS"

/** notification channels used on Android O+ */
const val CHANNEL_MENTION = "CHANNEL_MENTION"
private const val CHANNEL_FOLLOW = "CHANNEL_FOLLOW"
private const val CHANNEL_FOLLOW_REQUEST = "CHANNEL_FOLLOW_REQUEST"
private const val CHANNEL_BOOST = "CHANNEL_BOOST"
private const val CHANNEL_FAVOURITE = "CHANNEL_FAVOURITE"
private const val CHANNEL_POLL = "CHANNEL_POLL"
private const val CHANNEL_SUBSCRIPTIONS = "CHANNEL_SUBSCRIPTIONS"
private const val CHANNEL_SIGN_UP = "CHANNEL_SIGN_UP"
private const val CHANNEL_UPDATES = "CHANNEL_UPDATES"
private const val CHANNEL_REPORT = "CHANNEL_REPORT"
private const val CHANNEL_SEVERED_RELATIONSHIPS = "CHANNEL_SEVERED_RELATIONSHIPS"
private const val CHANNEL_BACKGROUND_TASKS = "CHANNEL_BACKGROUND_TASKS"

/** WorkManager Tag */
private const val NOTIFICATION_PULL_TAG = "pullNotifications"

/** Tag for the summary notification  */
private const val GROUP_SUMMARY_TAG = BuildConfig.APPLICATION_ID + ".notification.group_summary"

/** The name of the account that caused the notification, for use in a summary  */
private const val EXTRA_ACCOUNT_NAME =
    BuildConfig.APPLICATION_ID + ".notification.extra.account_name"

/** The notification's type (string representation of a Notification.Type)  */
private const val EXTRA_NOTIFICATION_TYPE =
    BuildConfig.APPLICATION_ID + ".notification.extra.notification_type"

/**
 * Takes a given Mastodon notification and creates a new Android notification or updates the
 * existing Android notification.
 *
 * The Android notification has it's tag set to the Mastodon notification ID, and it's ID set
 * to the ID of the account that received the notification.
 *
 * @param context to access application preferences and services
 * @param mastodonNotification    a new Mastodon notification
 * @param account the account for which the notification should be shown
 * @return the new notification
 */
fun makeNotification(
    context: Context,
    notificationManager: NotificationManager,
    mastodonNotification: Notification,
    account: AccountEntity,
    isFirstOfBatch: Boolean,
): android.app.Notification {
    var notif = mastodonNotification
    notif = notif.rewriteToStatusTypeIfNeeded(account.accountId)
    val mastodonNotificationId = notif.id
    val accountId = account.id.toInt()

    // Check for an existing notification with this Mastodon Notification ID
    val activeNotifications = notificationManager.activeNotifications

    val existingAndroidNotification = activeNotifications.find { mastodonNotificationId == it.tag && accountId == it.id }?.notification

    // Notification group member
    // =========================
    notificationId++

    // Create the notification -- either create a new one, or use the existing one.
    val builder = existingAndroidNotification?.let {
        NotificationCompat.Builder(context, it)
    } ?: newAndroidNotification(context, notif, account)

    builder
        .setContentTitle(titleForType(context, notif, account))
        .setContentText(bodyForType(notif, context, account.alwaysOpenSpoiler))

    if (notif.type === Notification.Type.MENTION || notif.type === Notification.Type.POLL) {
        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(bodyForType(notif, context, account.alwaysOpenSpoiler)),
        )
    }

    // Load the avatar synchronously
    val accountAvatar = try {
        val target = Glide.with(context)
            .asBitmap()
            .load(notif.account.avatar)
            .transform(RoundedCorners(20))
            .submit()
        target.get()
    } catch (e: ExecutionException) {
        Timber.w(e, "error loading account avatar")
        BitmapFactory.decodeResource(context.resources, DR.drawable.avatar_default)
    } catch (e: InterruptedException) {
        Timber.w(e, "error loading account avatar")
        BitmapFactory.decodeResource(context.resources, DR.drawable.avatar_default)
    }
    builder.setLargeIcon(accountAvatar)

    // Reply to mention action; RemoteInput is available from KitKat Watch, but buttons are available from Nougat
    if (notif.type === Notification.Type.MENTION &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    ) {
        val replyRemoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.getString(R.string.label_quick_reply))
            .build()
        val quickReplyPendingIntent = getStatusReplyIntent(context, notif, account)
        val quickReplyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply_24dp,
            context.getString(R.string.action_quick_reply),
            quickReplyPendingIntent,
        )
            .addRemoteInput(replyRemoteInput)
            .build()
        builder.addAction(quickReplyAction)
        val composeIntent = getStatusComposeIntent(context, notif, account)
        val composeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply_24dp,
            context.getString(R.string.action_compose_shortcut),
            composeIntent,
        )
            .setShowsUserInterface(true)
            .build()
        builder.addAction(composeAction)
    }
    builder.setSubText(account.fullName)
    builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
    builder.setOnlyAlertOnce(true)

    // Add the sending account's name, so it can be used when summarising this notification
    val extras = Bundle()
    extras.putString(EXTRA_ACCOUNT_NAME, notif.account.name)
    extras.putEnum(EXTRA_NOTIFICATION_TYPE, notif.type)
    builder.addExtras(extras)

    // Only alert for the first notification of a batch to avoid multiple alerts at once
    if (!isFirstOfBatch) {
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
    }
    return builder.build()
}

/**
 * Updates the summary notifications for each notification group.
 *
 * Notifications are sent to channels. Within each channel they may be grouped, and the group
 * may have a summary.
 *
 * Pachli uses N notification channels for each account, each channel corresponds to a type
 * of notification (follow, reblog, mention, etc). Therefore each channel also has exactly
 * 0 or 1 summary notifications along with its regular notifications.
 *
 * The group key is the same as the channel ID.
 *
 * Regnerates the summary notifications for all active Pachli notifications for `account`.
 * This may delete the summary notification if there are no active notifications for that
 * account in a group.
 *
 * @see [Create a
 * notification group](https://developer.android.com/develop/ui/views/notifications/group)
 *
 * @param context to access application preferences and services
 * @param notificationManager the system's NotificationManager
 * @param account the account for which the notification should be shown
 */
fun updateSummaryNotifications(
    context: Context,
    notificationManager: NotificationManager,
    account: AccountEntity,
) {
    // Map from the channel ID to a list of notifications in that channel. Those are the
    // notifications that will be summarised.
    val channelGroups: MutableMap<String?, MutableList<StatusBarNotification>> = HashMap()
    val accountId = account.id.toInt()

    // Initialise the map with all channel IDs.
    Notification.Type.entries.forEach {
        channelGroups[getChannelId(account, it)] = ArrayList()
    }

    // Fetch all existing notifications. Add them to the map, ignoring notifications that:
    // - belong to a different account
    // - are summary notifications
    for (sn in notificationManager.activeNotifications) {
        if (sn.id != accountId) continue
        val channelId = sn.notification.group
        val summaryTag = "$GROUP_SUMMARY_TAG.$channelId"
        if (summaryTag == sn.tag) continue

        // TODO: API 26 supports getting the channel ID directly (sn.getNotification().getChannelId()).
        // This works here because the channelId and the groupKey are the same.
        val members = channelGroups[channelId]
        if (members == null) { // can't happen, but just in case...
            Timber.e("members == null for channel ID %s", channelId)
            continue
        }
        members.add(sn)
    }

    // Create, update, or cancel the summary notifications for each group.
    for ((channelId, members) in channelGroups) {
        val summaryTag = "$GROUP_SUMMARY_TAG.$channelId"

        // If there are 0-1 notifications in this group then the additional summary
        // notification is not needed and can be cancelled.
        if (members.size <= 1) {
            notificationManager.cancel(summaryTag, accountId)
            continue
        }

        // Create a notification that summarises the other notifications in this group

        // All notifications in this group have the same type, so get it from the first.
        val notificationType = members[0].notification.extras.getEnum<Notification.Type>(EXTRA_NOTIFICATION_TYPE)
        val summaryResultIntent = MainActivityIntent.openNotification(
            context,
            accountId.toLong(),
            notificationType,
        )
        val summaryStackBuilder = TaskStackBuilder.create(context)
        summaryStackBuilder.addParentStack(MainActivity::class.java)
        summaryStackBuilder.addNextIntent(summaryResultIntent)
        val summaryResultPendingIntent = summaryStackBuilder.getPendingIntent(
            (notificationId + account.id * 10000).toInt(),
            pendingIntentFlags(false),
        )
        val title = context.resources.getQuantityString(
            R.plurals.notification_title_summary,
            members.size,
            members.size,
        )
        val text = joinNames(context, members)
        val summaryBuilder = NotificationCompat.Builder(context, channelId!!)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(summaryResultPendingIntent)
            .setColor(context.getColor(DR.color.notification_color))
            .setAutoCancel(true)
            .setShortcutId(account.id.toString())
            .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(account.fullName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(true)
            .setGroup(channelId)
            .setGroupSummary(true)
        setSoundVibrationLight(account, summaryBuilder)

        // TODO: Use the batch notification API available in NotificationManagerCompat
        // 1.11 and up (https://developer.android.com/jetpack/androidx/releases/core#1.11.0-alpha01)
        // when it is released.
        notificationManager.notify(summaryTag, accountId, summaryBuilder.build())

        // Android will rate limit / drop notifications if they're posted too
        // quickly. There is no indication to the user that this happened.
        // See https://github.com/tuskyapp/Tusky/pull/3626#discussion_r1192963664
        try {
            Thread.sleep(1000)
        } catch (ignored: InterruptedException) {
        }
    }
}

private fun newAndroidNotification(
    context: Context,
    body: Notification,
    account: AccountEntity,
): NotificationCompat.Builder {
    val eventResultIntent = MainActivityIntent.openNotification(context, account.id, body.type)
    val eventStackBuilder = TaskStackBuilder.create(context)
    eventStackBuilder.addParentStack(MainActivity::class.java)
    eventStackBuilder.addNextIntent(eventResultIntent)
    val eventResultPendingIntent = eventStackBuilder.getPendingIntent(
        account.id.toInt(),
        pendingIntentFlags(false),
    )
    val channelId = getChannelId(account, body)!!
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentIntent(eventResultPendingIntent)
        .setColor(context.getColor(DR.color.notification_color))
        .setGroup(channelId)
        .setAutoCancel(true)
        .setShortcutId(account.id.toString())
        .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
    setSoundVibrationLight(account, builder)
    return builder
}

private fun getStatusReplyIntent(
    context: Context,
    body: Notification,
    account: AccountEntity,
): PendingIntent {
    val status = body.status!!
    val inReplyToId = status.id
    val (_, _, account1, _, _, _, _, _, _, _, _, _, _, _, _, _, _, contentWarning, replyVisibility, _, mentions) = status.actionableStatus
    var mentionedUsernames: MutableList<String?> = ArrayList()
    mentionedUsernames.add(account1.username)
    for ((_, _, username) in mentions) {
        mentionedUsernames.add(username)
    }
    mentionedUsernames.removeAll(setOf(account.username))
    mentionedUsernames = ArrayList(LinkedHashSet(mentionedUsernames))

    // TODO: Revisit suppressing this when this file is moved
    @SuppressLint("IntentDetector")
    val replyIntent = Intent(context, SendStatusBroadcastReceiver::class.java)
        .setAction(REPLY_ACTION)
        .putExtra(KEY_SENDER_ACCOUNT_ID, account.id)
        .putExtra(KEY_SENDER_ACCOUNT_IDENTIFIER, account.identifier)
        .putExtra(KEY_SENDER_ACCOUNT_FULL_NAME, account.fullName)
        .putExtra(KEY_NOTIFICATION_ID, notificationId)
        .putExtra(KEY_CITED_STATUS_ID, inReplyToId)
        .putExtra(KEY_VISIBILITY, replyVisibility)
        .putExtra(KEY_SPOILER, contentWarning)
        .putExtra(KEY_MENTIONS, mentionedUsernames.toTypedArray())
    return PendingIntent.getBroadcast(
        context.applicationContext,
        notificationId,
        replyIntent,
        pendingIntentFlags(true),
    )
}

private fun getStatusComposeIntent(
    context: Context,
    body: Notification,
    account: AccountEntity,
): PendingIntent {
    val status = body.status!!
    val citedLocalAuthor = status.account.localUsername
    val citedText = status.content.parseAsMastodonHtml().toString()
    val inReplyToId = status.id
    val (_, _, account1, _, _, _, _, _, _, _, _, _, _, _, _, _, _, contentWarning, replyVisibility, _, mentions, _, _, _, _, _, _, language) = status.actionableStatus
    val mentionedUsernames: MutableSet<String> = LinkedHashSet()
    mentionedUsernames.add(account1.username)
    for ((_, _, mentionedUsername) in mentions) {
        if (mentionedUsername != account.username) {
            mentionedUsernames.add(mentionedUsername)
        }
    }
    val composeOptions = ComposeOptions(
        inReplyToId = inReplyToId,
        replyVisibility = replyVisibility,
        contentWarning = contentWarning,
        replyingStatusAuthor = citedLocalAuthor,
        replyingStatusContent = citedText,
        mentionedUsernames = mentionedUsernames,
        modifiedInitialState = true,
        language = language,
        kind = ComposeOptions.ComposeKind.NEW,
    )
    val composeIntent = MainActivityIntent.openCompose(
        context,
        composeOptions,
        account.id,
        body.id,
        account.id.toInt(),
    )
    return PendingIntent.getActivity(
        context.applicationContext,
        notificationId,
        composeIntent,
        pendingIntentFlags(false),
    )
}

/**
 * Creates a notification channel for notifications for background work that should not
 * disturb the user.
 *
 * @param context context
 */
fun createWorkerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHANNEL_BACKGROUND_TASKS,
        context.getString(R.string.notification_listenable_worker_name),
        NotificationManager.IMPORTANCE_NONE,
    )
    channel.description = context.getString(R.string.notification_listenable_worker_description)
    channel.enableLights(false)
    channel.enableVibration(false)
    channel.setShowBadge(false)
    notificationManager.createNotificationChannel(channel)
}

/**
 * Creates a notification for a background worker.
 *
 * @param context context
 * @param titleResource String resource to use as the notification's title
 * @return the notification
 */
fun createWorkerNotification(
    context: Context,
    @StringRes titleResource: Int,
): android.app.Notification {
    val title = context.getString(titleResource)
    return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_TASKS)
        .setContentTitle(title)
        .setTicker(title)
        .setSmallIcon(R.drawable.ic_notify)
        .setOngoing(true)
        .build()
}

fun createNotificationChannelsForAccount(account: AccountEntity, context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelIds = arrayOf(
            CHANNEL_MENTION + account.identifier,
            CHANNEL_FOLLOW + account.identifier,
            CHANNEL_FOLLOW_REQUEST + account.identifier,
            CHANNEL_BOOST + account.identifier,
            CHANNEL_FAVOURITE + account.identifier,
            CHANNEL_POLL + account.identifier,
            CHANNEL_SUBSCRIPTIONS + account.identifier,
            CHANNEL_SIGN_UP + account.identifier,
            CHANNEL_UPDATES + account.identifier,
            CHANNEL_REPORT + account.identifier,
            CHANNEL_SEVERED_RELATIONSHIPS + account.identifier,
        )
        val channelNames = intArrayOf(
            R.string.notification_mention_name,
            R.string.notification_follow_name,
            R.string.notification_follow_request_name,
            R.string.notification_boost_name,
            R.string.notification_favourite_name,
            R.string.notification_poll_name,
            R.string.notification_subscription_name,
            R.string.notification_sign_up_name,
            R.string.notification_update_name,
            R.string.notification_report_name,
            R.string.notification_severed_relationships_name,
        )
        val channelDescriptions = intArrayOf(
            R.string.notification_mention_descriptions,
            R.string.notification_follow_description,
            R.string.notification_follow_request_description,
            R.string.notification_boost_description,
            R.string.notification_favourite_description,
            R.string.notification_poll_description,
            R.string.notification_subscription_description,
            R.string.notification_sign_up_description,
            R.string.notification_update_description,
            R.string.notification_report_description,
            R.string.notification_severed_relationships_description,
        )
        val channels: MutableList<NotificationChannel> = ArrayList(6)
        val channelGroup = NotificationChannelGroup(account.identifier, account.fullName)
        notificationManager.createNotificationChannelGroup(channelGroup)
        for (i in channelIds.indices) {
            val id = channelIds[i]
            val name = context.getString(channelNames[i])
            val description = context.getString(channelDescriptions[i])
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(id, name, importance)
            channel.description = description
            channel.enableLights(true)
            channel.lightColor = -0xd46f27
            channel.enableVibration(true)
            channel.setShowBadge(true)
            channel.group = account.identifier
            channels.add(channel)
        }
        notificationManager.createNotificationChannels(channels)
    }
}

fun deleteNotificationChannelsForAccount(account: AccountEntity, context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.deleteNotificationChannelGroup(account.identifier)
    }
}

fun enablePullNotifications(context: Context) {
    Timber.i("Enabling pull notifications for all accounts")
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWorkByTag(NOTIFICATION_PULL_TAG)

    // Periodic work requests are supposed to start running soon after being enqueued. In
    // practice that may not be soon enough, so create and enqueue an expedited one-time
    // request to get new notifications immediately.
    Timber.d("Enqueing immediate notification worker")
    val fetchNotifications: WorkRequest = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
        .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    workManager.enqueue(fetchNotifications)
    val workRequest: WorkRequest = PeriodicWorkRequest.Builder(
        NotificationWorker::class.java,
        PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS,
        PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
        TimeUnit.MILLISECONDS,
    )
        .addTag(NOTIFICATION_PULL_TAG)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(5, TimeUnit.MINUTES)
        .build()
    workManager.enqueue(workRequest)
    Timber.d("enabled notification checks with %d ms interval", PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)
    NotificationConfig.notificationMethod = NotificationConfig.Method.Pull
}

fun disablePullNotifications(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag(NOTIFICATION_PULL_TAG)
    Timber.w("Disabling pull notifications for all accounts")
    NotificationConfig.notificationMethod = NotificationConfig.Method.Unknown
}

fun clearNotificationsForAccount(context: Context, account: AccountEntity) {
    val accountId = account.id.toInt()
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    for (androidNotification in notificationManager.activeNotifications) {
        if (accountId == androidNotification.id) {
            notificationManager.cancel(androidNotification.tag, androidNotification.id)
        }
    }
}

/**
 * Returns true if [account] is **not** filtering notifications of [type],
 * otherwise false.
 */
fun filterNotification(
    notificationManager: NotificationManager,
    account: AccountEntity,
    type: Notification.Type,
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = getChannelId(account, type)
            ?: // unknown notificationtype
            return false
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE
    }
    return when (type) {
        Notification.Type.MENTION -> account.notificationsMentioned
        Notification.Type.STATUS -> account.notificationsSubscriptions
        Notification.Type.FOLLOW -> account.notificationsFollowed
        Notification.Type.FOLLOW_REQUEST -> account.notificationsFollowRequested
        Notification.Type.REBLOG -> account.notificationsReblogged
        Notification.Type.FAVOURITE -> account.notificationsFavorited
        Notification.Type.POLL -> account.notificationsPolls
        Notification.Type.SIGN_UP -> account.notificationsSignUps
        Notification.Type.UPDATE -> account.notificationsUpdates
        Notification.Type.REPORT -> account.notificationsReports
        Notification.Type.SEVERED_RELATIONSHIPS -> account.notificationsSeveredRelationships
        Notification.Type.UNKNOWN -> false
    }
}

private fun getChannelId(account: AccountEntity, notification: Notification): String? {
    return getChannelId(account, notification.type)
}

private fun getChannelId(account: AccountEntity, type: Notification.Type): String? {
    return when (type) {
        Notification.Type.MENTION -> CHANNEL_MENTION + account.identifier
        Notification.Type.STATUS -> CHANNEL_SUBSCRIPTIONS + account.identifier
        Notification.Type.FOLLOW -> CHANNEL_FOLLOW + account.identifier
        Notification.Type.FOLLOW_REQUEST -> CHANNEL_FOLLOW_REQUEST + account.identifier
        Notification.Type.REBLOG -> CHANNEL_BOOST + account.identifier
        Notification.Type.FAVOURITE -> CHANNEL_FAVOURITE + account.identifier
        Notification.Type.POLL -> CHANNEL_POLL + account.identifier
        Notification.Type.SIGN_UP -> CHANNEL_SIGN_UP + account.identifier
        Notification.Type.UPDATE -> CHANNEL_UPDATES + account.identifier
        Notification.Type.REPORT -> CHANNEL_REPORT + account.identifier
        Notification.Type.SEVERED_RELATIONSHIPS -> CHANNEL_SEVERED_RELATIONSHIPS + account.identifier
        Notification.Type.UNKNOWN -> null
    }
}

private fun setSoundVibrationLight(
    account: AccountEntity,
    builder: NotificationCompat.Builder,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return // do nothing on Android O or newer, the system uses the channel settings anyway
    }
    if (account.notificationSound) {
        builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
    }
    if (account.notificationVibration) {
        builder.setVibrate(longArrayOf(500, 500))
    }
    if (account.notificationLight) {
        builder.setLights(-0xd46f27, 300, 1000)
    }
}

private fun wrapItemAt(notification: StatusBarNotification): String {
    return notification.notification.extras.getString(EXTRA_ACCOUNT_NAME)
        .unicodeWrap() // getAccount().getName());
}

private fun joinNames(context: Context, notifications: List<StatusBarNotification>): String? {
    if (notifications.size > 3) {
        val length = notifications.size
        return String.format(
            context.getString(R.string.notification_summary_large),
            wrapItemAt(notifications[length - 1]),
            wrapItemAt(notifications[length - 2]),
            wrapItemAt(notifications[length - 3]),
            length - 3,
        )
    } else if (notifications.size == 3) {
        return String.format(
            context.getString(R.string.notification_summary_medium),
            wrapItemAt(notifications[2]),
            wrapItemAt(notifications[1]),
            wrapItemAt(notifications[0]),
        )
    } else if (notifications.size == 2) {
        return String.format(
            context.getString(R.string.notification_summary_small),
            wrapItemAt(notifications[1]),
            wrapItemAt(notifications[0]),
        )
    }
    return null
}

private fun titleForType(
    context: Context,
    notification: Notification,
    account: AccountEntity,
): String? {
    val accountName = notification.account.name.unicodeWrap()
    return when (notification.type) {
        Notification.Type.MENTION -> {
            context.getString(R.string.notification_mention_format, accountName)
        }

        Notification.Type.STATUS -> {
            context.getString(R.string.notification_subscription_format, accountName)
        }

        Notification.Type.FOLLOW -> {
            context.getString(R.string.notification_follow_format, accountName)
        }

        Notification.Type.FOLLOW_REQUEST -> {
            context.getString(R.string.notification_follow_request_format, accountName)
        }

        Notification.Type.FAVOURITE -> {
            context.getString(R.string.notification_favourite_format, accountName)
        }

        Notification.Type.REBLOG -> {
            context.getString(R.string.notification_reblog_format, accountName)
        }

        Notification.Type.POLL -> {
            val status = notification.status!!
            if (status.account.id == account.accountId) {
                context.getString(R.string.poll_ended_created)
            } else {
                context.getString(R.string.poll_ended_voted)
            }
        }

        Notification.Type.SIGN_UP -> {
            context.getString(R.string.notification_sign_up_format, accountName)
        }

        Notification.Type.UPDATE -> {
            context.getString(R.string.notification_update_format, accountName)
        }

        Notification.Type.REPORT -> {
            context.getString(R.string.notification_report_format, account.domain)
        }

        Notification.Type.SEVERED_RELATIONSHIPS -> {
            context.getString(
                R.string.notification_severed_relationships_format,
                notification.relationshipSeveranceEvent?.targetName,
            )
        }

        Notification.Type.UNKNOWN -> null
    }
}

private fun bodyForType(
    notification: Notification,
    context: Context,
    alwaysOpenSpoiler: Boolean,
): String? {
    when (notification.type) {
        Notification.Type.FOLLOW, Notification.Type.FOLLOW_REQUEST, Notification.Type.SIGN_UP -> {
            return "@" + notification.account.username
        }

        Notification.Type.MENTION, Notification.Type.FAVOURITE, Notification.Type.REBLOG, Notification.Type.STATUS -> {
            val status = notification.status!!
            return if (!TextUtils.isEmpty(status.spoilerText) && !alwaysOpenSpoiler) {
                status.spoilerText
            } else {
                status.content.parseAsMastodonHtml().toString()
            }
        }

        Notification.Type.POLL -> {
            val status = notification.status!!
            return if (!TextUtils.isEmpty(status.spoilerText) && !alwaysOpenSpoiler) {
                status.spoilerText
            } else {
                val builder = StringBuilder(status.content.parseAsMastodonHtml())
                builder.append('\n')
                val poll = status.poll!!
                val options = poll.options
                for (i in options.indices) {
                    val (title, votesCount) = options[i]
                    builder.append(
                        buildDescription(
                            title,
                            calculatePercent(votesCount, poll.votersCount, poll.votesCount),
                            poll.ownVotes != null && poll.ownVotes!!.contains(i),
                            context,
                        ),
                    )
                    builder.append('\n')
                }
                builder.toString()
            }
        }

        Notification.Type.REPORT -> {
            val report = notification.report!!
            return context.getString(
                R.string.notification_header_report_format,
                notification.account.name.unicodeWrap(),
                report.targetAccount.name.unicodeWrap(),
            )
        }
        Notification.Type.SEVERED_RELATIONSHIPS -> {
            val resourceId = when (notification.relationshipSeveranceEvent!!.type) {
                RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> R.string.notification_severed_relationships_domain_block_body
                RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> R.string.notification_severed_relationships_user_domain_block_body
                RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> R.string.notification_severed_relationships_account_suspension_body
                RelationshipSeveranceEvent.Type.UNKNOWN -> R.string.notification_severed_relationships_unknown_body
            }
            return context.getString(resourceId)
        }

        Notification.Type.UNKNOWN -> return null
        Notification.Type.UPDATE -> return null
    }
}

fun pendingIntentFlags(mutable: Boolean): Int {
    return if (mutable) {
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}

/**
 * Returns the enum associated with the given [key].
 *
 * @throws IllegalStateException if the value at [key] is not valid for the enum [T].
 */
inline fun <reified T : Enum<T>> Bundle.getEnum(key: String) =
    getInt(key, -1).let { if (it >= 0) enumValues<T>()[it] else throw IllegalStateException("unrecognised enum ordinal: $it") }

/**
 * Inserts an enum [value] into the mapping of this [Bundle], replacing any
 * existing value for the given [key].
 */
fun <T : Enum<T>> Bundle.putEnum(key: String, value: T?) =
    putInt(key, value?.ordinal ?: -1)
