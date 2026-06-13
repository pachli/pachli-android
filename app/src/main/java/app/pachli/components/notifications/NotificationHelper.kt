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
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.text.HtmlCompat
import androidx.core.text.htmlEncode
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pachli.BuildConfig
import app.pachli.MainActivity
import app.pachli.R
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.createDraftReply
import app.pachli.core.database.model.AccountIdentifier
import app.pachli.core.database.model.PachliAccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.domain.notifications.NotificationConfig
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AccountFilterReason
import app.pachli.core.model.AccountWarning
import app.pachli.core.model.Draft
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Notification
import app.pachli.core.model.RelationshipSeveranceEvent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.IntentRouterActivityIntent
import app.pachli.core.navigation.pendingIntentFlags
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.buildDescription
import app.pachli.core.ui.calculatePercent
import app.pachli.core.worker.NOTIFICATION_ID_PRUNE_CACHE
import app.pachli.receiver.SendStatusBroadcastReceiver
import app.pachli.worker.NotificationWorker
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** ID of notification shown when fetching notifications  */
const val NOTIFICATION_ID_FETCH_NOTIFICATION = 0

/** Dynamic notification IDs start here  */
private var notificationId = NOTIFICATION_ID_PRUNE_CACHE + 1

const val REPLY_ACTION = "REPLY_ACTION"
const val KEY_REPLY = "KEY_REPLY"
const val KEY_SENDER_ACCOUNT_ID = "KEY_SENDER_ACCOUNT_ID"
const val KEY_SENDER_ACCOUNT_IDENTIFIER = "KEY_SENDER_ACCOUNT_IDENTIFIER"
const val KEY_SENDER_ACCOUNT_FULL_NAME = "KEY_SENDER_ACCOUNT_FULL_NAME"

/** Key to return the server ID of the notification, equivalent to [Notification.id]. */
const val KEY_SERVER_NOTIFICATION_ID = "KEY_SERVER_NOTIFICATION_ID"

/** Key to return the [Draft]. */
const val KEY_DRAFT = "KEY_DRAFT"

/**
 * Notification channels for per-account Mastodon notifications, used on
 * Android O+
 *
 * @property baseId Base ID for the channel. Notification channels are created
 * per account, use [channelId] to get the full channel ID.
 * @property nameRes Resource identifier for the notification channel name
 * @property descriptionRes Resource identifier for the notification
 * channel description
 */
enum class PachliNotificationChannels(
    private val baseId: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
) {
    MENTION("CHANNEL_MENTION", R.string.notification_mention_name, R.string.notification_mention_descriptions),
    FOLLOW("CHANNEL_FOLLOW", R.string.notification_follow_name, R.string.notification_follow_description),
    FOLLOW_REQUEST("CHANNEL_FOLLOW_REQUEST", R.string.notification_follow_request_name, R.string.notification_follow_request_description),
    REBLOG("CHANNEL_BOOST", R.string.notification_boost_name, R.string.notification_boost_description),
    FAVOURITE("CHANNEL_FAVOURITE", R.string.notification_favourite_name, R.string.notification_favourite_description),
    POLL("CHANNEL_POLL", R.string.notification_poll_name, R.string.notification_poll_description),
    SUBSCRIPTIONS("CHANNEL_SUBSCRIPTIONS", R.string.notification_subscription_name, R.string.notification_subscription_description),
    SIGN_UP("CHANNEL_SIGN_UP", R.string.notification_sign_up_name, R.string.notification_sign_up_description),
    UPDATES("CHANNEL_UPDATES", R.string.notification_update_name, R.string.notification_update_description),
    REPORT("CHANNEL_REPORT", R.string.notification_report_name, R.string.notification_report_description),
    SEVERED_RELATIONSHIPS("CHANNEL_SEVERED_RELATIONSHIPS", R.string.notification_severed_relationships_name, R.string.notification_severed_relationships_description),
    MODERATION_WARNINGS("CHANNEL_MODERATION_WARNING", R.string.notification_moderation_warnings_name, R.string.notification_moderation_warnings_description),
    QUOTE("CHANNEL_QUOTE", R.string.notification_quote_name, R.string.notification_quote_description),
    QUOTED_UPDATE("CHANNEL_QUOTED_UPDATE", R.string.notification_quoted_update_name, R.string.notification_quoted_update_description),

    ;

    /**
     * @return The full ID for this channel for the account identified
     * by [accountIdentifier].
     */
    fun channelId(accountIdentifier: AccountIdentifier) = baseId + accountIdentifier
}

/** WorkManager Tag */
private const val NOTIFICATION_PULL_TAG = "pullNotifications"

/** Tag for the summary notification  */
private const val GROUP_SUMMARY_TAG = BuildConfig.APPLICATION_ID + ".notification.group_summary"

/** The name of the account that caused the notification, for use in a summary  */
private const val EXTRA_ACCOUNT_NAME = BuildConfig.APPLICATION_ID + ".notification.extra.account_name"

/** The notification's type. */
private const val EXTRA_NOTIFICATION_TYPE = BuildConfig.APPLICATION_ID + ".notification.extra.notification_type"

/**
 * Takes a given Mastodon notification and creates a new Android notification or updates the
 * existing Android notification.
 *
 * The Android notification tag is the Mastodon notification ID, and the notification ID
 * is the ID of the account that received the notification.
 *
 * @param context to access application preferences and services
 * @param mastodonNotification Mastodon [Notification]
 * @param pachliAccountEntity the account for which the notification should be shown
 * @return the new notification
 */
fun makeNotification(
    context: Context,
    notificationManager: NotificationManager,
    mastodonNotification: Notification,
    pachliAccountEntity: PachliAccountEntity,
    isFirstOfBatch: Boolean,
): android.app.Notification {
    val mastodonNotificationId = mastodonNotification.id
    val accountId = pachliAccountEntity.id.toInt()

    // Check for an existing notification with this Mastodon Notification ID
    val activeNotifications = notificationManager.activeNotifications

    val existingAndroidNotification = activeNotifications.find { mastodonNotificationId == it.tag && accountId == it.id }?.notification

    // Notification group member
    // =========================
    notificationId++

    // Create the Android notification -- either create a new one, or use the existing one.
    val androidNotificationBuilder = existingAndroidNotification?.let {
        NotificationCompat.Builder(context, it)
    } ?: newAndroidNotification(context, notificationId, mastodonNotification, pachliAccountEntity)

    androidNotificationBuilder
        .setContentTitle(titleForType(context, mastodonNotification, pachliAccountEntity))
        .setContentText(bodyForType(mastodonNotification, context, pachliAccountEntity.alwaysOpenSpoiler))

    if (mastodonNotification is Notification.Mention || mastodonNotification is Notification.Poll) {
        androidNotificationBuilder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(bodyForType(mastodonNotification, context, pachliAccountEntity.alwaysOpenSpoiler)),
        )
    }

    // Load the avatar synchronously
    val accountAvatar = try {
        Glide.with(context)
            .asBitmap()
            .load(mastodonNotification.account.avatar)
            .transform(RoundedCorners(20))
            .submit()
            .get()
    } catch (e: ExecutionException) {
        Timber.w(e, "error loading account avatar")
        BitmapFactory.decodeResource(context.resources, DR.drawable.avatar_default)
    } catch (e: InterruptedException) {
        Timber.w(e, "error loading account avatar")
        BitmapFactory.decodeResource(context.resources, DR.drawable.avatar_default)
    }
    androidNotificationBuilder.setLargeIcon(accountAvatar)

    // Reply to mention action; RemoteInput is available from KitKat Watch, but buttons are available from Nougat
    if (mastodonNotification is Notification.Mention && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val replyRemoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.getString(R.string.label_quick_reply))
            .build()
        val quickReplyPendingIntent = getStatusReplyIntent(context, mastodonNotification, pachliAccountEntity)
        val quickReplyAction = NotificationCompat.Action.Builder(
            app.pachli.core.ui.R.drawable.ic_reply_24dp,
            context.getString(R.string.action_quick_reply),
            quickReplyPendingIntent,
        )
            .addRemoteInput(replyRemoteInput)
            .build()
        androidNotificationBuilder.addAction(quickReplyAction)
        val composeIntent = getStatusComposeIntent(context, mastodonNotification, pachliAccountEntity)
        val composeAction = NotificationCompat.Action.Builder(
            app.pachli.core.ui.R.drawable.ic_reply_24dp,
            context.getString(R.string.action_compose_shortcut),
            composeIntent,
        )
            .setShowsUserInterface(true)
            .build()
        androidNotificationBuilder.addAction(composeAction)
    }

    androidNotificationBuilder.setSubText(pachliAccountEntity.fullName)
    androidNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    androidNotificationBuilder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
    androidNotificationBuilder.setOnlyAlertOnce(true)

    // Add the sending account's name, so it can be used when summarising this notification
    val extras = Bundle()
    extras.putString(EXTRA_ACCOUNT_NAME, mastodonNotification.account.name)
    extras.putEnum(EXTRA_NOTIFICATION_TYPE, mastodonNotification.type)
    androidNotificationBuilder.addExtras(extras)

    // Only alert for the first notification of a batch to avoid multiple alerts at once
    if (!isFirstOfBatch) {
        androidNotificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
    }
    return androidNotificationBuilder.build()
}

/**
 * Regnerates the summary notifications for all active Pachli notifications for `account`.
 * This may delete the summary notification if there are no active notifications for that
 * account in a group.
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
 * @see [Create a
 * notification group](https://developer.android.com/develop/ui/views/notifications/group)
 *
 * @param context to access application preferences and services
 * @param notificationManager the system's NotificationManager
 * @param pachliAccountEntity the account for which the notification should be shown
 */
fun updateSummaryNotifications(
    context: Context,
    notificationManager: NotificationManager,
    pachliAccountEntity: PachliAccountEntity,
) {
    // Map from the channel ID to a list of notifications in that channel. Those are the
    // notifications that will be summarised.
    val channelGroups = buildMap {
        PachliNotificationChannels.entries.forEach {
            put(it.channelId(pachliAccountEntity.identifier), ArrayList<StatusBarNotification>())
        }
    }

    val accountId = pachliAccountEntity.id.toInt()

    // Fetch all existing notifications. Add them to the map, ignoring notifications that:
    // - belong to a different account
    // - are summary notifications
    for (sn in notificationManager.activeNotifications) {
        if (sn.id != accountId) continue
        val channelId = sn.notification.group
        val summaryNotificationTag = "$GROUP_SUMMARY_TAG.$channelId"
        if (summaryNotificationTag == sn.tag) continue

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
        val summaryNotificationTag = "$GROUP_SUMMARY_TAG.$channelId"

        // If there are 0-1 notifications in this group then the additional summary
        // notification is not needed and can be cancelled.
        if (members.size <= 1) {
            notificationManager.cancel(summaryNotificationTag, accountId)
            continue
        }

        // Create a notification that summarises the other notifications in this group

        // All notifications in this group have the same type, so get it from the first.
        val notificationType = members[0].notification.extras.getEnum<Notification.Type>(EXTRA_NOTIFICATION_TYPE)
        val summaryResultIntent = IntentRouterActivityIntent.fromNotification(
            context,
            pachliAccountEntity.id,
            -1,
            null,
            notificationType = notificationType,
        )
        val summaryStackBuilder = TaskStackBuilder.create(context)
        summaryStackBuilder.addParentStack(MainActivity::class.java)
        summaryStackBuilder.addNextIntent(summaryResultIntent)
        val summaryResultPendingIntent = summaryStackBuilder.getPendingIntent(
            (notificationId + pachliAccountEntity.id * 10000).toInt(),
            pendingIntentFlags(false),
        )
        val title = context.resources.getQuantityString(
            R.plurals.notification_title_summary,
            members.size,
            members.size,
        )
        val text = joinNames(context, members)
        val summaryBuilder = NotificationCompat.Builder(context, channelId!!)
            .setSmallIcon(app.pachli.core.common.R.drawable.ic_notify)
            .setContentIntent(summaryResultPendingIntent)
            .setColor(context.getColor(DR.color.notification_color))
            .setAutoCancel(true)
            .setShortcutId(pachliAccountEntity.id.toString())
            .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(pachliAccountEntity.fullName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(true)
            .setGroup(channelId)
            .setGroupSummary(true)
        setSoundVibrationLight(pachliAccountEntity, summaryBuilder)

        // TODO: Use the batch notification API available in NotificationManagerCompat
        // 1.11 and up (https://developer.android.com/jetpack/androidx/releases/core#1.11.0-alpha01)
        // when it is released.
        notificationManager.notify(summaryNotificationTag, accountId, summaryBuilder.build())

        // Android will rate limit / drop notifications if they're posted too
        // quickly. There is no indication to the user that this happened.
        // See https://github.com/tuskyapp/Tusky/pull/3626#discussion_r1192963664
        try {
            Thread.sleep(1000)
        } catch (_: InterruptedException) {
        }
    }
}

private fun newAndroidNotification(
    context: Context,
    notificationId: Int,
    notification: Notification,
    pachliAccountEntity: PachliAccountEntity,
): NotificationCompat.Builder {
    val eventResultIntent = IntentRouterActivityIntent.fromNotification(
        context,
        pachliAccountEntity.id,
        notificationId,
        notification.id,
        notification.type,
    )
    val eventStackBuilder = TaskStackBuilder.create(context)
    eventStackBuilder.addParentStack(MainActivity::class.java)
    eventStackBuilder.addNextIntent(eventResultIntent)
    val eventResultPendingIntent = eventStackBuilder.getPendingIntent(
        pachliAccountEntity.id.toInt(),
        pendingIntentFlags(false),
    )
    val channelId = getChannelId(pachliAccountEntity, notification)!!
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(app.pachli.core.common.R.drawable.ic_notify)
        .setContentIntent(eventResultPendingIntent)
        .setColor(context.getColor(DR.color.notification_color))
        .setGroup(channelId)
        .setAutoCancel(true)
        .setShortcutId(pachliAccountEntity.id.toString())
        .setDefaults(0) // So it doesn't ring twice, notify only in Target callback
    setSoundVibrationLight(pachliAccountEntity, builder)
    return builder
}

private fun getStatusReplyIntent(
    context: Context,
    body: Notification.WithStatus,
    pachliAccountEntity: PachliAccountEntity,
): PendingIntent {
    val status = body.status

    val draft = Draft.createDraftReply(pachliAccountEntity, status.actionableStatus)

    // TODO: Revisit suppressing this when this file is moved
    @SuppressLint("IntentDetector")
    val replyIntent = Intent(context, SendStatusBroadcastReceiver::class.java)
        .setAction(REPLY_ACTION)
        .putExtra(KEY_DRAFT, draft)
        .putExtra(KEY_SENDER_ACCOUNT_ID, pachliAccountEntity.id)
        // Required
        .putExtra(KEY_SENDER_ACCOUNT_IDENTIFIER, pachliAccountEntity.identifier as Parcelable)
        .putExtra(KEY_SENDER_ACCOUNT_FULL_NAME, pachliAccountEntity.fullName)
        .putExtra(KEY_SERVER_NOTIFICATION_ID, body.id)
    return PendingIntent.getBroadcast(
        context.applicationContext,
        notificationId,
        replyIntent,
        pendingIntentFlags(true),
    )
}

private fun getStatusComposeIntent(
    context: Context,
    body: Notification.WithStatus,
    pachliAccountEntity: PachliAccountEntity,
): PendingIntent {
    val status = body.status

    val draft = Draft.createDraftReply(pachliAccountEntity, status.actionableStatus)
    val composeOptions = ComposeOptions(
        draft = draft,
        referencingStatus = ComposeOptions.ReferencingStatus.ReplyingTo.from(status.actionableStatus),
    )
    val composeIntent = IntentRouterActivityIntent.fromNotificationCompose(
        context,
        pachliAccountEntity.id,
        composeOptions,
        pachliAccountEntity.id.toInt(),
        body.id,
    )
    return PendingIntent.getActivity(
        context.applicationContext,
        notificationId,
        composeIntent,
        pendingIntentFlags(false),
    )
}

fun createNotificationChannelsForAccount(account: PachliAccountEntity, context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelGroup = NotificationChannelGroup(account.identifier.value, account.fullName)
        notificationManager.createNotificationChannelGroup(channelGroup)

        val channels = PachliNotificationChannels.entries.map {
            val id = it.channelId(account.identifier)
            val name = context.getString(it.nameRes)
            val description = context.getString(it.descriptionRes)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(id, name, importance)
            channel.description = description
            channel.enableLights(true)
            channel.lightColor = -0xd46f27
            channel.enableVibration(true)
            channel.setShowBadge(true)
            channel.group = account.identifier.value
            channel
        }
        notificationManager.createNotificationChannels(channels)
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
    val fetchNotifications = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    workManager.enqueue(fetchNotifications)
    val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
        PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS,
        PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
        TimeUnit.MILLISECONDS,
    )
        .addTag(NOTIFICATION_PULL_TAG)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(5, TimeUnit.MINUTES)
        .build()
    workManager.enqueueUniquePeriodicWork(
        NOTIFICATION_PULL_TAG,
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest,
    )
    Timber.d("enabled notification checks with %d ms interval", PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)
    NotificationConfig.notificationMethod = NotificationConfig.Method.Pull
}

fun disablePullNotifications(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag(NOTIFICATION_PULL_TAG)
    Timber.w("Disabling pull notifications for all accounts")
    NotificationConfig.notificationMethod = NotificationConfig.Method.Unknown
}

fun clearNotificationsForAccount(context: Context, pachliAccountId: Long) {
    val accountId = pachliAccountId.toInt()
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    for (androidNotification in notificationManager.activeNotifications) {
        if (accountId == androidNotification.id) {
            notificationManager.cancel(androidNotification.tag, androidNotification.id)
        }
    }
}

/**
 * Returns true if [account] is **not** filtering notifications of [notification],
 * otherwise false.
 */
fun filterNotification(
    notificationManager: NotificationManager,
    account: PachliAccountEntity,
    notification: Notification,
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // unknown notificationtype
        val channelId = getChannelId(account, notification) ?: return false
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel != null && channel.importance > NotificationManager.IMPORTANCE_NONE
    }
    return when (notification) {
        is Notification.Mention -> account.notificationsMentioned
        is Notification.Status -> account.notificationsSubscriptions
        is Notification.Follow -> account.notificationsFollowed
        is Notification.FollowRequest -> account.notificationsFollowRequested
        is Notification.Reblog -> account.notificationsReblogged
        is Notification.Favourite -> account.notificationsFavorited
        is Notification.Poll -> account.notificationsPolls
        is Notification.SignUp -> account.notificationsSignUps
        is Notification.Update -> account.notificationsUpdates
        is Notification.Report -> account.notificationsReports
        is Notification.SeveredRelationships -> account.notificationsSeveredRelationships
        is Notification.ModerationWarning -> account.notificationsModerationWarnings
        is Notification.Quote -> account.notificationsQuotes
        is Notification.QuotedUpdate -> account.notificationsQuotedUpdates
        is Notification.Unknown -> false
    }
}

/**
 * Returns the [AccountFilterDecision] for [notificationData] based on the notification
 * filters in [accountWithFilters].
 *
 * @return The most severe [AccountFilterDecision], in order [Hide][AccountFilterDecision.Hide],
 * [Warn][AccountFilterDecision.Warn], or [None][AccountFilterDecision.None].
 */
fun filterNotificationByAccount(accountWithFilters: PachliAccount, notificationData: Notification): AccountFilterDecision {
    val notification = notificationData // .notification
    // Some notifications are never filtered, irrespective of the account that
    // sent them.
    when (notification) {
        // Poll we interacted with has ended.
        is Notification.Poll -> return AccountFilterDecision.None
        // Status we interacted with has been updated.
        is Notification.Update -> return AccountFilterDecision.None
        // A new moderation report.
        is Notification.Report -> return AccountFilterDecision.None
        // Moderation has resulted in severed relationships.
        is Notification.SeveredRelationships -> return AccountFilterDecision.None
        // Moderators sent a warning.
        is Notification.ModerationWarning -> return AccountFilterDecision.None
        // We explicitly asked to be notified about this user.
        is Notification.Status -> return AccountFilterDecision.None
        // Admin signup notifications should not be filtered.
        is Notification.SignUp -> return AccountFilterDecision.None
        // TODO: Quote notifications should probably not be filtered
        // here either.
        else -> {
            /* fall through */
        }
    }

    // The account that generated the notification.
    val accountToTest = notificationData.account

    // Any notifications from our own activity are not filtered.
    if (accountWithFilters.entity.accountId == accountToTest.id) return AccountFilterDecision.None

    val decisions = buildList {
        // Check the following relationship.
        if (accountWithFilters.entity.notificationAccountFilterNotFollowed != FilterAction.NONE) {
            if (accountWithFilters.following.none { it.serverId == accountToTest.id }) {
                add(
                    AccountFilterDecision.make(
                        accountWithFilters.entity.notificationAccountFilterNotFollowed,
                        AccountFilterReason.NOT_FOLLOWING,
                    ),
                )
            }
        }

        // Check the age of the account relative to the notification.
        accountToTest.createdAt?.let { createdAt ->
            if (accountWithFilters.entity.notificationAccountFilterYounger30d != FilterAction.NONE) {
                if (Duration.between(createdAt, notification.createdAt) < Duration.ofDays(30)) {
                    add(
                        AccountFilterDecision.make(
                            accountWithFilters.entity.notificationAccountFilterYounger30d,
                            AccountFilterReason.YOUNGER_30D,
                        ),
                    )
                }
            }
        }

        // Check limited status.
        if (accountToTest.limited && accountWithFilters.entity.notificationAccountFilterLimitedByServer != FilterAction.NONE) {
            add(
                AccountFilterDecision.make(
                    accountWithFilters.entity.notificationAccountFilterLimitedByServer,
                    AccountFilterReason.LIMITED_BY_SERVER,
                ),
            )
        }
    }

    return decisions.firstOrNull { it is AccountFilterDecision.Hide }
        ?: decisions.firstOrNull { it is AccountFilterDecision.Warn }
        ?: AccountFilterDecision.None
}

private fun getChannelId(account: PachliAccountEntity, notification: Notification): String? {
    return when (notification) {
        is Notification.Mention -> PachliNotificationChannels.MENTION.channelId(account.identifier)
        is Notification.Status -> PachliNotificationChannels.SUBSCRIPTIONS.channelId(account.identifier)
        is Notification.Follow -> PachliNotificationChannels.FOLLOW.channelId(account.identifier)
        is Notification.FollowRequest -> PachliNotificationChannels.FOLLOW_REQUEST.channelId(account.identifier)
        is Notification.Reblog -> PachliNotificationChannels.REBLOG.channelId(account.identifier)
        is Notification.Favourite -> PachliNotificationChannels.FAVOURITE.channelId(account.identifier)
        is Notification.Poll -> PachliNotificationChannels.POLL.channelId(account.identifier)
        is Notification.SignUp -> PachliNotificationChannels.SIGN_UP.channelId(account.identifier)
        is Notification.Update -> PachliNotificationChannels.UPDATES.channelId(account.identifier)
        is Notification.Report -> PachliNotificationChannels.REPORT.channelId(account.identifier)
        is Notification.SeveredRelationships -> PachliNotificationChannels.SEVERED_RELATIONSHIPS.channelId(account.identifier)
        is Notification.ModerationWarning -> PachliNotificationChannels.MODERATION_WARNINGS.channelId(account.identifier)
        is Notification.Quote -> PachliNotificationChannels.QUOTE.channelId(account.identifier)
        is Notification.QuotedUpdate -> PachliNotificationChannels.QUOTED_UPDATE.channelId(account.identifier)
        is Notification.Unknown -> null
    }
}

private fun setSoundVibrationLight(
    account: PachliAccountEntity,
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
    account: PachliAccountEntity,
): Spanned {
    val accountName = notification.account.name.htmlEncode().unicodeWrap()
    val htmlTitle = when (notification) {
        is Notification.Mention -> {
            context.getString(R.string.notification_mention_format, accountName)
        }

        is Notification.Status -> {
            context.getString(R.string.notification_subscription_format, accountName)
        }

        is Notification.Follow -> {
            context.getString(R.string.notification_follow_format, accountName)
        }

        is Notification.FollowRequest -> {
            context.getString(R.string.notification_follow_request_format, accountName)
        }

        is Notification.Favourite -> {
            context.getString(R.string.notification_favourite_format, accountName)
        }

        is Notification.Reblog -> {
            context.getString(R.string.notification_reblog_format, accountName)
        }

        is Notification.Poll -> {
            val status = notification.status
            if (status.account.id == account.accountId) {
                context.getString(R.string.poll_ended_created)
            } else {
                context.getString(R.string.poll_ended_voted)
            }
        }

        is Notification.SignUp -> {
            context.getString(R.string.notification_sign_up_format, accountName)
        }

        is Notification.Update -> {
            context.getString(R.string.notification_update_format, accountName)
        }

        is Notification.Report -> {
            context.getString(R.string.notification_report_format, account.domain)
        }

        is Notification.SeveredRelationships -> {
            context.getString(
                R.string.notification_severed_relationships_format,
                notification.relationshipSeveranceEvent?.targetName,
            )
        }

        is Notification.ModerationWarning -> {
            context.getString(R.string.notification_moderation_warning_title)
        }

        is Notification.Quote -> {
            context.getString(R.string.notification_quote_format, accountName)
        }

        is Notification.QuotedUpdate -> {
            context.getString(R.string.notification_quoted_update_format)
        }

        is Notification.Unknown -> context.getString(R.string.notification_unknown)
    }

    return HtmlCompat.fromHtml(htmlTitle, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

private fun bodyForType(
    notification: Notification,
    context: Context,
    alwaysOpenSpoiler: Boolean,
): String? {
    when (notification) {
        is Notification.Follow, is Notification.FollowRequest, is Notification.SignUp -> {
            return "@" + notification.account.username
        }

        // Can this be "is Notification.WithStatus" instead?
        // Maybe, if Poll is moved higher up.
        is Notification.Mention,
        is Notification.Favourite,
        is Notification.Reblog,
        is Notification.Status,
        is Notification.Quote,
        is Notification.QuotedUpdate,
        -> {
            val status = notification.status
            return if (!TextUtils.isEmpty(status.spoilerText) && !alwaysOpenSpoiler) {
                status.spoilerText
            } else {
                status.content.parseAsMastodonHtml().toString()
            }
        }

        is Notification.Poll -> {
            val status = notification.status
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

        is Notification.Report -> {
            val report = notification.report
            return context.getString(
                R.string.notification_header_report_format,
                notification.account.name.unicodeWrap(),
                report.targetAccount.name.unicodeWrap(),
            )
        }

        is Notification.SeveredRelationships -> {
            val resourceId = when (notification.relationshipSeveranceEvent.type) {
                RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> R.string.notification_severed_relationships_domain_block_body
                RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> R.string.notification_severed_relationships_user_domain_block_body
                RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> R.string.notification_severed_relationships_account_suspension_body
                RelationshipSeveranceEvent.Type.UNKNOWN -> R.string.notification_severed_relationships_unknown_body
            }
            return context.getString(resourceId)
        }

        is Notification.ModerationWarning -> {
            val stringRes = when (notification.accountWarning.action) {
                AccountWarning.Action.NONE -> R.string.notification_moderation_warning_body_none_fmt
                AccountWarning.Action.DISABLE -> R.string.notification_moderation_warning_body_disable_fmt
                AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE -> R.string.notification_moderation_warning_body_mark_statuses_as_sensitive_fmt
                AccountWarning.Action.DELETE_STATUSES -> R.string.notification_moderation_warning_body_delete_statuses_fmt
                AccountWarning.Action.SILENCE -> R.string.notification_moderation_warning_body_silence_fmt
                AccountWarning.Action.SUSPEND -> R.string.notification_moderation_warning_body_suspend_fmt
                AccountWarning.Action.UNKNOWN -> R.string.notification_moderation_warning_body_unknown_fmt
            }
            return context.getString(stringRes, notification.accountWarning.text)
        }

        is Notification.Unknown -> return null
        is Notification.Update -> return null
    }
}

/**
 * Returns the enum associated with the given [key].
 *
 * @throws IllegalStateException if the value at [key] is not valid for the enum [T].
 */
inline fun <reified T : Enum<T>> Bundle.getEnum(key: String) = getInt(key, -1).let { if (it >= 0) enumValues<T>()[it] else throw IllegalStateException("unrecognised enum ordinal: $it") }

/**
 * Inserts an enum [value] into the mapping of this [Bundle], replacing any
 * existing value for the given [key].
 */
fun <T : Enum<T>> Bundle.putEnum(key: String, value: T?) = putInt(key, value?.ordinal ?: -1)
