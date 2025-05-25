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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.StringRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.compose.ComposeActivity.QueuedMedia
import app.pachli.components.compose.MediaPreviewAdapter.Companion.Payload
import app.pachli.components.compose.MediaPreviewAdapter.Companion.QUEUED_MEDIA_DIFFER
import app.pachli.components.compose.MediaPreviewAdapter.MediaAction
import app.pachli.components.compose.MediaPreviewAdapter.MediaAction.EDIT_FOCUS
import app.pachli.components.compose.MediaPreviewAdapter.MediaAction.EDIT_IMAGE
import app.pachli.components.compose.MediaPreviewAdapter.MediaAction.REMOVE
import app.pachli.components.compose.UploadState.Uploaded
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.network.model.Attachment
import app.pachli.databinding.ItemComposeMediaAttachmentBinding
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Listener for user updates to media descriptions.
 *
 * @param item The item. [item.description][QueuedMedia.description] is the
 * the **previous** description.
 * @param newDescription The new description.
 */
typealias OnDescriptionChangedListener = (item: QueuedMedia, newDescription: String) -> Unit

/**
 * Listener for user requests to edit media focus.
 *
 * Receivers should show a UI allowing the user to edit the focus.
 *
 * @param item The media item to modify.
 */
typealias OnEditFocusListener = (item: QueuedMedia) -> Unit

/**
 * Listener for user requests to edit the media.
 *
 * Receivers should show a UI allowing the user to edit the media.
 *
 * @param item The media item to edit.
 */
typealias OnEditImageListener = (item: QueuedMedia) -> Unit

/**
 * Listener for requests to remove the media from the attachment list.
 *
 * @param item The media item to remove.
 */
typealias OnRemoveMediaListener = (item: QueuedMedia) -> Unit

/**
 * Manages a list of [QueuedMedia] items, displayed using
 * [ItemComposeMediaAttachmentBinding].
 *
 * @param descriptionLimit The maximum number of characters that can be
 * entered as the media description.
 * @param onDescriptionChanged Called whenever the description for an
 * item is changed.
 * @param onEditFocus Called whenever the user wants to edit the focus.
 * @param onEditImage Called whenever the user wants to edit the image.
 * @param onRemoveMedia Callened whenever the user wants to remove the
 * media attachment.
 */
class MediaPreviewAdapter(
    private val glide: RequestManager,
    private val onDescriptionChanged: OnDescriptionChangedListener,
    private val onEditFocus: OnEditFocusListener,
    private val onEditImage: OnEditImageListener,
    private val onRemoveMedia: OnRemoveMediaListener,
) : ListAdapter<QueuedMedia, AttachmentViewHolder>(QUEUED_MEDIA_DIFFER) {
    private var descriptionLimit = 1500

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        return AttachmentViewHolder(
            ItemComposeMediaAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            glide,
            onDescriptionChanged = onDescriptionChanged,
            onMediaClick = ::showMediaPopup,
            getDescriptionLimit = { return@AttachmentViewHolder descriptionLimit },
        )
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int, payloads: List<Any?>) {
        holder.bind(getItem(position), payloads)
    }

    fun setMediaDescriptionLimit(limit: Int) {
        if (limit != descriptionLimit) {
            descriptionLimit = limit
            notifyItemRangeChanged(0, itemCount, Payload.MEDIA_DESCRIPTION_LIMIT)
        }
    }

    /**
     * Actions that can be performed on the attached media.
     *
     * @param resourceId String resource to label the action in menus and Talkback.
     *
     * @see [showMediaPopup]
     * @see [AttachmentViewHolder.bindUri]
     */
    enum class MediaAction(@StringRes val resourceId: Int) {
        EDIT_FOCUS(R.string.action_set_focus),
        EDIT_IMAGE(R.string.action_edit_image),
        REMOVE(R.string.action_remove),

        ;

        companion object {
            /** @return List of valid actions for [item]. */
            fun from(item: QueuedMedia) = buildList {
                if (item.type == QueuedMedia.Type.IMAGE) {
                    add(EDIT_FOCUS)
                    // Already-published items can't be edited
                    if (item.uploadState.get() !is Uploaded.Published) {
                        add(EDIT_IMAGE)
                    }
                }
                add(REMOVE)
            }
        }
    }

    /**
     * Shows a menu allowing the user to perform the actions from
     * [MediaAction.from].
     *
     * Menu clicks are sent to [onEditFocus], [onEditImage], or
     * [onRemoveMedia] as appropriate.
     */
    private fun showMediaPopup(item: QueuedMedia, view: View) {
        val popup = PopupMenu(view.context, view)

        MediaAction.from(item).forEach {
            popup.menu.add(0, it.ordinal, 0, it.resourceId)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                EDIT_FOCUS.ordinal -> onEditFocus(item)
                EDIT_IMAGE.ordinal -> onEditImage(item)
                REMOVE.ordinal -> onRemoveMedia(item)
            }
            true
        }

        popup.show()
    }

    companion object {
        /** Payload from [QUEUED_MEDIA_DIFFER.getChangePayload]. */
        enum class Payload {
            /** [QueuedMedia.uri] changed */
            URI,

            /** [QueuedMedia.description] changed */
            DESCRIPTION,

            /** [QueuedMedia.focus] changed */
            FOCUS,

            /** [QueuedMedia.uploadState] changed */
            UPLOAD_STATE,

            /** Media description limit changed. */
            MEDIA_DESCRIPTION_LIMIT,
        }

        private val QUEUED_MEDIA_DIFFER = object : DiffUtil.ItemCallback<QueuedMedia>() {
            override fun areItemsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia) = oldItem.localId == newItem.localId
            override fun areContentsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia) = oldItem == newItem

            override fun getChangePayload(oldItem: QueuedMedia, newItem: QueuedMedia): Any? {
                if (oldItem.uri != newItem.uri) return Payload.URI
                if (oldItem.uploadState != newItem.uploadState) return Payload.UPLOAD_STATE
                if (oldItem.focus != newItem.focus) return Payload.FOCUS

                // Don't rebind the caption view if the caption hasn't
                // changed. This prevents an infinite loop:
                // 1. Bind description to caption here
                // 2. .afterTextChanged calls onEditCaption
                // 3. onEditCaption eventually updates the displayed list
                //    with the new caption.
                // 4. A new list means bind() is called, and we end up back
                //    here.
                // 5. Setting the *same* text goes back to step 1, hence loop.
                val oldDescription = oldItem.description?.toString()?.ifBlank { "" } ?: ""
                val newDescription = newItem.description?.toString()?.ifBlank { "" } ?: ""
                if (oldDescription != newDescription) return Payload.DESCRIPTION
                return super.getChangePayload(oldItem, newItem)
            }
        }
    }
}

/**
 * Displays media attachments using [ItemComposeMediaAttachmentBinding].
 *
 * @param binding View binding for the UI.
 * @param onDescriptionChanged Called when the description is changed.
 * @param onMediaClick Called when the user clicks the media preview image.
 * @param getDescriptionLimit Called to fetch the current media description limit.
 */
class AttachmentViewHolder(
    val binding: ItemComposeMediaAttachmentBinding,
    private val glide: RequestManager,
    private val onDescriptionChanged: OnDescriptionChangedListener,
    private val onMediaClick: (QueuedMedia, View) -> Unit,
    private val getDescriptionLimit: () -> Int,
) : RecyclerView.ViewHolder(binding.root) {
    private val context = binding.root.context

    private lateinit var item: QueuedMedia

    init {
        binding.descriptionLayout.counterMaxLength = getDescriptionLimit()
        binding.description.doAfterTextChanged { it?.let { onDescriptionChanged(item, it.toString()) } }

        binding.media.setOnClickListener { onMediaClick(item, binding.media) }

        val shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, context.resources.getDimension(app.pachli.core.designsystem.R.dimen.card_radius))
            .build()
        val materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
        binding.errorMsg.background = materialShapeDrawable
    }

    fun bind(item: QueuedMedia, payloads: List<Any?>? = null) {
        this.item = item

        if (payloads == null || payloads.isEmpty()) {
            bindAll(item)
            return
        }

        payloads.forEach { payload ->
            when (payload) {
                Payload.URI -> bindUri(item)
                Payload.DESCRIPTION -> bindDescription(item.description)
                Payload.FOCUS -> bindFocus(item.focus)
                Payload.UPLOAD_STATE -> bindUploadState(item.uploadState)
                Payload.MEDIA_DESCRIPTION_LIMIT -> binding.descriptionLayout.counterMaxLength = getDescriptionLimit()
                else -> bindAll(item)
            }
        }
    }

    /** Binds all [item] properties to the UI. */
    private fun bindAll(item: QueuedMedia) {
        bindUri(item)
        bindDescription(item.description)
        bindFocus(item.focus)
        bindUploadState(item.uploadState)
    }

    /**
     * Binds [item.uri][QueuedMedia.uri] and related properties to the UI.
     *
     * - Sets the image for the media.
     * - Sets the image's focus (if provided).
     * - Sets an appropriate Talkback action from the media actions.
     */
    private fun bindUri(item: QueuedMedia) = with(binding.media) {
        if (item.type == QueuedMedia.Type.AUDIO) {
            // TODO: Fancy waveform display?
            glide.clear(this)
            setImageResource(R.drawable.ic_music_box_preview_24dp)
            return@with
        }

        var request = glide.load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .dontAnimate()
            .centerInside()

        if (item.focus != null) request = request.addListener(this)

        request.into(this)

        // Default Talkback behaviour reads out that this is a button with
        // options available. Enhance the experience by listing the options.
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)

                    val options = MediaAction.from(item).map {
                        context.getString(it.resourceId)
                    }

                    info.addAction(
                        AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            options.joinToString(", "),
                        ),
                    )
                }
            },
        )
    }

    /**
     * Binds [description] to the UI.
     *
     * A null/blank description shows the warning message.
     */
    private fun bindDescription(description: String?) = with(binding.description) {
        binding.descriptionLayout.error = if (description.isNullOrBlank()) {
            context.getString(R.string.hint_media_description_missing)
        } else {
            null
        }

        // Only update the description in the UI if it has changed outside the
        // UI. Do nothing if the new description is the same as the text currently
        // in the view.
        //
        // Otherwise this will loop; setText -> .doAfterTextChanged -> save
        // description in viewmodel -> adapter list updates -> bindDescription.
        val prevDescription = text?.toString()?.ifBlank { "" } ?: ""
        val newDescription = description?.toString()?.ifBlank { "" } ?: ""
        if (newDescription != prevDescription) setText(description)
    }

    /** Binds [focus] to the UI. */
    private fun bindFocus(focus: Attachment.Focus?) = with(binding.media) {
        focus?.let { setFocalPoint(it) } ?: removeFocalPoint()
    }

    /**
     * Binds [uploadState] to the UI.
     *
     * - Sets the state on the preview image.
     * - Shows/hides the upload error message.
     */
    private fun bindUploadState(uploadState: Result<UploadState, MediaUploaderError>) {
        binding.media.setResult(uploadState)
        with(binding.errorMsg) {
            uploadState
                .onSuccess { hide() }
                .onFailure {
                    text = context.getString(R.string.upload_failed_msg_fmt, it.fmt(context))
                    show()
                }
        }
    }
}
