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

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.text.Spanned
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.core.os.BundleCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.adapter.LocaleAdapter
import app.pachli.components.compose.ComposeViewModel.ConfirmationKind
import app.pachli.components.compose.dialog.makeFocusDialog
import app.pachli.components.compose.dialog.showAddPollDialog
import app.pachli.components.compose.view.ComposeScheduleView
import app.pachli.components.compose.view.ComposeVisibilityListener
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.mastodonLength
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Loadable
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Attachment
import app.pachli.core.model.Emoji
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_ATTACHMENTS
import app.pachli.core.model.Status
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.InReplyTo
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.InitialCursorPosition
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.preferences.AppTheme
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.extensions.await
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.ActivityComposeBinding
import app.pachli.languageidentification.LanguageIdentifier
import app.pachli.languageidentification.UNDETERMINED_LANGUAGE_TAG
import app.pachli.util.PickMediaFiles
import app.pachli.util.getInitialLanguages
import app.pachli.util.getLocaleList
import app.pachli.util.getMediaSize
import app.pachli.util.highlightSpans
import app.pachli.util.iconRes
import app.pachli.util.modernLanguageCode
import app.pachli.util.setDrawableTint
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Compose a status, either by creating one from scratch, or by editing an existing
 * status, draft, or scheduled status.
 */
@AndroidEntryPoint
class ComposeActivity :
    BaseActivity(),
    ComposeVisibilityListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider,
    OnReceiveContentListener,
    ComposeScheduleView.OnTimeSetListener {

    private lateinit var visibilityBehavior: BottomSheetBehavior<*>
    private lateinit var addAttachmentBehavior: BottomSheetBehavior<*>
    private lateinit var emojiBehavior: BottomSheetBehavior<*>
    private lateinit var scheduleBehavior: BottomSheetBehavior<*>

    private var photoUploadUri: Uri? = null

    private val avatarRadius48dp by unsafeLazy { resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp) }

    /** Float alpha value to use for views to indicate they are disabled. */
    private val disabledAlphaFloat by unsafeLazy {
        ResourcesCompat.getFloat(resources, MaterialR.dimen.material_emphasis_disabled)
    }

    /** Float alpha value to use for drawable resources to indicate they are disabled. */
    private val disabledAlphaInt by unsafeLazy { (disabledAlphaFloat * 255).toInt() }

    @VisibleForTesting
    var maximumTootCharacters = DEFAULT_CHARACTER_LIMIT

    @VisibleForTesting
    val viewModel: ComposeViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ComposeViewModel.Factory> { factory ->
                factory.create(
                    intent.pachliAccountId,
                    ComposeActivityIntent.getComposeOptions(intent),
                )
            }
        },
    )

    private val binding by viewBinding(ActivityComposeBinding::inflate)

    private var maxUploadMediaNumber = DEFAULT_MAX_MEDIA_ATTACHMENTS

    /** List of locales the user can choose from when posting. */
    private lateinit var locales: List<Locale>

    @Inject
    lateinit var languageIdentifierFactory: LanguageIdentifier.Factory

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // photoUploadUri should never be null at this point, but don't crash if it is.
        val uploadUri = photoUploadUri ?: return@registerForActivityResult
        if (success) {
            pickMedia(uploadUri)
        }
    }

    private val pickMediaFile = registerForActivityResult(PickMediaFiles()) { uris ->
        if (viewModel.media.value.size + uris.size > maxUploadMediaNumber) {
            Toast.makeText(this, resources.getQuantityString(R.plurals.error_upload_max_media_reached, maxUploadMediaNumber, maxUploadMediaNumber), Toast.LENGTH_SHORT).show()
        } else {
            uris.forEach { uri -> pickMedia(uri) }
        }
    }

    // Contract kicked off by editImageInQueue; expects viewModel.cropImageItemOld set
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        val uriNew = result.uriContent
        if (result.isSuccessful && uriNew != null) {
            viewModel.cropImageItemOld?.let { itemOld ->
                lifecycleScope.launch {
                    val size = getMediaSize(contentResolver, uriNew)

                    viewModel.addMediaToQueue(
                        itemOld.type,
                        uriNew,
                        size,
                        itemOld.description,
                        // Intentionally reset focus when cropping
                        null,
                        itemOld,
                    )
                }
            }
        } else if (result == CropImage.CancelledResult) {
            Timber.w("Edit image cancelled by user")
        } else {
            Timber.w(result.error, "Edit image failed")
            displayTransientMessage(R.string.error_image_edit_failed)
        }
        viewModel.cropImageItemOld = null
    }

    /**
     * Pressing back either (a) closes an open bottom sheet, or (b) goes
     * back, if no bottom sheets are open.
     */
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (visibilityBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                addAttachmentBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                emojiBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                scheduleBehavior.state == BottomSheetBehavior.STATE_EXPANDED
            ) {
                visibilityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                addAttachmentBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            handleCloseButton()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.toolbar.applyWindowInsets(
            left = InsetType.MARGIN,
            top = InsetType.PADDING,
            right = InsetType.MARGIN,
        )
        binding.composeMainScrollView.applyWindowInsets(
            left = InsetType.MARGIN,
            top = InsetType.MARGIN,
            right = InsetType.MARGIN,
            bottom = InsetType.MARGIN,
            withIme = true,
        )
        binding.composeBottomBar.applyWindowInsets(
            left = InsetType.PADDING,
            bottom = InsetType.PADDING,
            right = InsetType.PADDING,
            withIme = true,
        )
        binding.addMediaBottomSheet.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
        )
        binding.composeOptionsBottomSheet.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
        )
        binding.composeScheduleView.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
        )
        // Top/bottom insets are specified in the XML.
        binding.emojiPickerBottomSheet.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
        )

        if (sharedPreferencesRepository.appTheme == AppTheme.BLACK) {
            setTheme(DR.style.AppDialogActivityBlackTheme)
        }
        setContentView(binding.root)

        // Restore photoUploadUri early, as it's needed by `takePicture`.
        savedInstanceState?.let {
            photoUploadUri = BundleCompat.getParcelable(it, KEY_PHOTO_UPLOAD_URI, Uri::class.java)
        }

        setupActionBar()

        val composeOptions = ComposeActivityIntent.getComposeOptions(intent)

        binding.replyLoadingErrorRetry.setOnClickListener { viewModel.reloadReply() }

        lifecycleScope.launch { viewModel.inReplyTo.collect(::bindInReplyTo) }

        lifecycleScope.launch {
            viewModel.accountFlow.take(1).collect { account ->
                setupAvatar(account.entity)

                if (viewModel.displaySelfUsername) {
                    binding.composeUsernameView.text = getString(
                        R.string.compose_active_account_description,
                        account.entity.fullName,
                    )
                    binding.composeUsernameView.show()
                } else {
                    binding.composeUsernameView.hide()
                }

                viewModel.setup(account)

                setupLanguageSpinner(getInitialLanguages(composeOptions?.language, account.entity))

                setupButtons(account.id)

                if (savedInstanceState != null) {
                    setupComposeField(null, composeOptions)
                } else {
                    setupComposeField(viewModel.initialContent, composeOptions)
                }

                val mediaAdapter = MediaPreviewAdapter(
                    glide = glide,
                    descriptionLimit = account.instanceInfo.maxMediaDescriptionChars,
                    onDescriptionChanged = this@ComposeActivity::onUpdateDescription,
                    onEditFocus = { item ->
                        makeFocusDialog(item.focus, item.uri) { newFocus ->
                            viewModel.updateFocus(item.localId, newFocus)
                        }
                    },
                    onEditImage = { lifecycleScope.launch { editImageInQueue(it) } },
                    onRemoveMedia = this@ComposeActivity::removeMediaFromQueue,
                )

                subscribeToUpdates(mediaAdapter)

                binding.composeMediaPreviewBar.layoutManager =
                    LinearLayoutManager(this@ComposeActivity, LinearLayoutManager.VERTICAL, false)
                binding.composeMediaPreviewBar.adapter = mediaAdapter
                binding.composeMediaPreviewBar.itemAnimator = null

                composeOptions?.scheduledAt?.let {
                    binding.composeScheduleView.setDateTime(it)
                }

                bindContentWarning(composeOptions?.contentWarning)
                setupPollView()
                applyShareIntent(intent, savedInstanceState)

                /* Finally, overwrite state with data from saved instance state. */
                savedInstanceState?.let {
                    (it.getSerializable(KEY_VISIBILITY) as Status.Visibility).apply {
                        setStatusVisibility(this)
                    }

                    it.getBoolean(KEY_CONTENT_WARNING_VISIBLE).apply {
                        viewModel.showContentWarningChanged(this)
                    }

                    (it.getSerializable(KEY_SCHEDULED_TIME) as? Date)?.let { time ->
                        viewModel.updateScheduledAt(time)
                    }
                }

                // Ensure the focus starts in the edit field. Calling requestFocus() is not enough
                // to reliably show the keyboard, follow rec. from
                // https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility#ShowReliably
                binding.composeEditField.requestFocus()
                WindowCompat.getInsetsController(window, binding.composeEditField).show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    /**
     * Updates the status based on content coming from a share intent.
     *
     * If the intent contains attachments (image, video, audio) those are attached.
     *
     * The [Intent.EXTRA_SUBJECT] and [Intent.EXTRA_TEXT] are used to build
     * the initial status content.
     */
    private fun applyShareIntent(intent: Intent, savedInstanceState: Bundle?) {
        // Only act if savedInstanceState is null, otherwise the attachments from the
        // intent and the attachments from the instance state will both be added.
        if (savedInstanceState != null) return
        val type = intent.type ?: return

        if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                        pickMedia(uri)
                    }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.forEach { uri ->
                        pickMedia(uri)
                    }
                }
            }
        }

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val shareBody = if (!subject.isNullOrBlank() && subject !in text) {
            subject + '\n' + text
        } else {
            text
        }

        if (shareBody.isNotBlank()) {
            val start = binding.composeEditField.selectionStart.coerceAtLeast(0)
            val end = binding.composeEditField.selectionEnd.coerceAtLeast(0)
            val left = min(start, end)
            val right = max(start, end)
            binding.composeEditField.text.replace(left, right, shareBody, 0, shareBody.length)
            // move edittext cursor to first when shareBody parsed
            binding.composeEditField.text.insert(0, "\n")
            binding.composeEditField.setSelection(0)
        }
    }

    /**
     * Binds the [InReplyTo] data to the UI.
     *
     * If there is no [InReplyTo] data the "reply" portion of the UI is hidden.
     *
     * Otherwise, either show the reply, or, if loading the reply failed, show UI
     * that allows the user to retry fetching it.
     *
     * @param result
     */
    private fun bindInReplyTo(result: Result<Loadable<InReplyTo.Status?>, UiError.LoadInReplyToError>) {
        /** Hides the UI elements for an in-reply-to status. */
        fun hide() {
            binding.statusAvatar.hide()
            binding.statusAvatarInset.hide()
            binding.statusDisplayName.hide()
            binding.statusUsername.hide()
            binding.statusContentWarningDescription.hide()
            binding.statusContent.hide()
            binding.replyDivider.hide()
        }

        /** Shows the UI elements for an in-reply-to status. */
        fun show() {
            binding.statusAvatar.show()
            binding.statusAvatarInset.show()
            binding.statusDisplayName.show()
            binding.statusUsername.show()
            binding.statusContentWarningDescription.show()
            binding.statusContent.show()
            binding.replyDivider.show()
        }

        val loadable = result.getOrElse {
            hide()
            binding.replyProgressIndicator.hide()
            // Setup and show failure group
            binding.replyLoadingErrorMessage.text = it.fmt(this@ComposeActivity)
            binding.replyLoadingError.show()
            return
        }

        binding.replyLoadingError.hide()

        val inReplyTo = when (loadable) {
            is Loadable.Loaded -> loadable.data
            is Loadable.Loading -> {
                hide()
                binding.replyProgressIndicator.show()
                return
            }
        }

        binding.replyProgressIndicator.hide()

        // No reply? Hide all the reply UI and return.
        if (inReplyTo == null) {
            hide()
            return
        }

        show()

        with(inReplyTo) {
            bindReplyAvatar(this)

            binding.statusDisplayName.text =
                displayName.emojify(glide, emojis, binding.statusDisplayName, sharedPreferencesRepository.animateEmojis)
            binding.statusUsername.text = getString(DR.string.post_username_format, username)

            if (contentWarning.isEmpty()) {
                binding.statusContentWarningDescription.hide()
            } else {
                binding.statusContentWarningDescription.text =
                    contentWarning.emojify(glide, emojis, binding.statusContentWarningDescription, sharedPreferencesRepository.animateEmojis)
                binding.statusContentWarningDescription.show()
            }

            binding.statusContent.text =
                content.emojify(glide, emojis, binding.statusContent, sharedPreferencesRepository.animateEmojis)
        }
    }

    /** Loads the avatar of the account being replied to into the UI. */
    private fun bindReplyAvatar(inReplyTo: InReplyTo.Status) {
        binding.statusAvatar.setPaddingRelative(0, 0, 0, 0)
        if (viewModel.statusDisplayOptions.value.showBotOverlay && inReplyTo.isBot) {
            binding.statusAvatarInset.visibility = View.VISIBLE
            glide.load(DR.drawable.bot_badge)
                .into(binding.statusAvatarInset)
        } else {
            binding.statusAvatarInset.visibility = View.GONE
        }

        loadAvatar(
            glide,
            inReplyTo.avatarUrl,
            binding.statusAvatar,
            avatarRadius48dp,
            sharedPreferencesRepository.animateAvatars,
        )
    }

    /**
     * Binds [startingContentWarning] to the UI, listens for any changes to the
     * content warning and forwards to the [viewModel].
     */
    private fun bindContentWarning(startingContentWarning: String?) {
        binding.composeContentWarningField.doOnTextChanged { newContentWarning, _, _, _ ->
            viewModel.onContentWarningChanged(newContentWarning?.toString() ?: "")
        }
        if (startingContentWarning != null) {
            binding.composeContentWarningField.setText(startingContentWarning)
        }
    }

    private fun setupComposeField(
        startingText: String?,
        composeOptions: ComposeOptions?,
    ) {
        binding.composeEditField.setOnReceiveContentListener(this)

        binding.composeEditField.setOnKeyListener { _, keyCode, event -> this.onKeyDown(keyCode, event) }

        binding.composeEditField.setAdapter(
            ComposeAutoCompleteAdapter(
                glide,
                this,
                sharedPreferencesRepository.animateAvatars,
                sharedPreferencesRepository.animateEmojis,
                sharedPreferencesRepository.showBotOverlay,
            ),
        )
        binding.composeEditField.setTokenizer(ComposeTokenizer())

        val mentionColour = binding.composeEditField.linkTextColors.defaultColor
        highlightSpans(binding.composeEditField.text, mentionColour)
        binding.composeEditField.doAfterTextChanged { editable ->
            highlightSpans(editable!!, mentionColour)
            viewModel.onContentChanged(editable)
        }

        startingText?.let {
            binding.composeEditField.setText(it)

            when (composeOptions?.initialCursorPosition ?: InitialCursorPosition.END) {
                InitialCursorPosition.START -> binding.composeEditField.setSelection(0)
                InitialCursorPosition.END -> binding.composeEditField.setSelection(
                    binding.composeEditField.length(),
                )
            }
        }

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
        ) {
            binding.composeEditField.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun subscribeToUpdates(mediaAdapter: MediaPreviewAdapter) {
        lifecycleScope.launch {
            viewModel.instanceInfo.collect { instanceData ->
                maximumTootCharacters = instanceData.maxChars
                maxUploadMediaNumber = instanceData.maxMediaAttachments
                updateVisibleCharactersLeft(viewModel.statusLength.value)
            }
        }

        lifecycleScope.launch {
            viewModel.statusLength.collect { updateVisibleCharactersLeft(it) }
        }

        lifecycleScope.launch {
            viewModel.closeConfirmationKind.collect { updateOnBackPressedCallbackState(it, bottomSheetStates()) }
        }

        lifecycleScope.launch {
            viewModel.emojis.collect(::bindEmojiList)
        }

        lifecycleScope.launch {
            viewModel.showContentWarning.combine(viewModel.markMediaAsSensitive) { showContentWarning, markSensitive ->
                updateMarkSensitiveButton(markSensitive, showContentWarning)
                showContentWarning(showContentWarning)
            }.collect()
        }

        lifecycleScope.launch {
            viewModel.statusVisibility.collect(::setStatusVisibility)
        }

        lifecycleScope.launch {
            viewModel.media.collect { media ->
                mediaAdapter.submitList(media)

                binding.composeMediaPreviewBar.visible(media.isNotEmpty())
                updateMarkSensitiveButton(viewModel.markMediaAsSensitive.value, viewModel.showContentWarning.value)
            }
        }

        lifecycleScope.launch {
            viewModel.poll.collect { poll ->
                binding.pollPreview.visible(poll != null)
                poll?.let(binding.pollPreview::setPoll)
            }
        }

        lifecycleScope.launch {
            viewModel.scheduledAt.collect { scheduledAt ->
                if (scheduledAt == null) {
                    binding.composeScheduleView.resetSchedule()
                } else {
                    binding.composeScheduleView.setDateTime(scheduledAt)
                }
                updateScheduleButton()
            }
        }

        // Hide the "Schedule" button if the server can't schedule. Simply
        // disabling it could be confusing to users wondering why they can't
        // use it.
        lifecycleScope.launch {
            viewModel.serverCanSchedule.collect {
                binding.composeScheduleButton.visible(it)
            }
        }

        // Determine whether the add attachment options are enabled.
        lifecycleScope.launch {
            viewModel.media.combine(viewModel.poll) { media, poll ->
                val active = poll == null &&
                    media.size < maxUploadMediaNumber &&
                    (media.isEmpty() || media.first().type == QueuedMedia.Type.IMAGE)
                enableButton(binding.composeAddAttachmentButton, active, active)
                enablePollButton(media.isEmpty())
            }.collect()
        }
    }

    /** @return List of states of the different bottomsheets */
    private fun bottomSheetStates() = listOf(
        visibilityBehavior.state,
        addAttachmentBehavior.state,
        emojiBehavior.state,
        scheduleBehavior.state,
    )

    /**
     * Enables [onBackPressedCallback] if a confirmation is required, or any botttom sheet is
     * open. Otherwise disables.
     */
    private fun updateOnBackPressedCallbackState(confirmationKind: ConfirmationKind, bottomSheetStates: List<Int>) {
        onBackPressedCallback.isEnabled = confirmationKind != ConfirmationKind.NONE ||
            bottomSheetStates.any { it != BottomSheetBehavior.STATE_HIDDEN }
    }

    private fun setupButtons(pachliAccountId: Long) {
        binding.composeOptionsBottomSheet.listener = this

        visibilityBehavior = BottomSheetBehavior.from(binding.composeOptionsBottomSheet)
        addAttachmentBehavior = BottomSheetBehavior.from(binding.addMediaBottomSheet)
        scheduleBehavior = BottomSheetBehavior.from(binding.composeScheduleView)
        emojiBehavior = BottomSheetBehavior.from(binding.emojiPickerBottomSheet).apply {
            addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        // Adjust the "Toot" button state; disabled when the emoji
                        // sheet is open, enabled when closed, so tapping emojis
                        // can't mis-click and send the post too early.
                        //
                        // When the sheet is open focus shifts to the filter view,
                        // when closed focus shifts to the editor.
                        when {
                            newState == BottomSheetBehavior.STATE_SETTLING -> {
                                binding.composeTootButton.isEnabled = !binding.composeTootButton.isEnabled
                            }

                            newState == BottomSheetBehavior.STATE_EXPANDED -> {
                                binding.composeTootButton.isEnabled = false
                                binding.emojiPickerBottomSheet.requestFocus()
                            }

                            newState == BottomSheetBehavior.STATE_HIDDEN -> {
                                binding.composeTootButton.isEnabled = true
                                binding.composeEditField.requestFocus()
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
                },
            )
        }

        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateOnBackPressedCallbackState(viewModel.closeConfirmationKind.value, bottomSheetStates())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { }
        }
        visibilityBehavior.addBottomSheetCallback(bottomSheetCallback)
        addAttachmentBehavior.addBottomSheetCallback(bottomSheetCallback)
        scheduleBehavior.addBottomSheetCallback(bottomSheetCallback)
        emojiBehavior.addBottomSheetCallback(bottomSheetCallback)

        enableButton(binding.composeEmojiButton, clickable = false, colorActive = false)

        // Setup the interface buttons.
        binding.composeTootButton.setOnClickListener { onSendClick(pachliAccountId) }
        binding.composeAddAttachmentButton.setOnClickListener { onAddAttachmentClick() }
        binding.composeChangeVisibilityButton.setOnClickListener { onChangeVisibilityClick() }
        binding.composeContentWarningButton.setOnClickListener { onContentWarningChanged() }
        binding.composeEmojiButton.setOnClickListener { onEmojiClick() }
        binding.composeMarkSensitiveButton.setOnClickListener { onMarkSensitiveClick() }
        binding.composeScheduleButton.setOnClickListener { onScheduleClick() }
        binding.composeScheduleView.setResetOnClickListener { resetSchedule() }
        binding.composeScheduleView.setListener(this)
        binding.atButton.setOnClickListener { atButtonClicked() }
        binding.hashButton.setOnClickListener { hashButtonClicked() }

        val cameraIcon = makeIcon(this, GoogleMaterial.Icon.gmd_camera_alt, IconicsSize.dp(18))
        binding.actionPhotoTake.setCompoundDrawablesRelativeWithIntrinsicBounds(cameraIcon, null, null, null)

        val imageIcon = makeIcon(this, GoogleMaterial.Icon.gmd_image, IconicsSize.dp(18))
        binding.actionAddMedia.setCompoundDrawablesRelativeWithIntrinsicBounds(imageIcon, null, null, null)

        val pollIcon = makeIcon(this, GoogleMaterial.Icon.gmd_poll, IconicsSize.dp(18))
        binding.addPollTextActionTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(pollIcon, null, null, null)

        binding.actionPhotoTake.visible(Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager) != null)

        binding.actionPhotoTake.setOnClickListener { lifecycleScope.launch { initiateCameraApp() } }
        binding.actionAddMedia.setOnClickListener { onAddMediaClick() }
        binding.addPollTextActionTextView.setOnClickListener { onAddPollClick() }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setupLanguageSpinner(initialLanguages: List<String>) {
        binding.composePostLanguageButton.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.onLanguageChanged((parent.adapter.getItem(position) as Locale).modernLanguageCode)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                parent.setSelection(0)
            }
        }
        binding.composePostLanguageButton.apply {
            locales = getLocaleList(initialLanguages)
            adapter = LocaleAdapter(context, android.R.layout.simple_spinner_dropdown_item, locales)
            setSelection(0)
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.run {
            title = null
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(app.pachli.core.ui.R.drawable.ic_close_24dp)
        }
    }

    private fun setupAvatar(account: AccountEntity) {
        val actionBarSizeAttr = intArrayOf(androidx.appcompat.R.attr.actionBarSize)
        val avatarSize = obtainStyledAttributes(null, actionBarSizeAttr).use { a ->
            a.getDimensionPixelSize(0, 1)
        }

        loadAvatar(
            glide,
            account.profilePictureUrl,
            binding.composeAvatar,
            avatarSize / 8,
            sharedPreferencesRepository.animateAvatars,
        )
        binding.composeAvatar.contentDescription = getString(
            R.string.compose_active_account_description,
            account.fullName,
        )
    }

    private fun replaceTextAtCaret(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = binding.composeEditField.selectionStart.coerceAtMost(binding.composeEditField.selectionEnd)
        val end = binding.composeEditField.selectionStart.coerceAtLeast(binding.composeEditField.selectionEnd)
        val textToInsert = if (start > 0 && !binding.composeEditField.text[start - 1].isWhitespace()) {
            " $text"
        } else {
            text
        }
        binding.composeEditField.text.replace(start, end, textToInsert)

        // Set the cursor after the inserted text
        binding.composeEditField.setSelection(start + text.length)
    }

    /**
     * Inserts [text] in to the editor immediately before each word in the
     * selected text.
     *
     * E.g., given current text is `foo bar baz` and [text] is `#`
     *
     * If `bar baz` is selected the result is `foo #bar #baz`.
     *
     * Only words that start in the selection are considered.
     *
     * - If `r baz` is selected the result is `foo bar #baz`.
     * - If `bar ba` is selected the result is `foo #bar #baz`.
     *
     * If no text is selected [text] is inserted at the caret.
     */
    fun prependSelectedWordsWith(text: CharSequence) = with(binding.composeEditField) {
        // Ensure selectionStart < selectionEnd (selecting backwards in an EditText
        // can cause this).
        val start = selectionStart.coerceAtMost(selectionEnd)
        val end = selectionStart.coerceAtLeast(selectionEnd)
        val editorText = getText()

        // No selection? Insert at caret, move the cursor to immediately after the
        // insertion, and finish.
        if (start == end) {
            editorText.insert(start, text)
            setSelection(start + text.length)
            return
        }

        var wasWord: Boolean
        var isWord = end < editorText.length && !Character.isWhitespace(editorText[end])
        var newEnd = end

        // Iterate the selection backward so we don't have to juggle indices on insertion.
        var index = end - 1
        while (index >= start - 1 && index >= 0) {
            wasWord = isWord
            isWord = !Character.isWhitespace(editorText[index])
            if (wasWord && !isWord) {
                // We've reached the beginning of a word, perform insert
                editorText.insert(index + 1, text)
                newEnd += text.length
            }
            --index
        }

        if (start == 0 && isWord) {
            // Special case when the selection includes the start of the text.
            editorText.insert(0, text)
            newEnd += text.length
        }

        // Keep the same text (including insertions) selected.
        setSelection(start, newEnd)
    }

    private fun atButtonClicked() {
        prependSelectedWordsWith("@")
    }

    private fun hashButtonClicked() {
        prependSelectedWordsWith("#")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(KEY_PHOTO_UPLOAD_URI, photoUploadUri)
        outState.putSerializable(KEY_VISIBILITY, viewModel.statusVisibility.value)
        outState.putBoolean(KEY_CONTENT_WARNING_VISIBLE, viewModel.showContentWarning.value)
        outState.putSerializable(KEY_SCHEDULED_TIME, viewModel.scheduledAt.value)
        super.onSaveInstanceState(outState)
    }

    private fun displayPermamentMessage(message: String) {
        val bar = Snackbar.make(binding.activityCompose, message, Snackbar.LENGTH_INDEFINITE)
        // necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimension(DR.dimen.compose_activity_snackbar_elevation)
        bar.setAnchorView(R.id.composeBottomBar)
        bar.show()
    }

    /** Displays a [Snackbar] showing [message], anchored to the bottom bar. */
    private fun displayTransientMessage(message: String) {
        val bar = Snackbar.make(binding.activityCompose, message, Snackbar.LENGTH_LONG)
        // necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimension(DR.dimen.compose_activity_snackbar_elevation)
        bar.setAnchorView(R.id.composeBottomBar)
        bar.show()
    }

    /** Displays a [Snackbar] showing [stringId], anchored to the bottom bar. */
    private fun displayTransientMessage(@StringRes stringId: Int) {
        displayTransientMessage(getString(stringId))
    }

    /**
     * Handles clicking the "mark sensitive" (eye) button in the bottom bar.
     *
     * Toggles the state of the status' sensitivity.
     */
    private fun onMarkSensitiveClick() {
        this.viewModel.toggleMarkSensitive()
    }

    private fun updateMarkSensitiveButton(markMediaSensitive: Boolean, contentWarningShown: Boolean) = with(binding.composeMarkSensitiveButton) {
        if (viewModel.media.value.isEmpty()) {
            hide()
        } else {
            show()

            when {
                contentWarningShown -> {
                    // Control is disabled, content warning forces media to be sensitive.
                    alpha = disabledAlphaFloat
                    isClickable = false
                    setImageResource(R.drawable.ic_hide_media_24dp)
                    drawable.clearColorFilter()
                }

                markMediaSensitive -> {
                    // Control is active, icon and colour highlight this.
                    alpha = 1F
                    isClickable = true
                    setImageResource(R.drawable.ic_hide_media_24dp)
                    setDrawableTint(this@ComposeActivity, drawable, android.R.attr.colorPrimary)
                }

                else -> {
                    // Control is available for use.
                    alpha = 1F
                    isClickable = true
                    setImageResource(R.drawable.ic_eye_24dp)
                    setDrawableTint(this@ComposeActivity, drawable, android.R.attr.colorControlNormal)
                }
            }
        }
    }

    private fun updateScheduleButton() {
        if (viewModel.editing) {
            // Can't reschedule a published status
            enableButton(binding.composeScheduleButton, clickable = false, colorActive = false)
        } else {
            val attr = if (viewModel.scheduledAt.value == null) {
                android.R.attr.colorControlNormal
            } else {
                android.R.attr.colorPrimary
            }

            @ColorInt val color = MaterialColors.getColor(binding.composeScheduleButton, attr)
            binding.composeScheduleButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    /**
     * Enables / disables the bottom bar buttons.
     *
     * @param enable True if the buttons should be enabled.
     * @param editing True if editing a posted status (disables visibility and
     * scheduling buttons irrespective of [enable]).
     */
    private fun enableButtons(enable: Boolean, editing: Boolean) {
        binding.composeAddAttachmentButton.isClickable = enable
        binding.composeChangeVisibilityButton.isClickable = enable && !editing
        binding.composeEmojiButton.isClickable = enable && viewModel.emojis.value.isNotEmpty()
        binding.composeMarkSensitiveButton.isClickable = enable
        binding.composeScheduleButton.isClickable = enable && !editing
        binding.composeTootButton.isEnabled = enable
    }

    private fun setStatusVisibility(visibility: Status.Visibility) {
        binding.composeOptionsBottomSheet.setStatusVisibility(visibility)
        binding.composeTootButton.setStatusVisibility(binding.composeTootButton, visibility)

        val iconRes = visibility.iconRes() ?: R.drawable.ic_lock_open_24dp
        binding.composeChangeVisibilityButton.setImageResource(iconRes)
        if (viewModel.editing) {
            // Can't update visibility on published status
            enableButton(binding.composeChangeVisibilityButton, clickable = false, colorActive = false)
        }
    }

    /**
     * Handles clicking the "Visibility" button in the bottom bar.
     *
     * Shows/hides the bottom sheet displaying a menu of visibility options.
     */
    private fun onChangeVisibilityClick() {
        if (visibilityBehavior.state == BottomSheetBehavior.STATE_HIDDEN || visibilityBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            visibilityBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            addAttachmentBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            visibilityBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    /**
     * Handles clicking the "schedule" button in the bottom bar.
     *
     * Either:
     *
     * - Shows the UI to choose a date/time, if the status is not currently
     * scheduled (see [ComposeScheduleView.openPickDateDialog]), or
     * - Shows/hides a UI to edit/clear the scheduling data (see [showScheduleView].
     */
    private fun onScheduleClick() {
        if (viewModel.scheduledAt.value == null) {
            binding.composeScheduleView.openPickDateDialog()
        } else {
            showScheduleView()
        }
    }

    /**
     * Shows a bottom sheet displaying the scheduled date/time for the status,
     * and a UI to clear it.
     */
    private fun showScheduleView() {
        if (scheduleBehavior.state == BottomSheetBehavior.STATE_HIDDEN || scheduleBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            scheduleBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            visibilityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            addAttachmentBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    /**
     * Handles clicking the "emoji" button in the bottom bar.
     *
     * Shows/hides the bottom sheet displaying an emoji grid to choose from.
     */
    private fun onEmojiClick() {
        if (emojiBehavior.state == BottomSheetBehavior.STATE_HIDDEN || emojiBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            emojiBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            visibilityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            addAttachmentBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    /**
     * Handles clicking the "Add attachment" button in the bottom bar.
     *
     * Shows/hides the bottom sheet displaying a menu of attachment options.
     */
    private fun onAddAttachmentClick() {
        if (addAttachmentBehavior.state == BottomSheetBehavior.STATE_HIDDEN || addAttachmentBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            addAttachmentBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            visibilityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            addAttachmentBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    /**
     * Handles clicking the "Add media" button in the "Add attachment" menu.
     *
     * Requests the necessary permissions, or directly launches
     * [pickMediaFile]. If permissions are necessary the media picker is
     * launched in [onRequestPermissionsResult].
     */
    private fun onAddMediaClick() {
        addAttachmentBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // Wait until bottom sheet is not collapsed and show next screen after
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        addAttachmentBehavior.removeBottomSheetCallback(this)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this@ComposeActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(
                                this@ComposeActivity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE,
                            )
                        } else {
                            pickMediaFile.launch(true)
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            },
        )
        addAttachmentBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    /**
     * Handles clicking the "Add poll" button in the "Add attachment" menu.
     */
    private fun onAddPollClick() = lifecycleScope.launch {
        addAttachmentBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        val instanceParams = viewModel.instanceInfo.value
        showAddPollDialog(
            context = this@ComposeActivity,
            poll = viewModel.poll.value,
            maxOptionCount = instanceParams.pollMaxOptions,
            maxOptionLength = instanceParams.pollMaxLength,
            minDuration = instanceParams.pollMinDuration,
            maxDuration = instanceParams.pollMaxDuration,
            onUpdatePoll = viewModel::onPollChanged,
        )
    }

    private fun setupPollView() {
        binding.pollPreview.setOnClickListener {
            val popup = PopupMenu(this, binding.pollPreview)
            val editId = 1
            val removeId = 2
            popup.menu.add(0, editId, 0, R.string.edit_poll)
            popup.menu.add(0, removeId, 0, R.string.action_remove)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    editId -> onAddPollClick()
                    removeId -> removePoll()
                }
                true
            }
            popup.show()
        }
    }

    private fun removePoll() {
        viewModel.onPollChanged(null)
        binding.pollPreview.hide()
    }

    override fun onVisibilityChanged(visibility: Status.Visibility) {
        viewModel.onStatusVisibilityChanged(visibility)
        visibilityBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    @VisibleForTesting
    val selectedLanguage: String?
        get() = viewModel.language

    private fun updateVisibleCharactersLeft(textLength: Int) {
        val remainingLength = maximumTootCharacters - textLength
        binding.composeCharactersLeftView.text = String.format(Locale.getDefault(), "%d", remainingLength)

        val textColor = if (remainingLength < 0) {
            MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)
        } else {
            MaterialColors.getColor(binding.composeCharactersLeftView, android.R.attr.textColorTertiary)
        }
        binding.composeCharactersLeftView.setTextColor(textColor)
    }

    private fun onContentWarningChanged() {
        val showWarning = binding.composeContentWarningBar.isGone
        viewModel.showContentWarningChanged(showWarning)
    }

    private fun verifyScheduledTime(): Boolean {
        return binding.composeScheduleView.verifyScheduledTime(viewModel.scheduledAt.value)
    }

    private fun onSendClick(pachliAccountId: Long) = lifecycleScope.launch {
        if (viewModel.confirmStatusLanguage) confirmStatusLanguage()

        if (verifyScheduledTime()) {
            sendStatus(pachliAccountId)
        } else {
            showScheduleView()
        }
    }

    /**
     * Check the status' language.
     *
     * Try and identify the language the status is written in, and compare that with
     * the language the user selected. If the selected language is not in the top three
     * detected languages, and the language was detected at 60+% confidence then prompt
     * the user to change the language before posting.
     */
    private suspend fun confirmStatusLanguage() {
        // Note: There's some dancing around here because the language identifiers
        // are BCP-47 codes (potentially with multiple components) and the Mastodon API wants ISO 639.
        // See https://github.com/mastodon/mastodon/issues/23541

        // Null check. Shouldn't be necessary
        val currentLang = viewModel.language ?: return

        // Try and identify the language the status is written in. Limit to the
        // first three possibilities. Don't show errors to the user, just bail,
        // as there's nothing they can do to resolve any error.
        val languages = languageIdentifierFactory.newInstance().use {
            it.identifyPossibleLanguages(binding.composeEditField.text.toString())
                .getOrElse {
                    Timber.d("error when identifying languages: %s", it)
                    return
                }
        }

        // If there are no matches then bail
        // Note: belt and braces, shouldn't happen per documented behaviour, as at
        // least one item should always be returned.
        if (languages.isEmpty()) return

        // Ignore results where the language could not be determined.
        if (languages.first().languageTag == UNDETERMINED_LANGUAGE_TAG) return

        // If the current language is any of the ones detected then it's OK.
        if (languages.any { it.languageTag.startsWith(currentLang) }) return

        // Warn the user about the language mismatch only if 60+% sure of the guess.
        val detectedLang = languages.first()
        if (detectedLang.confidence < 0.6) return

        // Viewmodel's language tag has just the first component (e.g., "zh"), the
        // guessed language might more (e.g,. "-Hant"), so trim to just the first.
        val detectedLangTruncatedTag = detectedLang.languageTag.split('-', limit = 2)[0]

        val localeList = getLocaleList(emptyList()).associateBy { it.modernLanguageCode }

        val detectedLocale = localeList[detectedLangTruncatedTag] ?: return
        val detectedDisplayLang = detectedLocale.displayLanguage
        val currentDisplayLang = localeList[viewModel.language]?.displayLanguage ?: return

        // Otherwise, show the dialog.
        val dialog = AlertDialog.Builder(this@ComposeActivity)
            .setTitle(R.string.compose_warn_language_dialog_title)
            .setMessage(
                getString(
                    R.string.compose_warn_language_dialog_fmt,
                    currentDisplayLang,
                    detectedDisplayLang,
                ),
            )
            .create()
            .await(
                getString(R.string.compose_warn_language_dialog_change_language_fmt, detectedDisplayLang),
                getString(R.string.compose_warn_language_dialog_accept_language_fmt, currentDisplayLang),
                getString(R.string.compose_warn_language_dialog_accept_and_dont_ask_fmt, currentDisplayLang),
            )

        if (dialog == AlertDialog.BUTTON_POSITIVE) {
            viewModel.onLanguageChanged(detectedLangTruncatedTag)
            locales.indexOf(detectedLocale).takeIf { it != -1 }?.let {
                binding.composePostLanguageButton.setSelection(it)
            }
        }

        if (dialog == AlertDialog.BUTTON_NEUTRAL) viewModel.confirmStatusLanguage = false
    }

    /** This is for the fancy keyboards which can insert images and stuff, and drag&drop etc */
    override fun onReceiveContent(view: View, contentInfo: ContentInfoCompat): ContentInfoCompat? {
        if (contentInfo.clip.description.hasMimeType("image/*")) {
            val split = contentInfo.partition { item: ClipData.Item -> item.uri != null }
            split.first?.let { content ->
                for (i in 0 until content.clip.itemCount) {
                    pickMedia(
                        content.clip.getItemAt(i).uri,
                        contentInfo.clip.description.label as String?,
                    )
                }
            }
            return split.second
        }
        return contentInfo
    }

    private fun sendStatus(pachliAccountId: Long) {
        enableButtons(false, viewModel.editing)
        val contentText = binding.composeEditField.text.toString()
        var spoilerText = ""
        if (viewModel.showContentWarning.value) {
            spoilerText = binding.composeContentWarningField.text.toString()
        }
        val statusLength = viewModel.statusLength.value
        if ((statusLength <= 0 || contentText.isBlank()) && viewModel.media.value.isEmpty()) {
            binding.composeEditField.error = getString(R.string.error_empty)
            enableButtons(true, viewModel.editing)
        } else if (statusLength <= maximumTootCharacters) {
            lifecycleScope.launch {
                viewModel.sendStatus(contentText, spoilerText, pachliAccountId)
                deleteDraftAndFinish()
            }
        } else {
            binding.composeEditField.error = getString(R.string.error_compose_character_limit)
            enableButtons(true, viewModel.editing)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickMediaFile.launch(true)
            } else {
                Snackbar.make(
                    binding.activityCompose,
                    R.string.error_media_upload_permission,
                    Snackbar.LENGTH_SHORT,
                ).apply {
                    setAction(app.pachli.core.ui.R.string.action_retry) { onAddMediaClick() }
                    // necessary so snackbar is shown over everything
                    view.elevation = resources.getDimension(DR.dimen.compose_activity_snackbar_elevation)
                    show()
                }
            }
        }
    }

    private suspend fun initiateCameraApp() = withContext(Dispatchers.IO) {
        addAttachmentBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val photoFile: File = try {
            createNewImageFile(this@ComposeActivity)
        } catch (_: IOException) {
            displayTransientMessage(R.string.error_media_upload_opening)
            return@withContext
        }

        // Continue only if the File was successfully created
        photoUploadUri = FileProvider.getUriForFile(
            this@ComposeActivity,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            photoFile,
        )?.also {
            takePicture.launch(it)
        }
    }

    private fun enableButton(button: ImageButton, clickable: Boolean, colorActive: Boolean) {
        button.isEnabled = clickable
        if (colorActive) {
            setDrawableTint(this, button.drawable, android.R.attr.textColorTertiary)
        } else {
            button.drawable.clearColorFilter()
        }
    }

    private fun enablePollButton(enable: Boolean) {
        binding.addPollTextActionTextView.isEnabled = enable
        binding.addPollTextActionTextView.compoundDrawables.getOrNull(0)?.let { drawable ->
            if (enable) {
                drawable.alpha = 255
            } else {
                drawable.alpha = disabledAlphaInt
            }
        }
    }

    private suspend fun editImageInQueue(item: QueuedMedia) {
        // If input image is lossless, output image should be lossless.
        // Currently the only supported lossless format is png.
        val mimeType: String? = contentResolver.getType(item.uri)
        val isPng: Boolean = mimeType != null && mimeType.endsWith("/png")
        val tempFile = createNewImageFile(this, if (isPng) ".png" else ".jpg")

        // "Authority" must be the same as the android:authorities string in AndroidManifest.xml
        val uriNew = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", tempFile)

        viewModel.cropImageItemOld = item

        cropImage.launch(
            CropImageContractOptions(
                uri = item.uri,
                cropImageOptions = CropImageOptions(
                    customOutputUri = uriNew,
                    outputCompressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                ),
            ),
        )
    }

    private fun removeMediaFromQueue(item: QueuedMedia) {
        viewModel.removeMediaFromQueue(item)
    }

    private fun pickMedia(uri: Uri, description: String? = null) {
        lifecycleScope.launch {
            viewModel.pickMedia(uri, description).onFailure {
                val message = getString(
                    R.string.error_pick_media_fmt,
                    it.fmt(this@ComposeActivity),
                )
                displayPermamentMessage(message)
            }
        }
    }

    /**
     * Shows/hides the content warning area depending on [show].
     *
     * Adjusts the colours of the content warning button to reflect the state.
     */
    private fun showContentWarning(show: Boolean) {
        // Skip any animations if the current visibility matches the intended visibility. This
        // prevents a visual oddity where the compose editor animates in to view when first
        // opening the activity.
        if (binding.composeContentWarningBar.isVisible == show) return
        TransitionManager.beginDelayedTransition(binding.composeContentWarningBar.parent as ViewGroup)
        @ColorInt val color = if (show) {
            binding.composeContentWarningBar.show()
            binding.composeContentWarningField.setSelection(binding.composeContentWarningField.text.length)
            binding.composeContentWarningField.requestFocus()
            MaterialColors.getColor(binding.composeContentWarningButton, android.R.attr.colorPrimary)
        } else {
            binding.composeContentWarningBar.hide()
            binding.composeEditField.requestFocus()
            MaterialColors.getColor(binding.composeContentWarningButton, android.R.attr.colorControlNormal)
        }
        binding.composeContentWarningButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleCloseButton()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    // send toot by pressing CTRL + ENTER
                    this.onSendClick(intent.pachliAccountId)
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleCloseButton() {
        val contentText = binding.composeEditField.text.toString()
        val contentWarning = binding.composeContentWarningField.text.toString()
        when (viewModel.closeConfirmationKind.value) {
            ConfirmationKind.NONE -> {
                viewModel.stopUploads()
                finish()
            }
            ConfirmationKind.SAVE_OR_DISCARD ->
                getSaveAsDraftOrDiscardDialog(contentText, contentWarning).show()
            ConfirmationKind.UPDATE_OR_DISCARD ->
                getUpdateDraftOrDiscardDialog(contentText, contentWarning).show()
            ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES ->
                getContinueEditingOrDiscardDialog().show()
            ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_DRAFT ->
                getDeleteEmptyDraftOrContinueEditing().show()
        }
    }

    /**
     * User is editing a new post, and can either save the changes as a draft or discard them.
     */
    private fun getSaveAsDraftOrDiscardDialog(contentText: String, contentWarning: String): AlertDialog.Builder {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.compose_save_draft)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_delete) { _, _ ->
                viewModel.stopUploads()
                deleteDraftAndFinish()
            }

        if (viewModel.media.value.isNotEmpty()) {
            builder.setMessage(R.string.compose_save_draft_loses_media)
        }

        return builder
    }

    /**
     * User is editing an existing draft, and can either update the draft with the new changes or
     * discard them.
     */
    private fun getUpdateDraftOrDiscardDialog(contentText: String, contentWarning: String): AlertDialog.Builder {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.compose_save_draft)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                viewModel.stopUploads()
                finish()
            }

        if (viewModel.media.value.isNotEmpty()) {
            builder.setMessage(R.string.compose_save_draft_loses_media)
        }

        return builder
    }

    /**
     * User is editing a post (scheduled, or posted), and can either go back to editing, or
     * discard the changes.
     */
    private fun getContinueEditingOrDiscardDialog(): AlertDialog.Builder {
        return AlertDialog.Builder(this)
            .setMessage(R.string.unsaved_changes)
            .setPositiveButton(R.string.action_continue_edit) { _, _ ->
                // Do nothing, dialog will dismiss, user can continue editing
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                viewModel.stopUploads()
                finish()
            }
    }

    /**
     * User is editing an existing draft and making it empty.
     * The user can either delete the empty draft or go back to editing.
     */
    private fun getDeleteEmptyDraftOrContinueEditing(): AlertDialog.Builder {
        return AlertDialog.Builder(this)
            .setMessage(R.string.compose_delete_draft)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteDraft()
                viewModel.stopUploads()
                finish()
            }
            .setNegativeButton(R.string.action_continue_edit) { _, _ ->
                // Do nothing, dialog will dismiss, user can continue editing
            }
    }

    private fun deleteDraftAndFinish() {
        viewModel.deleteDraft()
        finish()
    }

    private fun saveDraftAndFinish(contentText: String, contentWarning: String) {
        lifecycleScope.launch {
            val dialog = if (viewModel.shouldShowSaveDraftDialog()) {
                ProgressDialog.show(
                    this@ComposeActivity,
                    null,
                    getString(R.string.saving_draft),
                    true,
                    false,
                )
            } else {
                null
            }
            viewModel.saveDraft(contentText, contentWarning)
            dialog?.cancel()
            finish()
        }
    }

    override suspend fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
    }

    private fun bindEmojiList(emojiList: List<Emoji>) {
        binding.emojiPickerBottomSheet.animate = sharedPreferencesRepository.animateEmojis
        binding.emojiPickerBottomSheet.clickListener = { replaceTextAtCaret("${it.shortcode} ") }
        binding.emojiPickerBottomSheet.emojis = emojiList

        enableButton(binding.composeEmojiButton, emojiList.isNotEmpty(), emojiList.isNotEmpty())
    }

    /**
     * Media queued for upload.
     *
     * @param account
     * @param localId Pachli identified for this media, while it's queued.
     * @param uri Local URI for this media on device.
     * @param type Media's [Type].
     * @param mediaSize Media size in bytes, or [app.pachli.util.MEDIA_SIZE_UNKNOWN]. See [getMediaSize].
     * @param description
     * @param focus
     * @param uploadState
     */
    data class QueuedMedia(
        val account: AccountEntity,
        val localId: Int,
        val uri: Uri,
        val type: Type,
        val mediaSize: Long,
        val description: String? = null,
        val focus: Attachment.Focus? = null,
        val uploadState: Result<UploadState, MediaUploaderError>,
    ) {
        enum class Type {
            IMAGE,
            VIDEO,
            AUDIO,
        }

        /**
         * Server's ID for this attachment. May be null if the media is still
         * being uploaded, or it was uploaded and there was an error that
         * meant it couldn't be processed. Attachments that have an error
         * *after* processing have a non-null `serverId`.
         */
        val serverId: String?
            get() = uploadState.mapBoth(
                { state ->
                    when (state) {
                        is UploadState.Uploading -> null
                        is UploadState.Uploaded.Processing -> state.serverId
                        is UploadState.Uploaded.Processed -> state.serverId
                        is UploadState.Uploaded.Published -> state.serverId
                    }
                },
                { error ->
                    when (error) {
                        is MediaUploaderError.UpdateMediaError -> error.serverId
                        else -> null
                    }
                },
            )
    }

    override fun onTimeSet(time: Date?) {
        viewModel.updateScheduledAt(time)
        if (verifyScheduledTime()) {
            scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            showScheduleView()
        }
    }

    private fun resetSchedule() {
        viewModel.updateScheduledAt(null)
        scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun onUpdateDescription(media: QueuedMedia, description: String) {
        // Do nothing if no changes.
        if (media.description.isNullOrBlank() && description.isBlank()) return
        if (media.description == description) return
        viewModel.updateDescription(media.localId, media.serverId, description)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        private const val KEY_PHOTO_UPLOAD_URI = "app.pachli.KEY_PHOTO_UPLOAD_URI"
        private const val KEY_VISIBILITY = "app.pachli.KEY_VISIBILITY"
        private const val KEY_SCHEDULED_TIME = "app.pachli.KEY_SCHEDULED_TIME"
        private const val KEY_CONTENT_WARNING_VISIBLE = "app.pachli.KEY_CONTENT_WARNING_VISIBLE"

        /**
         * [InputFilter] that uses the "Mastodon" length of a string, where emojis always
         * count as a single character.
         *
         * Unlike [InputFilter.LengthFilter] the source input is not trimmed if it is
         * too long, it's just rejected.
         */
        class MastodonLengthFilter(private val maxLength: Int) : InputFilter {
            override fun filter(
                source: CharSequence?,
                start: Int,
                end: Int,
                dest: Spanned?,
                dstart: Int,
                dend: Int,
            ): CharSequence? {
                val destRemovedLength = dest?.subSequence(dstart, dend).toString().mastodonLength()
                val available = maxLength - dest.toString().mastodonLength() + destRemovedLength
                val sourceLength = source?.subSequence(start, end).toString().mastodonLength()

                // Not enough space to insert the new text
                if (sourceLength > available) return REJECT

                return ALLOW
            }

            companion object {
                /** Filter result allowing the changes */
                val ALLOW = null

                /** Filter result preventing the changes */
                const val REJECT = ""
            }
        }
    }
}
