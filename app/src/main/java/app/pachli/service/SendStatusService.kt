package app.pachli.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import app.pachli.R
import app.pachli.components.compose.MediaUploader
import app.pachli.components.drafts.DraftHelper
import app.pachli.components.notifications.pendingIntentFlags
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.designsystem.R as DR
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.StatusComposedEvent
import app.pachli.core.eventhub.StatusEditedEvent
import app.pachli.core.eventhub.StatusScheduledEvent
import app.pachli.core.model.Attachment
import app.pachli.core.model.MediaAttribute
import app.pachli.core.model.NewPoll
import app.pachli.core.model.NewStatus
import app.pachli.core.model.Status
import app.pachli.core.navigation.IntentRouterActivityIntent
import app.pachli.core.network.model.asNetworkModel
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import retrofit2.HttpException
import timber.log.Timber

@AndroidEntryPoint
class SendStatusService : Service() {

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var draftHelper: DraftHelper

    @Inject
    lateinit var mediaUploader: MediaUploader

    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private val statusesToSend = ConcurrentHashMap<Int, StatusToSend>()
    private val sendJobs = ConcurrentHashMap<Int, Job>()

    private val notificationManager by unsafeLazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra(KEY_STATUS)) {
            val statusToSend: StatusToSend = IntentCompat.getParcelableExtra(intent, KEY_STATUS, StatusToSend::class.java)
                ?: throw IllegalStateException("SendStatusService started without $KEY_STATUS extra")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, getString(R.string.send_post_notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            var notificationText = statusToSend.warningText
            if (notificationText.isBlank()) {
                notificationText = statusToSend.text
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.send_post_notification_title))
                .setContentText(notificationText)
                .setProgress(1, 0, true)
                .setOngoing(true)
                .setColor(getColor(DR.color.notification_color))
                .addAction(0, getString(android.R.string.cancel), cancelSendingIntent(sendingNotificationId))

            if (statusesToSend.isEmpty() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                startForeground(sendingNotificationId, builder.build())
            } else {
                notificationManager.notify(sendingNotificationId, builder.build())
            }

            statusesToSend[sendingNotificationId] = statusToSend
            sendStatus(sendingNotificationId--)
        } else {
            if (intent.hasExtra(KEY_CANCEL)) {
                cancelSending(intent.getIntExtra(KEY_CANCEL, 0))
            }
        }

        return START_NOT_STICKY
    }

    private fun sendStatus(statusId: Int) {
        // when statusToSend == null, sending has been canceled
        val statusToSend = statusesToSend[statusId] ?: return

        // when account == null, user has logged out, cancel sending
        val account = accountManager.getAccountById(statusToSend.pachliAccountId)

        if (account == null) {
            statusesToSend.remove(statusId)
            notificationManager.cancel(statusId)
            stopSelfWhenDone()
            return
        }

        statusToSend.retries++

        sendJobs[statusId] = serviceScope.launch {
            // first, wait for media uploads to finish
            val media = statusToSend.media.map { mediaItem ->
                if (mediaItem.id == null) {
                    val uploadState = mediaUploader.waitForUploadToFinish(mediaItem.localId)
                    val media = uploadState.getOrElse {
                        Timber.w("failed uploading media: %s", it.fmt(this@SendStatusService))
                        failSending(statusId)
                        stopSelfWhenDone()
                        return@launch
                    }
                    mediaItem.copy(id = media.serverId)
                } else {
                    mediaItem
                }
            }

            // then wait until server finished processing the media
            var mediaCheckRetries = 0
            while (media.any { mediaItem -> !mediaItem.processed }) {
                delay(1000L * mediaCheckRetries)
                media.forEach { mediaItem ->
                    if (!mediaItem.processed) {
                        mastodonApi.getMedia(mediaItem.id!!)
                            .onSuccess { mediaItem.processed = it.code == 200 }
                            .onFailure {
                                failSending(statusId)
                                stopSelfWhenDone()
                                return@launch
                            }
                    }
                }
                mediaCheckRetries++
            }

            val isNew = statusToSend.statusId == null

            if (isNew) {
                media.forEach { mediaItem ->
                    if (mediaItem.processed && (mediaItem.description != null || mediaItem.focus != null)) {
                        mastodonApi.updateMedia(mediaItem.id!!, mediaItem.description, mediaItem.focus?.toMastodonApiString())
                            .onFailure { error ->
                                Timber.w("failed to update media on status send: %s", error)
                                failOrRetry(error.throwable, statusId)
                                return@launch
                            }
                    }
                }
            }

            // finally, send the new status
            val newStatus = NewStatus(
                status = statusToSend.text,
                warningText = statusToSend.warningText,
                inReplyToId = statusToSend.inReplyToId,
                visibility = statusToSend.visibility,
                sensitive = statusToSend.sensitive,
                mediaIds = media.map { it.id!! },
                scheduledAt = statusToSend.scheduledAt,
                poll = statusToSend.poll,
                language = statusToSend.language,
                mediaAttributes = media.map { media ->
                    MediaAttribute(
                        id = media.id!!,
                        description = media.description,
                        focus = media.focus?.toMastodonApiString(),
                        thumbnail = null,
                    )
                },
            )

            val sendResult = if (isNew) {
                if (newStatus.scheduledAt == null) {
                    mastodonApi.createStatus(
                        account.authHeader,
                        account.domain,
                        statusToSend.idempotencyKey,
                        newStatus.asNetworkModel(),
                    )
                } else {
                    mastodonApi.createScheduledStatus(
                        account.authHeader,
                        account.domain,
                        statusToSend.idempotencyKey,
                        newStatus.asNetworkModel(),
                    )
                }
            } else {
                mastodonApi.editStatus(
                    statusToSend.statusId,
                    account.authHeader,
                    account.domain,
                    statusToSend.idempotencyKey,
                    newStatus.asNetworkModel(),
                )
            }

            sendResult.onSuccess {
                val sentStatus = it.body
                statusesToSend.remove(statusId)
                // If the status was loaded from a draft, delete the draft and associated media files.
                if (statusToSend.draftId != 0) {
                    draftHelper.deleteDraftAndAttachments(statusToSend.draftId)
                }

                mediaUploader.cancelUploadScope(*statusToSend.media.map { it.localId }.toIntArray())

                val scheduled = statusToSend.scheduledAt != null

                if (scheduled) {
                    eventHub.dispatch(StatusScheduledEvent)
                } else if (!isNew) {
                    eventHub.dispatch(
                        StatusEditedEvent(
                            statusToSend.statusId,
                            (sentStatus as app.pachli.core.network.model.Status).asModel(),
                        ),
                    )
                } else {
                    eventHub.dispatch(
                        StatusComposedEvent(
                            (sentStatus as app.pachli.core.network.model.Status).asModel(),
                        ),
                    )
                }

                notificationManager.cancel(statusId)
            }
                .onFailure {
                    Timber.w("failed sending status: %s", it)
                    failOrRetry(it.throwable, statusId)
                }

            stopSelfWhenDone()
        }
    }

    private suspend fun failOrRetry(throwable: Throwable, statusId: Int) {
        when (throwable) {
            // the server refused to accept, save status & show error message
            is HttpException -> failSending(statusId)
            // a network problem occurred, let's retry sending the status
            is IOException -> retrySending(statusId)
            // Some other problem, fail
            else -> failSending(statusId)
        }
    }

    private suspend fun retrySending(statusId: Int) {
        // when statusToSend == null, sending has been canceled
        val statusToSend = statusesToSend[statusId] ?: return

        val backoff = TimeUnit.SECONDS.toMillis(statusToSend.retries.toLong()).coerceAtMost(
            MAX_RETRY_INTERVAL,
        )

        delay(backoff)
        sendStatus(statusId)
    }

    private fun stopSelfWhenDone() {
        if (statusesToSend.isEmpty()) {
            ServiceCompat.stopForeground(this@SendStatusService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onTimeout(startId: Int) {
        // Android wants the service to shut down. Fail any statuses that still need to be sent
        // and shut down. See https://developer.android.com/about/versions/14/changes/fgs-types-required
        runBlocking {
            statusesToSend.forEach { (i, _) ->
                failSending(i) // Will stop the service when there are no more statuses
            }
        }
    }

    private suspend fun failSending(statusId: Int) {
        val failedStatus = statusesToSend.remove(statusId)
        if (failedStatus != null) {
            mediaUploader.cancelUploadScope(*failedStatus.media.map { it.localId }.toIntArray())

            saveStatusToDrafts(failedStatus, failedToSendAlert = true)

            val notification = buildDraftNotification(
                R.string.send_post_notification_error_title,
                R.string.send_post_notification_saved_content,
                failedStatus.pachliAccountId,
                statusId,
            )

            notificationManager.cancel(statusId)
            notificationManager.notify(errorNotificationId++, notification)
        }

        // NOTE only this removes the "Sending..." notification (added with startForeground() above)
        stopSelfWhenDone()
    }

    private fun cancelSending(statusId: Int) = serviceScope.launch {
        val statusToCancel = statusesToSend.remove(statusId)
        if (statusToCancel != null) {
            mediaUploader.cancelUploadScope(*statusToCancel.media.map { it.localId }.toIntArray())

            val sendJob = sendJobs.remove(statusId)
            sendJob?.cancel()

            saveStatusToDrafts(statusToCancel, failedToSendAlert = false)

            val notification = buildDraftNotification(
                R.string.send_post_notification_cancel_title,
                R.string.send_post_notification_saved_content,
                statusToCancel.pachliAccountId,
                statusId,
            )

            notificationManager.notify(statusId, notification)

            delay(5000)

            stopSelfWhenDone()
        }
    }

    private suspend fun saveStatusToDrafts(status: StatusToSend, failedToSendAlert: Boolean) {
        draftHelper.saveDraft(
            draftId = status.draftId,
            pachliAccountId = status.pachliAccountId,
            inReplyToId = status.inReplyToId,
            content = status.text,
            contentWarning = status.warningText,
            sensitive = status.sensitive,
            visibility = Status.Visibility.byString(status.visibility),
            mediaUris = status.media.map { it.uri },
            mediaDescriptions = status.media.map { it.description },
            mediaFocus = status.media.map { it.focus },
            poll = status.poll,
            failedToSend = true,
            failedToSendAlert = failedToSendAlert,
            scheduledAt = status.scheduledAt,
            language = status.language,
            statusId = status.statusId,
        )
    }

    private fun cancelSendingIntent(statusId: Int): PendingIntent {
        // TODO: Revisit suppressing this when this file is moved
        @SuppressLint("IntentDetector")
        val intent = Intent(this, SendStatusService::class.java)
        intent.putExtra(KEY_CANCEL, statusId)
        return PendingIntent.getService(
            this,
            statusId,
            intent,
            pendingIntentFlags(false),
        )
    }

    private fun buildDraftNotification(
        @StringRes title: Int,
        @StringRes content: Int,
        pachliAccountId: Long,
        statusId: Int,
    ): Notification {
        val intent = IntentRouterActivityIntent.fromDraftsNotification(this, pachliAccountId)

        val pendingIntent = PendingIntent.getActivity(
            this,
            statusId,
            intent,
            pendingIntentFlags(false),
        )

        return NotificationCompat.Builder(this@SendStatusService, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(title))
            .setContentText(getString(content))
            .setColor(getColor(DR.color.notification_color))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }

    companion object {
        private const val KEY_STATUS = "status"
        private const val KEY_CANCEL = "cancel_id"
        private const val CHANNEL_ID = "send_toots"

        private val MAX_RETRY_INTERVAL = TimeUnit.MINUTES.toMillis(1)

        private var sendingNotificationId = -1 // use negative ids to not clash with other notis
        private var errorNotificationId = Int.MIN_VALUE // use even more negative ids to not clash with other notis

        fun sendStatusIntent(
            context: Context,
            statusToSend: StatusToSend,
        ): Intent {
            // TODO: Revisit suppressing this when this file is moved
            @SuppressLint("IntentDetector")
            val intent = Intent(context, SendStatusService::class.java)
            intent.putExtra(KEY_STATUS, statusToSend)

            if (statusToSend.media.isNotEmpty()) {
                // forward uri permissions
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uriClip = ClipData(
                    ClipDescription("Status Media", arrayOf("image/*", "video/*")),
                    ClipData.Item(statusToSend.media[0].uri),
                )
                statusToSend.media
                    .drop(1)
                    .forEach { mediaItem ->
                        uriClip.addItem(ClipData.Item(mediaItem.uri))
                    }

                intent.clipData = uriClip
            }

            return intent
        }
    }
}

@Parcelize
data class StatusToSend(
    val text: String,
    val warningText: String,
    val visibility: String,
    val sensitive: Boolean,
    val media: List<MediaToSend>,
    val scheduledAt: Date?,
    val inReplyToId: String?,
    val poll: NewPoll?,
    val replyingStatusContent: String?,
    val replyingStatusAuthorUsername: String?,
    val pachliAccountId: Long,
    val draftId: Int,
    val idempotencyKey: String,
    var retries: Int,
    val language: String?,
    val statusId: String?,
) : Parcelable

@Parcelize
data class MediaToSend(
    val localId: Int,
    // null if media is not yet completely uploaded
    val id: String?,
    val uri: String,
    val description: String?,
    val focus: Attachment.Focus?,
    var processed: Boolean,
) : Parcelable
