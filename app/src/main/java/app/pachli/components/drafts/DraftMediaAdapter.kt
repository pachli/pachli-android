/* Copyright 2020 Tusky Contributors
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

package app.pachli.components.drafts

import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.DraftAttachment
import app.pachli.view.MediaPreviewImageView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy

class DraftMediaAdapter(
    private val glide: RequestManager,
    private val attachmentClick: () -> Unit,
) : ListAdapter<DraftAttachment, DraftMediaAdapter.DraftMediaViewHolder>(
    object : DiffUtil.ItemCallback<DraftAttachment>() {
        override fun areItemsTheSame(oldItem: DraftAttachment, newItem: DraftAttachment): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: DraftAttachment, newItem: DraftAttachment): Boolean {
            return oldItem == newItem
        }
    },
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftMediaViewHolder {
        return DraftMediaViewHolder(MediaPreviewImageView(parent.context))
    }

    override fun onBindViewHolder(holder: DraftMediaViewHolder, position: Int) {
        getItem(position)?.let { attachment ->
            if (attachment.type == DraftAttachment.Type.AUDIO) {
                holder.imageView.clearFocus()
                holder.imageView.setImageResource(R.drawable.ic_music_box_preview_24dp)
            } else {
                if (attachment.focus != null) {
                    holder.imageView.setFocalPoint(attachment.focus)
                } else {
                    holder.imageView.clearFocus()
                }
                var request = glide.load(attachment.uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .dontAnimate()
                    .centerInside()

                if (attachment.focus != null) {
                    request = request.addListener(holder.imageView)
                }

                request.into(holder.imageView)
            }
        }
    }

    inner class DraftMediaViewHolder(val imageView: MediaPreviewImageView) :
        RecyclerView.ViewHolder(imageView) {
        init {
            val thumbnailViewSize =
                imageView.context.resources.getDimensionPixelSize(DR.dimen.compose_media_preview_size)
            val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
            val margin = itemView.context.resources
                .getDimensionPixelSize(DR.dimen.compose_media_preview_margin)
            val marginBottom = itemView.context.resources
                .getDimensionPixelSize(DR.dimen.compose_media_preview_margin_bottom)
            layoutParams.setMargins(margin, 0, margin, marginBottom)
            imageView.layoutParams = layoutParams
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setOnClickListener {
                attachmentClick()
            }
        }
    }
}
