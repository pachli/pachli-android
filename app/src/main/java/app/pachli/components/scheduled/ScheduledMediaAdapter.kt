/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.components.scheduled

import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.designsystem.R
import app.pachli.core.model.Attachment
import app.pachli.core.ui.MediaPreviewImageView
import com.bumptech.glide.RequestManager

internal class ScheduledMediaAdapter(
    private val glide: RequestManager,
) : ListAdapter<Attachment, ScheduledMediaViewHolder>(
    object : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem == newItem
        }
    },
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduledMediaViewHolder {
        return ScheduledMediaViewHolder(glide, MediaPreviewImageView(parent.context))
    }

    override fun onBindViewHolder(holder: ScheduledMediaViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }
}

internal class ScheduledMediaViewHolder(
    private val glide: RequestManager,
    private val imageView: MediaPreviewImageView,
) : RecyclerView.ViewHolder(imageView) {
    init {
        val context = imageView.context
        val thumbnailViewSize = context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)
        val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
        val margin = context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
        val marginBottom = context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)
        layoutParams.setMargins(margin, 0, margin, marginBottom)
        imageView.layoutParams = layoutParams
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    fun bind(attachment: Attachment) {
        if (attachment.type == Attachment.Type.AUDIO) {
            imageView.clearFocus()
            imageView.setImageResource(app.pachli.core.ui.R.drawable.ic_music_box_preview_24dp)
            return
        }

        val request = glide.load(attachment.url)
            .dontAnimate()
            .centerInside()

        attachment.meta?.focus?.let {
            imageView.setFocalPoint(it)
            request.addListener(imageView)
        } ?: imageView.clearFocus()

        request.into(imageView)
    }
}
