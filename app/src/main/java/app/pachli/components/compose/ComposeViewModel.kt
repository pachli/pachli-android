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
import android.text.SpannedString
import android.text.style.URLSpan
import androidx.core.net.toUri
import androidx.core.text.toSpanned
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.components.compose.ComposeActivity.QueuedMedia
import app.pachli.components.compose.ComposeAutoCompleteAdapter.AutocompleteResult
import app.pachli.components.compose.UiError.PickMediaError
import app.pachli.components.compose.UploadState.Uploaded
import app.pachli.components.drafts.DraftHelper
import app.pachli.components.search.SearchType
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.common.string.mastodonLength
import app.pachli.core.common.string.randomAlphanumericString
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.InstanceInfo
import app.pachli.core.model.ServerOperation
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ComposeKind
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.InReplyTo
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.ShowSelfUsername
import app.pachli.core.ui.MentionSpan
import app.pachli.service.MediaToSend
import app.pachli.service.ServiceClient
import app.pachli.service.StatusToSend
import app.pachli.util.getInitialLanguages
import app.pachli.util.getLocaleList
import app.pachli.util.modernLanguageCode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.unwrap
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.z4kn4fein.semver.constraints.toConstraint
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// TODO:
//
// - initial content not restored
// x is a poll in a draft restored?

internal data class UiState(
    // Server limits rather than UiState? Change very infrequently
    val maxPostChars: Int,
    val maxMediaAttachments: Int,

    // Like current effectiveContentWarning
    val contentWarning: String,
    // Like current content
    val content: Editable,

    // Like initialContent?
    val initialContent: String?,

    val displaySelfUsername: Boolean,
)

internal data class InitialUiState(
    val account: PachliAccount,
//    val displaySelfUsername: Boolean,
//    val maxMediaDescriptionLimit: Int,
    /** The initial content for this status, before any edits */
    val content: String,
    val contentWarning: String,
    val scheduledAt: Date? = null,
) {
    companion object {
        fun from(composeOptions: ComposeOptions) {
        }
    }
}

internal sealed interface UiAction

internal sealed interface FallibleUiAction : UiAction {
    data object LoadInReplyTo : FallibleUiAction
    data class PickMedia(
        val uri: Uri,
        val description: String? = null,
        val focus: Attachment.Focus? = null,
    ) : FallibleUiAction

    /**
     * Attaches media identified by [uri] to this post.
     *
     * @param replaceItemId [ID][QueuedMedia.localId] of an existing
     * attachment that should be replaced. Null if this media should
     * not replace an existing attachment.
     */
    data class AttachMedia(
        val type: QueuedMedia.Type,
        val uri: Uri,
        val mediaSize: Long,
        val description: String? = null,
        val focus: Attachment.Focus? = null,
        val replaceItemId: Int? = null,
    ) : FallibleUiAction
}

sealed interface UiSuccess

internal sealed interface UiError : PachliError {
    val action: UiAction

    /** Error occurred loading the status this is a reply to. */
    data class LoadInReplyToError(override val cause: PachliError) : UiError {
        override val resourceId = R.string.ui_error_reload_reply_fmt
        override val formatArgs = null
        override val action = FallibleUiAction.LoadInReplyTo
    }

    sealed interface PickMediaError : UiError {
        data class PrepareMediaError(
            override val cause: MediaUploaderError.PrepareMediaError,
            override val action: UiAction,
        ) : PickMediaError, MediaUploaderError.PrepareMediaError by cause

        /**
         * User is trying to add an image to a post that already has a video
         * attachment, or vice-versa.
         */
        data class MixedMediaTypesError(override val action: UiAction) : PickMediaError {
            override val resourceId = R.string.error_media_upload_image_or_video
            override val formatArgs = null
            override val cause = null
        }
    }
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val mediaUploader: MediaUploader,
    private val serviceClient: ServiceClient,
    private val draftHelper: DraftHelper,
    serverRepository: ServerRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    val pachliAccountFlow = pachliAccountId.distinctUntilChanged().flatMapLatest {
        accountManager.getPachliAccountFlow(it).filterNotNull()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val composeOptions = MutableStateFlow<ComposeOptions>(ComposeOptions())

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>(replay = 1)

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    internal val uiResult = _uiResult.receiveAsFlow()

    /** Accept UI actions in to actionStateFlow */
    internal val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    /**
     * Triggers a reload of the status being replied to every time a value is emitted
     * into this flow.
     */
    private val reloadReply = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private val _initialUiState = MutableSharedFlow<InitialUiState>()
    internal val initialUiState = _initialUiState.asSharedFlow()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * Flow of data about the in-reply-to status for this post.
     *
     * - Ok(null) - this is not a reply.
     * - Ok(InReplyTo.Status) - this is a reply, with the status being replied to.
     * - Err() - error occurred fetching the status being replied to.
     */
    // Reload the reply when either the status being replied to changes or the user
    // explicitly triggers a reload.
    internal val inReplyTo = stateFlow(viewModelScope, Ok(Loadable.Loaded(null))) {
        reloadReply.combine(composeOptions.map { it.inReplyTo }.distinctUntilChanged()) { _, inReplyTo -> inReplyTo }
            .flatMapLatest { inReplyTo ->
                flow {
                    when (inReplyTo) {
                        is InReplyTo.Id -> {
                            emit(Ok(Loadable.Loading))
                            api.status(inReplyTo.statusId)
                                .mapEither(
                                    { Loadable.Loaded(InReplyTo.Status.from(it.body)) },
                                    { UiError.LoadInReplyToError(it) },
                                )
                        }

                        is InReplyTo.Status -> Ok(Loadable.Loaded(inReplyTo))
                        null -> Ok(Loadable.Loaded(null))
                    }.also { emit(it) }
                }
            }.flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    /** The initial content for this status, before any edits */
    private val initialContent = MutableSharedFlow<String>(replay = 1)

    /** The current language for this status. */
    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    /** If editing a draft then the ID of the draft, otherwise 0 */
//    private val draftId = composeOptions.map { it.draftId }
//        .onEach { Timber.d("draftId: $it") }
//        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    private val scheduledPostId = composeOptions.map { it.scheduledTootId }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )
    private val originalStatusId = composeOptions.map { it.statusId }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    //    private var contentWarningStateChanged: Boolean = false
    private val modifiedInitialState = composeOptions.map { it.modifiedInitialState == true }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    val instanceInfo = MutableStateFlow(InstanceInfo())
    val emojis = MutableStateFlow(emptyList<Emoji>())

    private val _markMediaAsSensitive = MutableStateFlow(false)
    val markMediaAsSensitive = _markMediaAsSensitive.asStateFlow()

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    // -- Flows for content.

    // This is Spanned rather than Editable because if the value is updated
    // by ComposeActivity
    private val fContent = MutableStateFlow<Spanned>(SpannedString(""))

    // -- Flows for content warning.
    private val fContentWarning = MutableStateFlow("")

    // TODO: This is probably wrong, this should be Flow<Boolean> toggled by the user.
//    val showContentWarning = fContentWarning.map { it.isNotBlank() }
    private val _showContentWarning = MutableStateFlow(false)
    val showContentWarning = _showContentWarning.asStateFlow()

    /**
     * The effective content warning. Either the real content warning, or the empty string
     * if the content warning has been hidden
     */
    val fEffectiveContentWarning = combine(showContentWarning, fContentWarning) { show, cw ->
        Timber.d("Evaluating effectiveContentWarning")
        Timber.d("  show: $show")
        Timber.d("    cw: $cw")
        if (show) cw else ""
    }.onEach { Timber.d("fEffectiveContentWarning: $it") }
        // .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Length of the status. */
    val fStatusLength = combine(fContent, fContentWarning, instanceInfo) { content, contentWarning, instanceInfo ->
        statusLength(content, contentWarning, instanceInfo.charactersReservedPerUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val initialVisibility = MutableSharedFlow<Status.Visibility>(replay = 1)

    private val _statusVisibility: MutableStateFlow<Status.Visibility> = MutableStateFlow(Status.Visibility.UNKNOWN)
    val statusVisibility = _statusVisibility.asStateFlow()
    private val _poll: MutableStateFlow<NewPoll?> = MutableStateFlow(null)
    val poll = _poll.asStateFlow()

    private val _scheduledAt = MutableStateFlow<Date?>(null)
    val scheduledAt = _scheduledAt.asStateFlow()

    private val _media: MutableStateFlow<List<QueuedMedia>> = MutableStateFlow(emptyList())
    val media = _media.asStateFlow()

    /**
     * If composing from an existing status, or a draft, the list of media attached to the
     * status before any edits.
     *
     * Used to determine if the media has changed.
     */
//    private val initialMedia: MutableSharedFlow<List<QueuedMedia>> = MutableSharedFlow(replay = 1)

//    private val _statusLength = MutableStateFlow(0)
//    val statusLength = _statusLength.asStateFlow()

    private val _languages = MutableStateFlow(emptyList<String>())
    val languages = _languages.asStateFlow()

    /** Flow of whether or not the server can schedule posts. */
    val serverCanSchedule = serverRepository.flow.map {
        it.get()?.can(ServerOperation.ORG_JOINMASTODON_STATUSES_SCHEDULED, ">= 1.0.0".toConstraint()) == true
    }.distinctUntilChanged()

    /**
     * True if the post's language should be checked before posting.
     *
     * Modifications are persisted back to shared preferences.
     */
    var confirmStatusLanguage: Boolean
        get() = sharedPreferencesRepository.confirmStatusLanguage
        set(value) {
            sharedPreferencesRepository.confirmStatusLanguage = value
        }

    private val composeKind = composeOptions.map { it.kind ?: ComposeKind.NEW }
        .onEach { Timber.d("new composeKind: $it") }

    // Used in ComposeActivity to pass state to result function when cropImage contract inflight
    var cropImageItemOld: QueuedMedia? = null

    // TODO: Copied from MainViewModel. Probably belongs back in AccountManager

    /** True if the account's username should be shown, false otherwise. */
    val displaySelfUsername = sharedPreferencesRepository.changes.filter { it == PrefKeys.SHOW_SELF_USERNAME }.onStart { emit(null) }
        .combine(accountManager.accountsFlow) { _, accounts ->
            when (sharedPreferencesRepository.showSelfUsername) {
                ShowSelfUsername.ALWAYS -> true
                ShowSelfUsername.DISAMBIGUATE -> accounts.size > 1
                ShowSelfUsername.NEVER -> false
            }
        }

    private var setupComplete = false

//    val dirtyContent2 = combine(composeOptions, fContent) { initial, content ->
//        Timber.d("dirtyContent check: '$content'")
//        Timber.d("           initial: '${initial.content}'")
//        !content.contentEquals(initial.content.orEmpty())
//    }.onEach { Timber.d("dirtyContent?: $it") }
//
//    val dirtyContent3 = fContent.runningFold(Pair<Spanned?, Spanned?>(null, null)) { acc, value ->
//        (acc.first ?: value) to value
//    }
//        //   .runningFold(false) { _, value -> value.first.toString() != value.second.toString() }
//        .map { it.first.toString() != it.second.toString() }
//        .onEach { Timber.d("dirtyContent?: $it") }

    /**
     * Tracks the earliest and most recent emissions in a [Flow<T>] and returns
     * them as a [Pair<T, T>].
     *
     * The [first][Pair.first] item in the [Pair] is the earliest value in the original flow,
     * the [second][Pair.second] item is the latest value in the original flow.
     */
    fun <T> Flow<T>.trackFirstLast() = runningFold(null as Pair<T, T>?) { acc, value -> (acc?.first ?: value) to value }
        .filterNotNull()

    inline fun <T> Flow<Pair<T, T>>.dirtyIf2(crossinline transform: suspend (first: T, last: T) -> Boolean): Flow<Boolean> {
        return map { transform(it.first, it.second) }.distinctUntilChanged()
    }

    /**
     * Returns a [Flow<Boolean>][Flow] that tracks the "dirty" state of the upstream
     * flow.
     *
     * [predicate]'s arguments are the first emission in to the upstream flow (after
     * collecting this flow), and the most recent emission in to the upstream flow.
     * [predicate] should compare them and return either true (if they are meaningfully
     * different) or false otherwise.
     *
     * This flow is distinct, so emissions from the upstream flow that do not change the
     * dirty state do not generate a corresponding emission from this flow.
     */
    inline fun <T> Flow<T>.dirtyIf(crossinline predicate: suspend (first: T, last: T) -> Boolean): Flow<Boolean> {
        return trackFirstLast().map { predicate(it.first, it.second) }.distinctUntilChanged()
    }

    val dirtyContent = fContent // .trackFirstLast()
//        .map { it.first.toString() != it.second.toString() }
        .dirtyIf { first, last -> first.toString() != last.toString() }
        .onEach { Timber.d("dirtyContent?: $it") }

//    val dirtyContentWarning = combine(composeOptions, fEffectiveContentWarning) { initial, cw ->
//        cw != (initial.contentWarning.orEmpty())
//    }.onEach { Timber.d("dirtyContentWarning: $it") }

    val dirtyContentWarning = fEffectiveContentWarning
        .dirtyIf { first, last -> first != last }
        .onEach { Timber.d("dirtyContentWarning: $it") }

    // Note: This will get called multiple times when media is uploading as
    // QueuedMedia includes the upload percentage, which will change. At the
    // moment do nothing about this, but consider it for the future.
//    val dirtyMedia = combine(initialMedia, media) { initial, media ->
//        if (initial.size != media.size) return@combine true
//
//        initial.zip(media) { initial, media ->
//            if (initial.description.orEmpty() != media.description.orEmpty()) return@combine true
//            if (initial.uri != media.uri) return@combine true
//            if (initial.focus != media.focus) return@combine true
//        }
//
//        return@combine false
//    }.onEach { Timber.d("dirtyMedia: $it") }
    val dirtyMedia = media.dirtyIf { first, last ->
        if (first.size != last.size) return@dirtyIf true

        first.zip(last) { initial, media ->
            if (initial.description.orEmpty() != media.description.orEmpty()) return@dirtyIf true
            if (initial.uri != media.uri) return@dirtyIf true
            if (initial.focus != media.focus) return@dirtyIf true
        }

        return@dirtyIf false
    }.onEach { Timber.d("dirtyMedia: $it") }

    //    val dirtyPoll = combine(composeOptions.map { it.poll }, poll) { initial, poll ->
//        initial != poll
//    }.onEach { Timber.d("dirtyPoll: $it") }
    val dirtyPoll = poll.dirtyIf { first, second -> first != second }

    //    val dirtyLanguage = combine(composeOptions.map { it.language }, language) { initial, language ->
//        Timber.d("dirtyLanguage: $initial $language")
//        initial != language
//    }.onEach { Timber.d("dirtyLanguage: $it") }
    val dirtyLanguage = language.dirtyIf { first, last -> first != last }
        .onEach { Timber.d("dirtyLanguage: $it") }

//    val dirtyVisibility = combine(initialVisibility, statusVisibility) { initial, vis ->
//        initial != vis
//    }.onEach { Timber.d("dirtyVisibility: $it") }

    val dirtyVisibility = statusVisibility.dirtyIf { first, last -> first != last }
        .onEach { Timber.d("dirtyVisibility: $it") }

    /**
     * @return True if content of this status is "dirty", meaning one or more of the
     *   following have changed since the compose session started: content,
     *   content warning and content warning visibility, media, polls, or the
     *   scheduled time to send.
     */
    // TODO: Add modifiedInitialState, need a 6 param version of this function
    // TODO: Should consider the schedule time too
    // TODO: Should consider the visibility too
//    val fIsDirty = combine(dirtyContent, dirtyContentWarning, dirtyMedia, dirtyPoll, dirtyLanguage) {
//            dirtyContent,
//            dirtyContentWarning,
//            dirtyMedia,
//            dirtyPoll,
//            dirtyLanguage,
//        ->
//
//        Timber.d("isDirty: $dirtyContent $dirtyContentWarning $dirtyMedia $dirtyPoll $dirtyLanguage")
//
//        dirtyContent || dirtyContentWarning || dirtyMedia || dirtyPoll || dirtyLanguage
//    }

    val fIsDirty = combine(
        dirtyContent,
        dirtyContentWarning,
        dirtyMedia,
        dirtyPoll,
        dirtyLanguage,
        dirtyVisibility,
        // scheduleTime
        // modifiedInitialState, whatever that's for
    ) { flows -> flows.any { it == true } }.distinctUntilChanged()
        .onEach { Timber.d("isDirty: $it") }

    init {
        viewModelScope.launch {
            pachliAccountFlow.collect { account ->
                val composeOptions = composeOptions.value

                _scheduledAt.value = composeOptions.scheduledAt

                _showContentWarning.value = composeOptions.contentWarning.orEmpty().isNotEmpty()
                fContentWarning.value = composeOptions.contentWarning.orEmpty()

//                Timber.d("show content warning?: ${_showContentWarning.value}")
//                Timber.d("  actual cw: ${fContentWarning.value}")
                // Timber.d("  effect cw: ${fEffectiveContentWarning.value}")

//                pachliAccount = account
                instanceInfo.value = account.instanceInfo
                emojis.value = account.emojis

                // Fetch the list of languages from composeOptions (which may be null). Use
                // this to build the list of locales in the correct order for this account.
                // The initial language code is the first entry in this list, update the
                // value in composeOptions and _language to be consistent.
                _languages.value = getInitialLanguages(composeOptions.language, account.entity)
                val locales = getLocaleList(languages.value)
                val initialLanguageCode = locales.first().modernLanguageCode
                this@ComposeViewModel.composeOptions.update { it.copy(language = initialLanguageCode) }
                _language.value = initialLanguageCode

                // Set the visibility.
                //
                // Visible is set from (in-order):
                //
                // - Visibility in composeOptions (if present)
                // - The more private of the visibility of the status being replied to (if this
                //   is a reply), or the user's default visibility.
                //
                // If we don't know the status' visibility (because we only have the ID) then
                // fall back to the user's default visibility
                val visibility = composeOptions.visibility ?: (composeOptions.inReplyTo as? InReplyTo.Status)?.visibility?.let {
                    account.entity.defaultPostPrivacy.coerceAtLeast(it)
                } ?: account.entity.defaultPostPrivacy
//                initialVisibility.emit(visibility)
                _statusVisibility.value = visibility

                initialContent.emit(
                    composeOptions.mentionedUsernames?.let { mentionedUsernames ->
                        buildString {
                            mentionedUsernames.forEach {
                                append('@')
                                append(it)
                                append(' ')
                            }
                        }
                    } ?: composeOptions.content.orEmpty(),
                )

                val poll = composeOptions.poll
                if (poll != null &&
                    composeOptions.mediaAttachments.isNullOrEmpty() &&
                    composeOptions.draftAttachments.isNullOrEmpty()
                ) {
                    _poll.value = poll
                }

                // Recreate the attachments. This is either:
                //
                // - Attachments from a draft, in which case they must be re-uploaded and
                // attached.
                // - Existing attachments, in which case use them as is.
                val draftAttachments = composeOptions.draftAttachments
                if (draftAttachments != null) {
                    draftAttachments.forEach { attachment ->
                        // Don't emit the action, call onPickMedia directly. This ensures
                        // `media` is updated **before** setting the value of `initialMedia`
                        // a few lines later. Otherwise the media is set after, `initialMedia`
                        // doesn't include it, and the media is treated as dirty.
                        onPickMedia(
                            account,
                            FallibleUiAction.PickMedia(
                                attachment.uri,
                                attachment.description,
                                attachment.focus,
                            ),
                        )
                    }
                } else {
                    composeOptions.mediaAttachments?.forEach { attachment ->
                        // when coming from redraft or ScheduledTootActivity
                        val mediaType = when (attachment.type) {
                            Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
                            Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
                            Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
                        }
                        addUploadedMedia(account.entity, attachment.id, mediaType, attachment.url.toUri(), attachment.description, attachment.meta?.focus)
                    }
                }
//                initialMedia.emit(media.value)

                _initialUiState.emit(
                    InitialUiState(
                        account = account,

                        scheduledAt = composeOptions.scheduledAt,
                        content = composeOptions.mentionedUsernames?.let { mentionedUsernames ->
                            buildString {
                                mentionedUsernames.forEach {
                                    append('@')
                                    append(it)
                                    append(' ')
                                }
                            }
                        } ?: composeOptions.content.orEmpty(),
                        contentWarning = fContentWarning.value,

                        // TODO:
                        // initialVisibility
                        // languages
                        // poll
                    ),
                )

                launch {
                    uiAction.collect { onUiAction(account, it) }
                }
            }
        }
    }

    private suspend fun onUiAction(account: PachliAccount, uiAction: UiAction) {
        val result = when (uiAction) {
            is FallibleUiAction.AttachMedia -> onUpdateMedia(account, uiAction)
            FallibleUiAction.LoadInReplyTo -> reloadReply.emit(Unit)
            is FallibleUiAction.PickMedia -> onPickMedia(account, uiAction)
        }
        // TODO: Emit the result
    }

    /**
     * Copies selected media and adds to the upload queue.
     *
     * @param mediaUri [ContentResolver] URI for the file to copy
     * @param description media description / caption
     * @param focus focus, if relevant
     */
    private suspend fun onPickMedia(
        account: PachliAccount,
        action: FallibleUiAction.PickMedia,
    ): Result<Unit, UiError> = withContext(Dispatchers.IO) {
        val (type, uri, size) = mediaUploader.prepareMedia(action.uri, instanceInfo.value)
            .mapError { PickMediaError.PrepareMediaError(it, action) }
            .onFailure { return@withContext Err(it) }.unwrap()

        media.value.firstOrNull()?.let { firstItem ->
            if (type != QueuedMedia.Type.IMAGE && firstItem.type == QueuedMedia.Type.IMAGE) {
                _uiResult.send(Err(PickMediaError.MixedMediaTypesError(action)))
                return@withContext Err(PickMediaError.MixedMediaTypesError(action))
            }
        }

//        accept(FallibleUiAction.AttachMedia(type, uri, size, action.description, null))
        Timber.d("Adding media to queue")
        addMediaToQueue(account, type, uri, size)
        Timber.d("onPickMedia returning")
        return@withContext Ok(Unit)
    }

    /**
     * Copies selected media and adds to the upload queue.
     *
     * @param mediaUri [ContentResolver] URI for the file to copy
     * @param description media description / caption
     * @param focus focus, if relevant
     */
//    suspend fun pickMedia(mediaUri: Uri, description: String? = null) = withContext(Dispatchers.IO) {
//        val (type, uri, size) = mediaUploader.prepareMedia(mediaUri, instanceInfo.value)
//            .mapError { PickMediaError.PrepareMediaError(it) }.getOrElse { return@withContext Err(it) }
//        val mediaItems = media.value
//        if (type != QueuedMedia.Type.IMAGE && mediaItems.isNotEmpty() && mediaItems[0].type == QueuedMedia.Type.IMAGE) {
//            _uiResult.send(Err(PickMediaError.MixedMediaTypesError))
//        } else {
//            val queuedMedia = addMediaToQueue(type, uri, size, description, null)
// //            _uiResult.send(Ok(queuedMedia))
//        }
//    }

    private suspend fun onUpdateMedia(account: PachliAccount, uiAction: FallibleUiAction.AttachMedia) {
        addMediaToQueue(
            account,
            uiAction.type,
            uiAction.uri,
            uiAction.mediaSize,
            uiAction.description,
            uiAction.focus,
            uiAction.replaceItemId,
        )
    }

    internal suspend fun addMediaToQueue(
        account: PachliAccount,
        type: QueuedMedia.Type,
        uri: Uri,
        mediaSize: Long,
        description: String? = null,
        focus: Attachment.Focus? = null,
        replaceItemId: Int? = null,
    ): QueuedMedia {
        val mediaItem = QueuedMedia(
            account = account.entity,
            localId = mediaUploader.getNewLocalMediaId(),
            uri = uri,
            type = type,
            mediaSize = mediaSize,
            description = description,
            focus = focus,
            uploadState = Ok(UploadState.Uploading(percentage = 0)),
        )

        _media.update { mediaList ->
            replaceItemId?.let {
                mediaUploader.cancelUploadScope(replaceItemId)
                return@update mediaList.map { if (it.localId == replaceItemId) mediaItem else it }
            }

            return@update mediaList + mediaItem
        }

        viewModelScope.launch {
            mediaUploader
                .uploadMedia(mediaItem, instanceInfo.value)
                .collect { uploadState ->
                    updateMediaItem(mediaItem.localId) { it.copy(uploadState = uploadState) }
                }
        }

        return mediaItem
    }

    private fun addUploadedMedia(account: AccountEntity, id: String, type: QueuedMedia.Type, uri: Uri, description: String?, focus: Attachment.Focus?) {
        _media.update { mediaList ->
            val mediaItem = QueuedMedia(
                account = account,
                localId = mediaUploader.getNewLocalMediaId(),
                uri = uri,
                type = type,
                mediaSize = 0,
                description = description,
                focus = focus,
                uploadState = Ok(Uploaded.Published(id)),
            )
            mediaList + mediaItem
        }
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaUploader.cancelUploadScope(item.localId)
        _media.update { mediaList -> mediaList.filter { it.localId != item.localId } }
    }

    fun toggleMarkSensitive() {
        this._markMediaAsSensitive.value = this._markMediaAsSensitive.value != true
    }

    /** Call this when the status' primary content changes */
    fun onContentChanged(newContent: Editable) {
        // The Editable received from the activity is always the same as the content
        // it contains is mutable. Assigning it directly to .value would do nothing,
        // as this is a stateflow where modifications that set the same value are
        // ignored. So call .toSpanned() to ensure the new value is different from
        // the previous value.
        Timber.d("onContentChanged: newContent: ${newContent.hashCode()}")
        fContent.value = newContent.toSpanned()
    }

    /** Call this when the status' content warning changes */
    fun onContentWarningChanged(newContentWarning: String) {
        Timber.d("cw changed")
        fContentWarning.value = newContentWarning
    }

    /** Call this to attach or clear the status' poll */
    fun onPollChanged(newPoll: NewPoll?) {
        _poll.value = newPoll
    }

    /** Call this to change the status' visibility */
    fun onStatusVisibilityChanged(newVisibility: Status.Visibility) {
        _statusVisibility.value = newVisibility
    }

    /** Call this to change the status' language */
    fun onLanguageChanged(newLanguage: String) {
        _language.value = newLanguage
    }

//    @VisibleForTesting
//    fun updateStatusLength() {
//        _statusLength.value = statusLength(content, effectiveContentWarning, instanceInfo.value.charactersReservedPerUrl)
//    }

    val fCloseConfirmationKind = combine(fIsDirty, composeKind, fEffectiveContentWarning) { dirty, composeKind, cw ->
        Timber.d("Creating new closeConfirmationKind")
        if (!dirty) return@combine ConfirmationKind.NONE

        // TODO: The isEmpty checks here should probably be extended. At the moment this
        // doesn't consider added media or palls.
        return@combine when (composeKind) {
            ComposeKind.NEW -> if (isEmpty(fContent.value, cw)) {
                ConfirmationKind.NONE
            } else {
                ConfirmationKind.SAVE_OR_DISCARD
            }

            ComposeKind.EDIT_DRAFT -> if (isEmpty(fContent.value, cw)) {
                ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_DRAFT
            } else {
                ConfirmationKind.UPDATE_OR_DISCARD
            }

            ComposeKind.EDIT_POSTED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
            ComposeKind.EDIT_SCHEDULED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
        }
    }
        .onEach { Timber.d("New close confirmation: $it") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConfirmationKind.NONE)

    private fun isEmpty(content: CharSequence, contentWarning: CharSequence): Boolean {
        return !modifiedInitialState.value && (content.isBlank() && contentWarning.isBlank() && media.value.isEmpty() && poll.value == null)
    }

    fun showContentWarningChanged(value: Boolean) {
        _showContentWarning.value = value
    }

    fun deleteDraft(draftId: Int) {
        if (draftId == 0) return

        viewModelScope.launch {
//            draftId.replayCache.lastOrNull()?.takeIf { it != 0 }?.let {
//                draftHelper.deleteDraftAndAttachments(it)
//            }
            draftHelper.deleteDraftAndAttachments(draftId)
        }
    }

    fun stopUploads() {
        mediaUploader.cancelUploadScope(*media.value.map { it.localId }.toIntArray())
    }

    fun shouldShowSaveDraftDialog(): Boolean {
        // if any of the media files need to be downloaded first it could take a while,
        // so show a loading dialog
        return media.value.any { mediaValue ->
            mediaValue.uri.scheme == "https"
        }
    }

    suspend fun saveDraft(pachliAccountId: Long, draftId: Int, content: String, contentWarning: String) {
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
            pachliAccountId = pachliAccountId,
            inReplyToId = composeOptions.value.inReplyTo?.statusId,
            content = content,
            contentWarning = contentWarning,
            sensitive = markMediaAsSensitive.value,
            visibility = statusVisibility.value,
            mediaUris = mediaUris,
            mediaDescriptions = mediaDescriptions,
            mediaFocus = mediaFocus,
            poll = poll.value,
            failedToSend = false,
            failedToSendAlert = false,
            scheduledAt = scheduledAt.value,
            language = language.value,
            statusId = originalStatusId.value,
        )
    }

    /**
     * Send status to the server.
     * Uses current state plus provided arguments.
     */
    suspend fun sendStatus(
        pachliAccountId: Long,
        draftId: Int,
        content: String,
        spoilerText: String,
    ) {
        // TODO: Should probably return Result here if this fails, surface failure to
        // the user.
        scheduledPostId.value.takeIf { !it.isNullOrEmpty() }?.let {
            api.deleteScheduledStatus(it)
        }

        val attachedMedia = media.value.map { item ->
            MediaToSend(
                localId = item.localId,
                id = item.serverId,
                uri = item.uri.toString(),
                description = item.description,
                focus = item.focus,
                processed = item.uploadState.get() is Uploaded.Processed || item.uploadState.get() is Uploaded.Published,
            )
        }

        return
        val tootToSend = StatusToSend(
            text = content,
            warningText = spoilerText,
            visibility = statusVisibility.value.serverString(),
            sensitive = attachedMedia.isNotEmpty() && (markMediaAsSensitive.value || showContentWarning.value),
            media = attachedMedia,
            scheduledAt = scheduledAt.value,
            inReplyToId = composeOptions.value.inReplyTo?.statusId,
            poll = poll.value,
            replyingStatusContent = null,
            replyingStatusAuthorUsername = null,
            pachliAccountId = pachliAccountId,
            draftId = draftId,
            idempotencyKey = randomAlphanumericString(16),
            retries = 0,
            language = language.value,
            statusId = originalStatusId.value,
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

    fun updateDescription(localId: Int, serverId: String?, description: String) {
        updateMediaItem(localId) { it.copy(description = description) }
    }

    fun updateFocus(localId: Int, focus: Attachment.Focus) {
        updateMediaItem(localId) { mediaItem -> mediaItem.copy(focus = focus) }
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
                    .mapBoth({ response ->
                        response.body.hashtags.map {
                            AutocompleteResult.HashtagResult(
                                hashtag = it.name,
                                usage7d = it.history.sumOf { it.uses },
                            )
                        }.sortedByDescending { it.usage7d }
                    }, { e ->
                        Timber.e("Autocomplete search for %s failed: %s", token, e)
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

    fun setup(pachliAccountId: Long, composeOptions: ComposeOptions) {
//        if (setupComplete) {
//            return
//        }

        viewModelScope.launch {
            this@ComposeViewModel.composeOptions.value = composeOptions
            this@ComposeViewModel.pachliAccountId.emit(pachliAccountId)
        }

//        _scheduledAt.value = composeOptions.scheduledAt
//
//        _fContentWarning.value = composeOptions.contentWarning.orEmpty()
//
//        pachliAccount = account
//
//        val preferredVisibility = account.entity.defaultPostPrivacy
//
//        val replyVisibility = composeOptions.replyVisibility ?: Status.Visibility.UNKNOWN
//        startingVisibility = Status.Visibility.getOrUnknown(
//            preferredVisibility.ordinal.coerceAtLeast(replyVisibility.ordinal),
//        )
//
// //        if (!contentWarningStateChanged) {
// //            _showContentWarning.value = contentWarning.isNotBlank()
// //        }
//
//        // recreate media list
//        val draftAttachments = composeOptions.draftAttachments
//        if (draftAttachments != null) {
//            // when coming from DraftActivity
//            viewModelScope.launch {
//                draftAttachments.forEach { attachment ->
//                    pickMedia(attachment.uri, attachment.description, attachment.focus)
//                }
//            }
//        } else {
//            composeOptions.mediaAttachments?.forEach { a ->
//                // when coming from redraft or ScheduledTootActivity
//                val mediaType = when (a.type) {
//                    Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
//                    Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
//                    Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
//                }
//                addUploadedMedia(account.entity, a.id, mediaType, a.url.toUri(), a.description, a.meta?.focus)
//            }
//        }
//
//        val tootVisibility = composeOptions.visibility ?: Status.Visibility.UNKNOWN
//        if (tootVisibility != Status.Visibility.UNKNOWN) {
//            startingVisibility = tootVisibility
//        }
//        _statusVisibility.value = startingVisibility
//        val mentionedUsernames = composeOptions.mentionedUsernames
//        if (mentionedUsernames != null) {
//            val builder = StringBuilder()
//            for (name in mentionedUsernames) {
//                builder.append('@')
//                builder.append(name)
//                builder.append(' ')
//            }
//            initialContent = builder.toString()
//        }
//
//        val poll = composeOptions.poll
//        if (poll != null && composeOptions.mediaAttachments.isNullOrEmpty()) {
//            _poll.value = poll
//        }

//        updateCloseConfirmation()
        setupComplete = true
    }

    fun updateScheduledAt(newScheduledAt: Date?) {
        _scheduledAt.value = newScheduledAt
    }

    /**
     * True if editing a status that has already been posted.
     *
     * False otherwise (this includes editing a scheduled status that has not reached
     * the scheduled time yet).
     */
    val editing: Boolean
        get() = !originalStatusId.value.isNullOrEmpty()

    /** How to confirm with the user when leaving [ComposeActivity]. */
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
