/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.compose

import android.content.ContentResolver
import android.net.Uri
import android.text.Editable
import android.text.Spanned
import android.text.style.URLSpan
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.components.compose.ComposeActivity.QueuedMedia
import app.pachli.components.compose.ComposeAutoCompleteAdapter.AutocompleteResult
import app.pachli.components.drafts.DraftHelper
import app.pachli.components.search.SearchType
import app.pachli.core.common.PachliError
import app.pachli.core.common.string.mastodonLength
import app.pachli.core.common.string.randomAlphanumericString
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.InstanceInfoRepository
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ComposeKind
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.MentionSpan
import app.pachli.service.MediaToSend
import app.pachli.service.ServiceClient
import app.pachli.service.StatusToSend
import at.connyduck.calladapter.networkresult.fold
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val mediaUploader: MediaUploader,
    private val serviceClient: ServiceClient,
    private val draftHelper: DraftHelper,
    instanceInfoRepo: InstanceInfoRepository,
) : ViewModel() {

    /** The current content */
    private var content: Editable = Editable.Factory.getInstance().newEditable("")

    /** The current content warning */
    private var contentWarning: String = ""

    /**
     * The effective content warning. Either the real content warning, or the empty string
     * if the content warning has been hidden
     */
    private val effectiveContentWarning
        get() = if (showContentWarning.value) contentWarning else ""

    private var replyingStatusAuthor: String? = null
    private var replyingStatusContent: String? = null

    /** The initial content for this status, before any edits */
    internal var initialContent: String = ""

    /** The initial content warning for this status, before any edits */
    private var initialContentWarning: String = ""

    /** The initial language for this status, before any changes */
    private var initialLanguage: String? = null

    /** The current language for this status. */
    internal var language: String? = null

    /** If editing a draft then the ID of the draft, otherwise 0 */
    private var draftId: Int = 0
    private var scheduledTootId: String? = null
    private var inReplyToId: String? = null
    private var originalStatusId: String? = null
    private var startingVisibility: Status.Visibility = Status.Visibility.UNKNOWN

    private var contentWarningStateChanged: Boolean = false
    private var modifiedInitialState: Boolean = false
    private var scheduledTimeChanged: Boolean = false

    val instanceInfo = instanceInfoRepo.instanceInfo

    val emojis = instanceInfoRepo.emojis

    private val _markMediaAsSensitive: MutableStateFlow<Boolean> =
        MutableStateFlow(accountManager.activeAccount?.defaultMediaSensitivity ?: false)
    val markMediaAsSensitive = _markMediaAsSensitive.asStateFlow()

    private val _statusVisibility: MutableStateFlow<Status.Visibility> = MutableStateFlow(Status.Visibility.UNKNOWN)
    val statusVisibility = _statusVisibility.asStateFlow()
    private val _showContentWarning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showContentWarning = _showContentWarning.asStateFlow()
    private val _poll: MutableStateFlow<NewPoll?> = MutableStateFlow(null)
    val poll = _poll.asStateFlow()
    private val _scheduledAt: MutableStateFlow<String?> = MutableStateFlow(null)
    val scheduledAt = _scheduledAt.asStateFlow()

    private val _media: MutableStateFlow<List<QueuedMedia>> = MutableStateFlow(emptyList())
    val media = _media.asStateFlow()
    private val _uploadError = MutableSharedFlow<MediaUploaderError>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uploadError = _uploadError.asSharedFlow()
    private val _closeConfirmation = MutableStateFlow(ConfirmationKind.NONE)
    val closeConfirmation = _closeConfirmation.asStateFlow()
    private val _statusLength = MutableStateFlow(0)
    val statusLength = _statusLength.asStateFlow()

    private lateinit var composeKind: ComposeKind

    // Used in ComposeActivity to pass state to result function when cropImage contract inflight
    var cropImageItemOld: QueuedMedia? = null

    private var setupComplete = false

    /** Errors preparing media for upload. */
    sealed interface PickMediaError : PachliError {
        @JvmInline
        value class PrepareMediaError(val error: MediaUploaderError.PrepareMediaError) : PickMediaError, MediaUploaderError.PrepareMediaError by error

        /**
         * User is trying to add an image to a post that already has a video
         * attachment, or vice-versa.
         */
        data object MixedMediaTypesError : PickMediaError {
            override val resourceId = R.string.error_media_upload_image_or_video
            override val formatArgs = null
            override val cause = null
        }
    }

    /**
     * Copies selected media and adds to the upload queue.
     *
     * @param mediaUri [ContentResolver] URI for the file to copy
     * @param description media description / caption
     * @param focus focus, if relevant
     */
    suspend fun pickMedia(mediaUri: Uri, description: String? = null, focus: Attachment.Focus? = null): Result<QueuedMedia, PickMediaError> = withContext(Dispatchers.IO) {
        val (type, uri, size) = mediaUploader.prepareMedia(mediaUri, instanceInfo.value)
            .mapError { PickMediaError.PrepareMediaError(it) }.getOrElse { return@withContext Err(it) }
        val mediaItems = media.value
        if (type != QueuedMedia.Type.IMAGE && mediaItems.isNotEmpty() && mediaItems[0].type == QueuedMedia.Type.IMAGE) {
            Err(PickMediaError.MixedMediaTypesError)
        } else {
            val queuedMedia = addMediaToQueue(type, uri, size, description, focus)
            Ok(queuedMedia)
        }
    }

    suspend fun addMediaToQueue(
        type: QueuedMedia.Type,
        uri: Uri,
        mediaSize: Long,
        description: String? = null,
        focus: Attachment.Focus? = null,
        replaceItem: QueuedMedia? = null,
    ): QueuedMedia {
        var stashMediaItem: QueuedMedia? = null

        _media.update { mediaList ->
            val mediaItem = QueuedMedia(
                localId = mediaUploader.getNewLocalMediaId(),
                uri = uri,
                type = type,
                mediaSize = mediaSize,
                description = description,
                focus = focus,
                state = QueuedMedia.State.UPLOADING,
            )
            stashMediaItem = mediaItem

            if (replaceItem != null) {
                mediaUploader.cancelUploadScope(replaceItem.localId)
                mediaList.map {
                    if (it.localId == replaceItem.localId) mediaItem else it
                }
            } else { // Append
                mediaList + mediaItem
            }
        }
        val mediaItem = stashMediaItem!! // stashMediaItem is always non-null and uncaptured at this point, but Kotlin doesn't know that

        viewModelScope.launch {
            mediaUploader
                .uploadMedia(mediaItem, instanceInfo.value)
                .collect { event ->
                    val item = media.value.find { it.localId == mediaItem.localId }
                        ?: return@collect
                    var newMediaItem: QueuedMedia? = null
                    val uploadEvent = event.getOrElse {
                        _media.update { mediaList -> mediaList.filter { it.localId != mediaItem.localId } }
                        _uploadError.emit(it)
                        return@collect
                    }

                    newMediaItem = when (uploadEvent) {
                        is UploadEvent.ProgressEvent -> item.copy(uploadPercent = uploadEvent.percentage)
                        is UploadEvent.FinishedEvent -> {
                            item.copy(
                                id = uploadEvent.media.mediaId,
                                uploadPercent = -1,
                                state = if (uploadEvent.media.processed) {
                                    QueuedMedia.State.PROCESSED
                                } else {
                                    QueuedMedia.State.UNPROCESSED
                                },
                            )
                        }
                    }
                    newMediaItem.let {
                        _media.update { mediaList ->
                            mediaList.map { mediaItem -> if (mediaItem.localId == it.localId) it else mediaItem }
                        }
                    }
                }
        }

        updateCloseConfirmation()
        return mediaItem
    }

    private fun addUploadedMedia(id: String, type: QueuedMedia.Type, uri: Uri, description: String?, focus: Attachment.Focus?) {
        _media.update { mediaList ->
            val mediaItem = QueuedMedia(
                localId = mediaUploader.getNewLocalMediaId(),
                uri = uri,
                type = type,
                mediaSize = 0,
                uploadPercent = -1,
                id = id,
                description = description,
                focus = focus,
                state = QueuedMedia.State.PUBLISHED,
            )
            mediaList + mediaItem
        }
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaUploader.cancelUploadScope(item.localId)
        _media.update { mediaList -> mediaList.filter { it.localId != item.localId } }
        updateCloseConfirmation()
    }

    fun toggleMarkSensitive() {
        this._markMediaAsSensitive.value = this._markMediaAsSensitive.value != true
    }

    /** Call this when the status' primary content changes */
    fun onContentChanged(newContent: Editable) {
        content = newContent
        updateStatusLength()
        updateCloseConfirmation()
    }

    /** Call this when the status' content warning changes */
    fun onContentWarningChanged(newContentWarning: String) {
        contentWarning = newContentWarning
        updateStatusLength()
        updateCloseConfirmation()
    }

    /** Call this to attach or clear the status' poll */
    fun onPollChanged(newPoll: NewPoll?) {
        _poll.value = newPoll
        updateCloseConfirmation()
    }

    /** Call this to change the status' visibility */
    fun onStatusVisibilityChanged(newVisibility: Status.Visibility) {
        _statusVisibility.value = newVisibility
    }

    /** Call this to change the status' language */
    fun onLanguageChanged(newLanguage: String) {
        language = newLanguage
        updateCloseConfirmation()
    }

    @VisibleForTesting
    fun updateStatusLength() {
        _statusLength.value = statusLength(content, effectiveContentWarning, instanceInfo.value.charactersReservedPerUrl)
    }

    private fun updateCloseConfirmation() {
        _closeConfirmation.value = if (isDirty()) {
            when (composeKind) {
                ComposeKind.NEW -> if (isEmpty(content, effectiveContentWarning)) {
                    ConfirmationKind.NONE
                } else {
                    ConfirmationKind.SAVE_OR_DISCARD
                }
                ComposeKind.EDIT_DRAFT -> if (isEmpty(content, effectiveContentWarning)) {
                    ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_DRAFT
                } else {
                    ConfirmationKind.UPDATE_OR_DISCARD
                }
                ComposeKind.EDIT_POSTED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
                ComposeKind.EDIT_SCHEDULED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
            }
        } else {
            ConfirmationKind.NONE
        }
    }

    /**
     * @return True if content of this status is "dirty", meaning one or more of the
     *   following have changed since the compose session started: content,
     *   content warning and content warning visibility, media, polls, or the
     *   scheduled time to send.
     */
    private fun isDirty(): Boolean {
        val contentChanged = !content.contentEquals(initialContent)

        val contentWarningChanged = effectiveContentWarning != initialContentWarning
        val mediaChanged = media.value.isNotEmpty()
        val pollChanged = poll.value != null
        val languageChanged = initialLanguage != language

        return modifiedInitialState || contentChanged || contentWarningChanged || mediaChanged || pollChanged || languageChanged || scheduledTimeChanged
    }

    private fun isEmpty(content: CharSequence, contentWarning: CharSequence): Boolean {
        return !modifiedInitialState && (content.isBlank() && contentWarning.isBlank() && media.value.isEmpty() && poll.value == null)
    }

    fun showContentWarningChanged(value: Boolean) {
        _showContentWarning.value = value
        contentWarningStateChanged = true
        updateStatusLength()
    }

    fun deleteDraft() {
        viewModelScope.launch {
            if (draftId != 0) {
                draftHelper.deleteDraftAndAttachments(draftId)
            }
        }
    }

    fun stopUploads() {
        mediaUploader.cancelUploadScope(*media.value.map { it.localId }.toIntArray())
    }

    fun shouldShowSaveDraftDialog(): Boolean {
        // if any of the media files need to be downloaded first it could take a while, so show a loading dialog
        return media.value.any { mediaValue ->
            mediaValue.uri.scheme == "https"
        }
    }

    suspend fun saveDraft(content: String, contentWarning: String) {
        val mediaUris: MutableList<String> = mutableListOf()
        val mediaDescriptions: MutableList<String?> = mutableListOf()
        val mediaFocus: MutableList<Attachment.Focus?> = mutableListOf()
        media.value.forEach { item ->
            mediaUris.add(item.uri.toString())
            mediaDescriptions.add(item.description)
            mediaFocus.add(item.focus)
        }

        draftHelper.saveDraft(
            draftId = draftId,
            accountId = accountManager.activeAccount?.id!!,
            inReplyToId = inReplyToId,
            content = content,
            contentWarning = contentWarning,
            sensitive = _markMediaAsSensitive.value,
            visibility = statusVisibility.value,
            mediaUris = mediaUris,
            mediaDescriptions = mediaDescriptions,
            mediaFocus = mediaFocus,
            poll = poll.value,
            failedToSend = false,
            failedToSendAlert = false,
            scheduledAt = scheduledAt.value,
            language = language,
            statusId = originalStatusId,
        )
    }

    /**
     * Send status to the server.
     * Uses current state plus provided arguments.
     */
    suspend fun sendStatus(
        content: String,
        spoilerText: String,
        accountId: Long,
    ) {
        if (!scheduledTootId.isNullOrEmpty()) {
            api.deleteScheduledStatus(scheduledTootId!!)
        }

        val attachedMedia = media.value.map { item ->
            MediaToSend(
                localId = item.localId,
                id = item.id,
                uri = item.uri.toString(),
                description = item.description,
                focus = item.focus,
                processed = item.state == QueuedMedia.State.PROCESSED || item.state == QueuedMedia.State.PUBLISHED,
            )
        }
        val tootToSend = StatusToSend(
            text = content,
            warningText = spoilerText,
            visibility = statusVisibility.value.serverString(),
            sensitive = attachedMedia.isNotEmpty() && (_markMediaAsSensitive.value || showContentWarning.value),
            media = attachedMedia,
            scheduledAt = scheduledAt.value,
            inReplyToId = inReplyToId,
            poll = poll.value,
            replyingStatusContent = null,
            replyingStatusAuthorUsername = null,
            accountId = accountId,
            draftId = draftId,
            idempotencyKey = randomAlphanumericString(16),
            retries = 0,
            language = language,
            statusId = originalStatusId,
        )

        serviceClient.sendToot(tootToSend)
    }

    private fun updateMediaItem(localId: Int, mutator: (QueuedMedia) -> QueuedMedia) {
        _media.update { mediaList ->
            mediaList.map { mediaItem ->
                if (mediaItem.localId == localId) {
                    mutator(mediaItem)
                } else {
                    mediaItem
                }
            }
        }
    }

    fun updateDescription(localId: Int, description: String) {
        updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(description = description)
        }
    }

    fun updateFocus(localId: Int, focus: Attachment.Focus) {
        updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(focus = focus)
        }
    }

    suspend fun searchAutocompleteSuggestions(token: String): List<AutocompleteResult> {
        when (token[0]) {
            '@' -> {
                return api.searchAccounts(query = token.substring(1), limit = 10).mapBoth(
                    { it.body.map { AutocompleteResult.AccountResult(it) } },
                    {
                        Timber.e(it.throwable, "Autocomplete search for %s failed.", token)
                        emptyList()
                    },
                )
            }
            '#' -> {
                return api.search(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
                    .fold({ searchResult ->
                        searchResult.hashtags.map {
                            AutocompleteResult.HashtagResult(
                                hashtag = it.name,
                                usage7d = it.history.sumOf { it.uses },
                            )
                        }.sortedByDescending { it.usage7d }
                    }, { e ->
                        Timber.e(e, "Autocomplete search for %s failed.", token)
                        emptyList()
                    })
            }
            ':' -> {
                val incomplete = token.substring(1)

                return emojis.value.filter { emoji ->
                    emoji.shortcode.contains(incomplete, ignoreCase = true)
                }.sortedBy { emoji ->
                    emoji.shortcode.indexOf(incomplete, ignoreCase = true)
                }.map { emoji ->
                    AutocompleteResult.EmojiResult(emoji)
                }
            }
            else -> {
                Timber.w("Unexpected autocompletion token: %s", token)
                return emptyList()
            }
        }
    }

    fun setup(composeOptions: ComposeOptions?) {
        if (setupComplete) {
            return
        }

        composeKind = composeOptions?.kind ?: ComposeKind.NEW

        val preferredVisibility = accountManager.activeAccount!!.defaultPostPrivacy

        val replyVisibility = composeOptions?.replyVisibility ?: Status.Visibility.UNKNOWN
        startingVisibility = Status.Visibility.getOrUnknown(
            preferredVisibility.ordinal.coerceAtLeast(replyVisibility.ordinal),
        )

        inReplyToId = composeOptions?.inReplyToId

        modifiedInitialState = composeOptions?.modifiedInitialState == true

        val contentWarning = composeOptions?.contentWarning
        if (contentWarning != null) {
            initialContentWarning = contentWarning
        }
        if (!contentWarningStateChanged) {
            _showContentWarning.value = !contentWarning.isNullOrBlank()
        }

        // recreate media list
        val draftAttachments = composeOptions?.draftAttachments
        if (draftAttachments != null) {
            // when coming from DraftActivity
            viewModelScope.launch {
                draftAttachments.forEach { attachment ->
                    pickMedia(attachment.uri, attachment.description, attachment.focus)
                }
            }
        } else {
            composeOptions?.mediaAttachments?.forEach { a ->
                // when coming from redraft or ScheduledTootActivity
                val mediaType = when (a.type) {
                    Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
                    Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
                    Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
                }
                addUploadedMedia(a.id, mediaType, a.url.toUri(), a.description, a.meta?.focus)
            }
        }

        draftId = composeOptions?.draftId ?: 0
        scheduledTootId = composeOptions?.scheduledTootId
        originalStatusId = composeOptions?.statusId
        initialContent = composeOptions?.content ?: ""
        initialLanguage = composeOptions?.language
        language = initialLanguage

        val tootVisibility = composeOptions?.visibility ?: Status.Visibility.UNKNOWN
        if (tootVisibility != Status.Visibility.UNKNOWN) {
            startingVisibility = tootVisibility
        }
        _statusVisibility.value = startingVisibility
        val mentionedUsernames = composeOptions?.mentionedUsernames
        if (mentionedUsernames != null) {
            val builder = StringBuilder()
            for (name in mentionedUsernames) {
                builder.append('@')
                builder.append(name)
                builder.append(' ')
            }
            initialContent = builder.toString()
        }

        _scheduledAt.value = composeOptions?.scheduledAt

        composeOptions?.sensitive?.let { _markMediaAsSensitive.value = it }

        val poll = composeOptions?.poll
        if (poll != null && composeOptions.mediaAttachments.isNullOrEmpty()) {
            _poll.value = poll
        }
        replyingStatusContent = composeOptions?.replyingStatusContent
        replyingStatusAuthor = composeOptions?.replyingStatusAuthor

        updateCloseConfirmation()
        setupComplete = true
    }

    fun updateScheduledAt(newScheduledAt: String?) {
        if (newScheduledAt != scheduledAt.value) {
            scheduledTimeChanged = true
        }

        _scheduledAt.value = newScheduledAt
        updateCloseConfirmation()
    }

    val editing: Boolean
        get() = !originalStatusId.isNullOrEmpty()

    enum class ConfirmationKind {
        /** No confirmation, finish */
        NONE,

        /** Content has changed and it's an un-posted status, show "save or discard" */
        SAVE_OR_DISCARD,

        /** Content has changed when editing a draft, show "update draft or discard changes" */
        UPDATE_OR_DISCARD,

        /** Content has changed when editing a posted status or scheduled status */
        CONTINUE_EDITING_OR_DISCARD_CHANGES,

        /** Content has been cleared when editing a draft */
        CONTINUE_EDITING_OR_DISCARD_DRAFT,
    }

    companion object {
        /**
         * Calculate the effective status length.
         *
         * Some text is counted differently:
         *
         * In the status body:
         *
         * - URLs always count for [urlLength] characters irrespective of their actual length
         *   (https://docs.joinmastodon.org/user/posting/#links)
         * - Mentions ("@user@some.instance") only count the "@user" part
         *   (https://docs.joinmastodon.org/user/posting/#mentions)
         * - Hashtags are always treated as their actual length, including the "#"
         *   (https://docs.joinmastodon.org/user/posting/#hashtags)
         * - Emojis are treated as a single character
         *
         * Content warning text is always treated as its full length, URLs and other entities
         * are not treated differently.
         *
         * @param body status body text
         * @param contentWarning optional content warning text
         * @param urlLength the number of characters attributed to URLs
         * @return the effective status length
         */
        fun statusLength(body: Spanned, contentWarning: String, urlLength: Int): Int {
            var length = body.toString().mastodonLength() - body.getSpans(0, body.length, URLSpan::class.java)
                .fold(0) { acc, span ->
                    // Accumulate a count of characters to be *ignored* in the final length
                    acc + when (span) {
                        is MentionSpan -> {
                            // Ignore everything from the second "@" (if present)
                            span.url.length - (
                                span.url.indexOf("@", 1).takeIf { it >= 0 }
                                    ?: span.url.length
                                )
                        }
                        else -> {
                            // Expected to be negative if the URL length < maxUrlLength
                            span.url.mastodonLength() - urlLength
                        }
                    }
                }

            // Content warning text is treated as is, URLs or mentions there are not special
            length += contentWarning.mastodonLength()

            return length
        }
    }
}
