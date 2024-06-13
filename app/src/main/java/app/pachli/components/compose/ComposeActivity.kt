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
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import androidx.core.content.res.use
import androidx.core.os.BundleCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.adapter.EmojiAdapter
import app.pachli.adapter.LocaleAdapter
import app.pachli.adapter.OnEmojiSelectedListener
import app.pachli.components.compose.ComposeViewModel.ConfirmationKind
import app.pachli.components.compose.dialog.CaptionDialog
import app.pachli.components.compose.dialog.makeFocusDialog
import app.pachli.components.compose.dialog.showAddPollDialog
import app.pachli.components.compose.view.ComposeOptionsListener
import app.pachli.components.compose.view.ComposeScheduleView
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.mastodonLength
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_MAX_MEDIA_ATTACHMENTS
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.InitialCursorPosition
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.databinding.ActivityComposeBinding
import app.pachli.util.PickMediaFiles
import app.pachli.util.getInitialLanguages
import app.pachli.util.getLocaleList
import app.pachli.util.getMediaSize
import app.pachli.util.highlightSpans
import app.pachli.util.iconRes
import app.pachli.core.ui.makeIcon
import app.pachli.util.modernLanguageCode
import app.pachli.util.setDrawableTint
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.options
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Compose a status, either by creating one from scratch, or by editing an existing
 * status, draft, or scheduled status.
 */
@AndroidEntryPoint
class ComposeActivity :
    BaseActivity(),
    ComposeOptionsListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider,
    OnEmojiSelectedListener,

    OnReceiveContentListener,
    ComposeScheduleView.OnTimeSetListener,
    CaptionDialog.Listener {

    private lateinit var composeOptionsBehavior: BottomSheetBehavior<*>
    private lateinit var addMediaBehavior: BottomSheetBehavior<*>
    private lateinit var emojiBehavior: BottomSheetBehavior<*>
    private lateinit var scheduleBehavior: BottomSheetBehavior<*>

    /** The account that is being used to compose the status */
    private lateinit var activeAccount: AccountEntity

    private var photoUploadUri: Uri? = null

    @VisibleForTesting
    var maximumTootCharacters = DEFAULT_CHARACTER_LIMIT

    @VisibleForTesting
    val viewModel: ComposeViewModel by viewModels()

    private val binding by viewBinding(ActivityComposeBinding::inflate)

    private var maxUploadMediaNumber = DEFAULT_MAX_MEDIA_ATTACHMENTS

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pickMedia(photoUploadUri!!)
        }
    }
    private val pickMediaFile = registerForActivityResult(PickMediaFiles()) { uris ->
        if (viewModel.media.value.size + uris.size > maxUploadMediaNumber) {
            Toast.makeText(this, resources.getQuantityString(R.plurals.error_upload_max_media_reached, maxUploadMediaNumber, maxUploadMediaNumber), Toast.LENGTH_SHORT).show()
        } else {
            uris.forEach { uri ->
                pickMedia(uri)
            }
        }
    }

    // Contract kicked off by editImageInQueue; expects viewModel.cropImageItemOld set
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        val uriNew = result.uriContent
        if (result.isSuccessful && uriNew != null) {
            viewModel.cropImageItemOld?.let { itemOld ->
                val size = getMediaSize(contentResolver, uriNew)

                lifecycleScope.launch {
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
            if (composeOptionsBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                addMediaBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                emojiBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                scheduleBehavior.state == BottomSheetBehavior.STATE_EXPANDED
            ) {
                composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            handleCloseButton()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeAccount = accountManager.activeAccount ?: return

        val theme = AppTheme.from(sharedPreferencesRepository)
        if (theme == AppTheme.BLACK) {
            setTheme(DR.style.AppDialogActivityBlackTheme)
        }
        setContentView(binding.root)

        setupActionBar()

        setupAvatar(activeAccount)
        val mediaAdapter = MediaPreviewAdapter(
            this,
            onAddCaption = { item ->
                CaptionDialog.newInstance(item.localId, item.description, item.uri).show(supportFragmentManager, "caption_dialog")
            },
            onAddFocus = { item ->
                makeFocusDialog(item.focus, item.uri) { newFocus ->
                    viewModel.updateFocus(item.localId, newFocus)
                }
                // TODO this is inconsistent to CaptionDialog (device rotation)?
            },
            onEditImage = this::editImageInQueue,
            onRemove = this::removeMediaFromQueue,
        )
        binding.composeMediaPreviewBar.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.composeMediaPreviewBar.adapter = mediaAdapter
        binding.composeMediaPreviewBar.itemAnimator = null

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        val composeOptions: ComposeOptions? = ComposeActivityIntent.getOptions(intent)
        viewModel.setup(composeOptions)

        setupButtons()
        subscribeToUpdates(mediaAdapter)

        if (accountManager.shouldDisplaySelfUsername(this)) {
            binding.composeUsernameView.text = getString(
                R.string.compose_active_account_description,
                activeAccount.fullName,
            )
            binding.composeUsernameView.show()
        } else {
            binding.composeUsernameView.hide()
        }

        setupReplyViews(composeOptions?.replyingStatusAuthor, composeOptions?.replyingStatusContent)
        val statusContent = composeOptions?.content
        if (!statusContent.isNullOrEmpty()) {
            binding.composeEditField.setText(statusContent)
        }

        if (!composeOptions?.scheduledAt.isNullOrEmpty()) {
            binding.composeScheduleView.setDateTime(composeOptions?.scheduledAt)
        }

        setupLanguageSpinner(getInitialLanguages(composeOptions?.language, activeAccount))
        setupComposeField(sharedPreferencesRepository, viewModel.initialContent, composeOptions)
        setupContentWarningField(composeOptions?.contentWarning)
        setupPollView()
        applyShareIntent(intent, savedInstanceState)

        /* Finally, overwrite state with data from saved instance state. */
        savedInstanceState?.let {
            photoUploadUri = BundleCompat.getParcelable(it, PHOTO_UPLOAD_URI_KEY, Uri::class.java)

            (it.getSerializable(VISIBILITY_KEY) as Status.Visibility).apply {
                setStatusVisibility(this)
            }

            it.getBoolean(CONTENT_WARNING_VISIBLE_KEY).apply {
                viewModel.showContentWarningChanged(this)
            }

            it.getString(SCHEDULED_TIME_KEY)?.let { time ->
                viewModel.updateScheduledAt(time)
            }
        }

        binding.composeEditField.post {
            binding.composeEditField.requestFocus()
        }
    }

    private fun applyShareIntent(intent: Intent, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            intent.type?.also { type ->
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
        }
    }

    private fun setupReplyViews(replyingStatusAuthor: String?, replyingStatusContent: String?) {
        if (replyingStatusAuthor != null) {
            binding.composeReplyView.show()
            binding.composeReplyView.text = getString(R.string.replying_to, replyingStatusAuthor)
            val arrowDownIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).apply { sizeDp = 12 }

            setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary)
            binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)

            binding.composeReplyView.setOnClickListener {
                TransitionManager.beginDelayedTransition(binding.composeReplyContentView.parent as ViewGroup)

                if (binding.composeReplyContentView.isVisible) {
                    binding.composeReplyContentView.hide()
                    binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)
                } else {
                    binding.composeReplyContentView.show()
                    val arrowUpIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_up).apply { sizeDp = 12 }

                    setDrawableTint(this, arrowUpIcon, android.R.attr.textColorTertiary)
                    binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowUpIcon, null)
                }
            }
        }
        replyingStatusContent?.let { binding.composeReplyContentView.text = it }
    }

    private fun setupContentWarningField(startingContentWarning: String?) {
        if (startingContentWarning != null) {
            binding.composeContentWarningField.setText(startingContentWarning)
        }
        binding.composeContentWarningField.doOnTextChanged { newContentWarning, _, _, _ ->
            viewModel.onContentWarningChanged(newContentWarning?.toString() ?: "")
        }
    }

    private fun setupComposeField(
        preferences: SharedPreferencesRepository,
        startingText: String?,
        composeOptions: ComposeOptions?,
    ) {
        binding.composeEditField.setOnReceiveContentListener(this)

        binding.composeEditField.setOnKeyListener { _, keyCode, event -> this.onKeyDown(keyCode, event) }

        binding.composeEditField.setAdapter(
            ComposeAutoCompleteAdapter(
                this,
                preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
                preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
                preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            ),
        )
        binding.composeEditField.setTokenizer(ComposeTokenizer())

        binding.composeEditField.setText(startingText)

        when (composeOptions?.initialCursorPosition ?: InitialCursorPosition.END) {
            InitialCursorPosition.START -> binding.composeEditField.setSelection(0)
            InitialCursorPosition.END -> binding.composeEditField.setSelection(
                binding.composeEditField.length(),
            )
        }

        val mentionColour = binding.composeEditField.linkTextColors.defaultColor
        highlightSpans(binding.composeEditField.text, mentionColour)
        binding.composeEditField.doAfterTextChanged { editable ->
            highlightSpans(editable!!, mentionColour)
            viewModel.onContentChanged(editable)
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
            viewModel.closeConfirmation.collect { updateOnBackPressedCallbackState(it, bottomSheetStates()) }
        }

        lifecycleScope.launch {
            viewModel.emojis.collect(::setEmojiList)
        }

        lifecycleScope.launch {
            viewModel.showContentWarning.combine(viewModel.markMediaAsSensitive) { showContentWarning, markSensitive ->
                updateSensitiveMediaToggle(markSensitive, showContentWarning)
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
                updateSensitiveMediaToggle(viewModel.markMediaAsSensitive.value, viewModel.showContentWarning.value)
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

        lifecycleScope.launch {
            viewModel.media.combine(viewModel.poll) { media, poll ->
                val active = poll == null &&
                    media.size < maxUploadMediaNumber &&
                    (media.isEmpty() || media.first().type == QueuedMedia.Type.IMAGE)
                enableButton(binding.composeAddMediaButton, active, active)
                enablePollButton(media.isEmpty())
            }.collect()
        }

        lifecycleScope.launch {
            viewModel.uploadError.collect { throwable ->
                if (throwable is UploadServerError) {
                    displayTransientMessage(throwable.getErrorString(this@ComposeActivity))
                } else {
                    displayTransientMessage(
                        getString(R.string.error_media_upload_sending_fmt, throwable.getErrorString(this@ComposeActivity)),
                    )
                }
            }
        }
    }

    /** @return List of states of the different bottomsheets */
    private fun bottomSheetStates() = listOf(
        composeOptionsBehavior.state,
        addMediaBehavior.state,
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

    private fun setupButtons() {
        binding.composeOptionsBottomSheet.listener = this

        composeOptionsBehavior = BottomSheetBehavior.from(binding.composeOptionsBottomSheet)
        addMediaBehavior = BottomSheetBehavior.from(binding.addMediaBottomSheet)
        scheduleBehavior = BottomSheetBehavior.from(binding.composeScheduleView)
        emojiBehavior = BottomSheetBehavior.from(binding.emojiView)

        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateOnBackPressedCallbackState(viewModel.closeConfirmation.value, bottomSheetStates())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { }
        }
        composeOptionsBehavior.addBottomSheetCallback(bottomSheetCallback)
        addMediaBehavior.addBottomSheetCallback(bottomSheetCallback)
        scheduleBehavior.addBottomSheetCallback(bottomSheetCallback)
        emojiBehavior.addBottomSheetCallback(bottomSheetCallback)

        enableButton(binding.composeEmojiButton, clickable = false, colorActive = false)

        // Setup the interface buttons.
        binding.composeTootButton.setOnClickListener { onSendClicked() }
        binding.composeAddMediaButton.setOnClickListener { openPickDialog() }
        binding.composeToggleVisibilityButton.setOnClickListener { showComposeOptions() }
        binding.composeContentWarningButton.setOnClickListener { onContentWarningChanged() }
        binding.composeEmojiButton.setOnClickListener { showEmojis() }
        binding.composeHideMediaButton.setOnClickListener { toggleHideMedia() }
        binding.composeScheduleButton.setOnClickListener { onScheduleClick() }
        binding.composeScheduleView.setResetOnClickListener { resetSchedule() }
        binding.composeScheduleView.setListener(this)
        binding.atButton.setOnClickListener { atButtonClicked() }
        binding.hashButton.setOnClickListener { hashButtonClicked() }
        binding.descriptionMissingWarningButton.setOnClickListener {
            displayTransientMessage(R.string.hint_media_description_missing)
        }

        val cameraIcon = makeIcon(this, GoogleMaterial.Icon.gmd_camera_alt, IconicsSize.dp(18))
        binding.actionPhotoTake.setCompoundDrawablesRelativeWithIntrinsicBounds(cameraIcon, null, null, null)

        val imageIcon = makeIcon(this, GoogleMaterial.Icon.gmd_image, IconicsSize.dp(18))
        binding.actionPhotoPick.setCompoundDrawablesRelativeWithIntrinsicBounds(imageIcon, null, null, null)

        val pollIcon = makeIcon(this, GoogleMaterial.Icon.gmd_poll, IconicsSize.dp(18))
        binding.addPollTextActionTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(pollIcon, null, null, null)

        binding.actionPhotoTake.visible(Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager) != null)

        binding.actionPhotoTake.setOnClickListener { initiateCameraApp() }
        binding.actionPhotoPick.setOnClickListener { onMediaPick() }
        binding.addPollTextActionTextView.setOnClickListener { openPollDialog() }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setupLanguageSpinner(initialLanguages: List<String>) {
        binding.composePostLanguageButton.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.postLanguage = (parent.adapter.getItem(position) as Locale).modernLanguageCode
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                parent.setSelection(0)
            }
        }
        binding.composePostLanguageButton.apply {
            adapter = LocaleAdapter(context, android.R.layout.simple_spinner_dropdown_item, getLocaleList(initialLanguages))
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

    private fun setupAvatar(activeAccount: AccountEntity) {
        val actionBarSizeAttr = intArrayOf(androidx.appcompat.R.attr.actionBarSize)
        val avatarSize = obtainStyledAttributes(null, actionBarSizeAttr).use { a ->
            a.getDimensionPixelSize(0, 1)
        }

        val animateAvatars = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
        loadAvatar(
            activeAccount.profilePictureUrl,
            binding.composeAvatar,
            avatarSize / 8,
            animateAvatars,
        )
        binding.composeAvatar.contentDescription = getString(
            R.string.compose_active_account_description,
            activeAccount.fullName,
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

    fun prependSelectedWordsWith(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = binding.composeEditField.selectionStart.coerceAtMost(binding.composeEditField.selectionEnd)
        val end = binding.composeEditField.selectionStart.coerceAtLeast(binding.composeEditField.selectionEnd)
        val editorText = binding.composeEditField.text

        if (start == end) {
            // No selection, just insert text at caret
            editorText.insert(start, text)
            // Set the cursor after the inserted text
            binding.composeEditField.setSelection(start + text.length)
        } else {
            var wasWord: Boolean
            var isWord = end < editorText.length && !Character.isWhitespace(editorText[end])
            var newEnd = end

            // Iterate the selection backward so we don't have to juggle indices on insertion
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
                // Special case when the selection includes the start of the text
                editorText.insert(0, text)
                newEnd += text.length
            }

            // Keep the same text (including insertions) selected
            binding.composeEditField.setSelection(start, newEnd)
        }
    }

    private fun atButtonClicked() {
        prependSelectedWordsWith("@")
    }

    private fun hashButtonClicked() {
        prependSelectedWordsWith("#")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(PHOTO_UPLOAD_URI_KEY, photoUploadUri)
        outState.putSerializable(VISIBILITY_KEY, viewModel.statusVisibility.value)
        outState.putBoolean(CONTENT_WARNING_VISIBLE_KEY, viewModel.showContentWarning.value)
        outState.putString(SCHEDULED_TIME_KEY, viewModel.scheduledAt.value)
        super.onSaveInstanceState(outState)
    }

    private fun displayTransientMessage(message: String) {
        val bar = Snackbar.make(binding.activityCompose, message, Snackbar.LENGTH_LONG)
        // necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimension(DR.dimen.compose_activity_snackbar_elevation)
        bar.setAnchorView(R.id.composeBottomBar)
        bar.show()
    }
    private fun displayTransientMessage(@StringRes stringId: Int) {
        displayTransientMessage(getString(stringId))
    }

    private fun toggleHideMedia() {
        this.viewModel.toggleMarkSensitive()
    }

    private fun updateSensitiveMediaToggle(markMediaSensitive: Boolean, contentWarningShown: Boolean) {
        if (viewModel.media.value.isEmpty()) {
            binding.composeHideMediaButton.hide()
            binding.descriptionMissingWarningButton.hide()
        } else {
            binding.composeHideMediaButton.show()
            @ColorInt val color = if (contentWarningShown) {
                binding.composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                binding.composeHideMediaButton.isClickable = false
                getColor(DR.color.transparent_tusky_blue)
            } else {
                binding.composeHideMediaButton.isClickable = true
                if (markMediaSensitive) {
                    binding.composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                    MaterialColors.getColor(binding.composeHideMediaButton, android.R.attr.colorPrimary)
                } else {
                    binding.composeHideMediaButton.setImageResource(R.drawable.ic_eye_24dp)
                    MaterialColors.getColor(binding.composeHideMediaButton, android.R.attr.colorControlNormal)
                }
            }
            binding.composeHideMediaButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

            var oneMediaWithoutDescription = false
            for (media in viewModel.media.value) {
                if (media.description.isNullOrEmpty()) {
                    oneMediaWithoutDescription = true
                    break
                }
            }
            binding.descriptionMissingWarningButton.visibility = if (oneMediaWithoutDescription) View.VISIBLE else View.GONE
        }
    }

    private fun updateScheduleButton() {
        if (viewModel.editing) {
            // Can't reschedule a published status
            enableButton(binding.composeScheduleButton, clickable = false, colorActive = false)
        } else {
            val attr = if (binding.composeScheduleView.time == null) {
                android.R.attr.colorControlNormal
            } else {
                android.R.attr.colorPrimary
            }

            @ColorInt val color = MaterialColors.getColor(binding.composeScheduleButton, attr)
            binding.composeScheduleButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun enableButtons(enable: Boolean, editing: Boolean) {
        binding.composeAddMediaButton.isClickable = enable
        binding.composeToggleVisibilityButton.isClickable = enable && !editing
        binding.composeEmojiButton.isClickable = enable
        binding.composeHideMediaButton.isClickable = enable
        binding.composeScheduleButton.isClickable = enable && !editing
        binding.composeTootButton.isEnabled = enable
    }

    private fun setStatusVisibility(visibility: Status.Visibility) {
        binding.composeOptionsBottomSheet.setStatusVisibility(visibility)
        binding.composeTootButton.setStatusVisibility(binding.composeTootButton, visibility)

        val iconRes = visibility.iconRes() ?: R.drawable.ic_lock_open_24dp
        binding.composeToggleVisibilityButton.setImageResource(iconRes)
        if (viewModel.editing) {
            // Can't update visibility on published status
            enableButton(binding.composeToggleVisibilityButton, clickable = false, colorActive = false)
        }
    }

    private fun showComposeOptions() {
        if (composeOptionsBehavior.state == BottomSheetBehavior.STATE_HIDDEN || composeOptionsBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun onScheduleClick() {
        if (viewModel.scheduledAt.value == null) {
            binding.composeScheduleView.openPickDateDialog()
        } else {
            showScheduleView()
        }
    }

    private fun showScheduleView() {
        if (scheduleBehavior.state == BottomSheetBehavior.STATE_HIDDEN || scheduleBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            scheduleBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun showEmojis() {
        binding.emojiView.adapter?.let {
            if (it.itemCount == 0) {
                val errorMessage = getString(R.string.error_no_custom_emojis, accountManager.activeAccount!!.domain)
                displayTransientMessage(errorMessage)
            } else {
                if (emojiBehavior.state == BottomSheetBehavior.STATE_HIDDEN || emojiBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    emojiBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                } else {
                    emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }

    private fun openPickDialog() {
        if (addMediaBehavior.state == BottomSheetBehavior.STATE_HIDDEN || addMediaBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            addMediaBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            addMediaBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun onMediaPick() {
        addMediaBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // Wait until bottom sheet is not collapsed and show next screen after
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        addMediaBehavior.removeBottomSheetCallback(this)
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
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openPollDialog() = lifecycleScope.launch {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
        val margin = resources.getDimensionPixelSize(DR.dimen.compose_media_preview_margin)
        val marginBottom = resources.getDimensionPixelSize(DR.dimen.compose_media_preview_margin_bottom)

        val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(margin, margin, margin, marginBottom)
        binding.pollPreview.layoutParams = layoutParams

        binding.pollPreview.setOnClickListener {
            val popup = PopupMenu(this, binding.pollPreview)
            val editId = 1
            val removeId = 2
            popup.menu.add(0, editId, 0, R.string.edit_poll)
            popup.menu.add(0, removeId, 0, R.string.action_remove)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    editId -> openPollDialog()
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
        composeOptionsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    @VisibleForTesting
    val selectedLanguage: String?
        get() = viewModel.postLanguage

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
        return binding.composeScheduleView.verifyScheduledTime(binding.composeScheduleView.getDateTime(viewModel.scheduledAt.value))
    }

    private fun onSendClicked() {
        if (verifyScheduledTime()) {
            sendStatus()
        } else {
            showScheduleView()
        }
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

    private fun sendStatus() {
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
                viewModel.sendStatus(contentText, spoilerText, activeAccount.id)
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
                    setAction(app.pachli.core.ui.R.string.action_retry) { onMediaPick() }
                    // necessary so snackbar is shown over everything
                    view.elevation = resources.getDimension(DR.dimen.compose_activity_snackbar_elevation)
                    show()
                }
            }
        }
    }

    private fun initiateCameraApp() {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val photoFile: File = try {
            createNewImageFile(this)
        } catch (ex: IOException) {
            displayTransientMessage(R.string.error_media_upload_opening)
            return
        }

        // Continue only if the File was successfully created
        photoUploadUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            photoFile,
        )?.also {
            takePicture.launch(it)
        }
    }

    private fun enableButton(button: ImageButton, clickable: Boolean, colorActive: Boolean) {
        button.isEnabled = clickable
        setDrawableTint(
            this,
            button.drawable,
            if (colorActive) {
                android.R.attr.textColorTertiary
            } else {
                DR.attr.textColorDisabled
            },
        )
    }

    private fun enablePollButton(enable: Boolean) {
        binding.addPollTextActionTextView.isEnabled = enable
        val textColor = MaterialColors.getColor(
            binding.addPollTextActionTextView,
            if (enable) {
                android.R.attr.textColorTertiary
            } else {
                android.R.attr.colorPrimary
            },
        )
        binding.addPollTextActionTextView.setTextColor(textColor)
        binding.addPollTextActionTextView.compoundDrawablesRelative[0].colorFilter = PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    private fun editImageInQueue(item: QueuedMedia) {
        // If input image is lossless, output image should be lossless.
        // Currently the only supported lossless format is png.
        val mimeType: String? = contentResolver.getType(item.uri)
        val isPng: Boolean = mimeType != null && mimeType.endsWith("/png")
        val tempFile = createNewImageFile(this, if (isPng) ".png" else ".jpg")

        // "Authority" must be the same as the android:authorities string in AndroidManifest.xml
        val uriNew = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", tempFile)

        viewModel.cropImageItemOld = item

        cropImage.launch(
            options(uri = item.uri) {
                setOutputUri(uriNew)
                setOutputCompressFormat(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
            },
        )
    }

    private fun removeMediaFromQueue(item: QueuedMedia) {
        viewModel.removeMediaFromQueue(item)
    }

    private fun pickMedia(uri: Uri, description: String? = null) {
        lifecycleScope.launch {
            viewModel.pickMedia(uri, description).onFailure { throwable ->
                val errorString = when (throwable) {
                    is FileSizeException -> {
                        val decimalFormat = DecimalFormat("0.##")
                        val allowedSizeInMb = throwable.allowedSizeInBytes.toDouble() / (1024 * 1024)
                        val formattedSize = decimalFormat.format(allowedSizeInMb)
                        getString(R.string.error_multimedia_size_limit, formattedSize)
                    }
                    is VideoOrImageException -> getString(R.string.error_media_upload_image_or_video)
                    else -> getString(R.string.error_media_upload_opening)
                }
                displayTransientMessage(errorString)
            }
        }
    }

    private fun showContentWarning(show: Boolean) {
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
                    this.onSendClicked()
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
        when (viewModel.closeConfirmation.value) {
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
        val warning = if (viewModel.media.value.isNotEmpty()) {
            R.string.compose_save_draft_loses_media
        } else {
            R.string.compose_save_draft
        }

        return AlertDialog.Builder(this)
            .setMessage(warning)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_delete) { _, _ ->
                viewModel.stopUploads()
                deleteDraftAndFinish()
            }
    }

    /**
     * User is editing an existing draft, and can either update the draft with the new changes or
     * discard them.
     */
    private fun getUpdateDraftOrDiscardDialog(contentText: String, contentWarning: String): AlertDialog.Builder {
        val warning = if (viewModel.media.value.isNotEmpty()) {
            R.string.compose_save_draft_loses_media
        } else {
            R.string.compose_save_draft
        }

        return AlertDialog.Builder(this)
            .setMessage(warning)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                viewModel.stopUploads()
                finish()
            }
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

    override fun onEmojiSelected(shortcode: String) {
        replaceTextAtCaret(":$shortcode: ")
    }

    private fun setEmojiList(emojiList: List<Emoji>?) {
        if (emojiList != null) {
            val animateEmojis = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
            binding.emojiView.adapter = EmojiAdapter(emojiList, this@ComposeActivity, animateEmojis)
            enableButton(binding.composeEmojiButton, true, emojiList.isNotEmpty())
        }
    }

    data class QueuedMedia(
        val localId: Int,
        val uri: Uri,
        val type: Type,
        val mediaSize: Long,
        val uploadPercent: Int = 0,
        val id: String? = null,
        val description: String? = null,
        val focus: Attachment.Focus? = null,
        val state: State,
    ) {
        enum class Type {
            IMAGE,
            VIDEO,
            AUDIO,
        }
        enum class State {
            UPLOADING,
            UNPROCESSED,
            PROCESSED,
            PUBLISHED,
        }
    }

    override fun onTimeSet(time: String?) {
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

    override fun onUpdateDescription(localId: Int, description: String) {
        viewModel.updateDescription(localId, description)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        internal const val COMPOSE_OPTIONS_EXTRA = "COMPOSE_OPTIONS"
        private const val PHOTO_UPLOAD_URI_KEY = "PHOTO_UPLOAD_URI"
        private const val VISIBILITY_KEY = "VISIBILITY"
        private const val SCHEDULED_TIME_KEY = "SCHEDULE"
        private const val CONTENT_WARNING_VISIBLE_KEY = "CONTENT_WARNING_VISIBLE"

        fun canHandleMimeType(mimeType: String?): Boolean {
            return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType == "text/plain")
        }

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
