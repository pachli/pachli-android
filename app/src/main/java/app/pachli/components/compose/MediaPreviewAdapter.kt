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

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.compose.ComposeActivity.QueuedMedia
import app.pachli.components.compose.UploadState.Uploaded
import app.pachli.components.compose.view.ProgressImageView
import app.pachli.core.designsystem.R as DR
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class MediaPreviewAdapter(
    context: Context,
    private val onAddCaption: (QueuedMedia) -> Unit,
    private val onAddFocus: (QueuedMedia) -> Unit,
    private val onEditImage: (QueuedMedia) -> Unit,
    private val onRemove: (QueuedMedia) -> Unit,
) : RecyclerView.Adapter<MediaPreviewAdapter.PreviewViewHolder>() {

    fun submitList(list: List<QueuedMedia>) {
        this.differ.submitList(list)
    }

    private fun onMediaClick(position: Int, view: View) {
        val item = differ.currentList[position]

        // Handle error
        item.uploadState
            .onSuccess { showMediaPopup(item, view) }
            .onFailure { showMediaError(item, it, view) }
    }

    private fun showMediaPopup(item: QueuedMedia, view: View) {
        val popup = PopupMenu(view.context, view)
        val addCaptionId = 1
        val addFocusId = 2
        val editImageId = 3
        val removeId = 4

        popup.menu.add(0, addCaptionId, 0, R.string.action_set_caption)
        if (item.type == QueuedMedia.Type.IMAGE) {
            popup.menu.add(0, addFocusId, 0, R.string.action_set_focus)
            if (item.uploadState.get() !is Uploaded.Published) {
                // Already-published items can't be edited
                popup.menu.add(0, editImageId, 0, R.string.action_edit_image)
            }
        }
        popup.menu.add(0, removeId, 0, R.string.action_remove)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                addCaptionId -> onAddCaption(item)
                addFocusId -> onAddFocus(item)
                editImageId -> onEditImage(item)
                removeId -> onRemove(item)
            }
            true
        }
        popup.show()
    }

    private fun showMediaError(item: QueuedMedia, error: MediaUploaderError, view: View) {
        AlertDialog.Builder(view.context)
            .setTitle(R.string.action_post_failed)
            .setMessage(view.context.getString(R.string.upload_failed_msg_fmt, error.fmt(view.context)))
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .setNegativeButton(R.string.upload_failed_modify_attachment) { _, _ -> showMediaPopup(item, view) }
            .show()
    }

    private val thumbnailViewSize =
        context.resources.getDimensionPixelSize(DR.dimen.compose_media_preview_size)

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        return PreviewViewHolder(ProgressImageView(parent.context))
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val item = differ.currentList[position]
        holder.progressImageView.setChecked(!item.description.isNullOrEmpty())

        holder.progressImageView.setResult(item.uploadState)

        if (item.type == QueuedMedia.Type.AUDIO) {
            // TODO: Fancy waveform display?
            holder.progressImageView.setImageResource(R.drawable.ic_music_box_preview_24dp)
        } else {
            val imageView = holder.progressImageView
            val focus = item.focus

            if (focus != null) {
                imageView.setFocalPoint(focus)
            } else {
                imageView.removeFocalPoint() // Probably unnecessary since we have no UI for removal once added.
            }

            var glide = Glide.with(holder.itemView.context)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .dontAnimate()
                .centerInside()

            if (focus != null) {
                glide = glide.addListener(imageView)
            }

            glide.into(imageView)
        }
    }

    private val differ = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<QueuedMedia>() {
            override fun areItemsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia): Boolean {
                return oldItem.localId == newItem.localId
            }

            override fun areContentsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia): Boolean {
                return oldItem == newItem
            }
        },
    )

    inner class PreviewViewHolder(val progressImageView: ProgressImageView) :
        RecyclerView.ViewHolder(progressImageView) {
        init {
            val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
            val margin = itemView.context.resources
                .getDimensionPixelSize(DR.dimen.compose_media_preview_margin)
            val marginBottom = itemView.context.resources
                .getDimensionPixelSize(DR.dimen.compose_media_preview_margin_bottom)
            layoutParams.setMargins(margin, 0, margin, marginBottom)
            progressImageView.layoutParams = layoutParams
            progressImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            progressImageView.setOnClickListener {
                onMediaClick(bindingAdapterPosition, progressImageView)
            }
        }
    }
}
