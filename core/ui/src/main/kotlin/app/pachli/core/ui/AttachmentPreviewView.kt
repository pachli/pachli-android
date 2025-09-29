/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.widget.ImageView.ScaleType
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import androidx.core.graphics.drawable.toDrawable
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.ui.databinding.AttachmentPreviewBinding
import app.pachli.core.ui.extensions.getContentDescription
import app.pachli.core.ui.extensions.getFormattedDescription
import app.pachli.core.ui.extensions.isPlayable
import com.bumptech.glide.RequestManager
import com.google.android.material.color.MaterialColors

/**
 * Displays a preview of a single [Attachment].
 *
 * Attachment is loaded into the view using [bind].
 *
 * If the attachment is playable [R.drawable.play_indicator_overlay] is displayed over
 * the preview.
 *
 * A badge is shown at the bottom right of the preview if the attachment has a
 * [description][Attachment.description].
 *
 * The content description is automatically set (see [Attachment.getContentDescription]).
 */
class AttachmentPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding = AttachmentPreviewBinding.inflate(LayoutInflater.from(context), this)

    /** Colour to use in placeholder drawables. */
    private val mediaPreviewUnloadedColor = MaterialColors.getColor(this, android.R.attr.textColorLink)

    /** Drawable to use for the media if [bind.useBlurHash][bind] is false. */
    private val mediaPreviewUnloadedDrawable = mediaPreviewUnloadedColor.toDrawable()

    init {
        // Forward any ImageView-appropriate attribute values down to the ImageView
        // that shows the preview. See values/attrs.xml.
        context.obtainStyledAttributes(attrs, R.styleable.AttachmentPreviewView, defStyleAttr, defStyleRes).use {
            with(binding.previewImageView) {
                it.getDrawable(R.styleable.AttachmentPreviewView_android_src)?.let { drawable ->
                    setImageDrawable(drawable)
                }

                baselineAlignBottom = it.getBoolean(R.styleable.AttachmentPreviewView_android_baselineAlignBottom, false)
                baseline = it.getDimensionPixelSize(R.styleable.AttachmentPreviewView_android_baseline, -1)
                adjustViewBounds = it.getBoolean(R.styleable.AttachmentPreviewView_android_adjustViewBounds, false)
                maxWidth = it.getDimensionPixelSize(R.styleable.AttachmentPreviewView_android_maxWidth, Integer.MAX_VALUE)
                maxHeight = it.getDimensionPixelSize(R.styleable.AttachmentPreviewView_android_maxHeight, Integer.MAX_VALUE)

                val scaleInt = it.getInt(R.styleable.AttachmentPreviewView_android_scaleType, -1)
                if (scaleInt != -1) scaleType = ScaleType.entries[scaleInt]

                it.getColorStateList(R.styleable.AttachmentPreviewView_android_tint)?.let { colorStateList ->
                    imageTintList = colorStateList
                    imageTintMode = PorterDuff.Mode.SRC_ATOP
                }

                when (it.getString(R.styleable.AttachmentPreviewView_android_tintMode)) {
                    "add" -> PorterDuff.Mode.ADD
                    "multiply" -> PorterDuff.Mode.MULTIPLY
                    "screen" -> PorterDuff.Mode.SCREEN
                    "src_atop" -> PorterDuff.Mode.SRC_ATOP
                    "src_in" -> PorterDuff.Mode.SRC_IN
                    "src_over" -> PorterDuff.Mode.SRC_OVER
                    else -> null
                }?.let { imageTintMode = it }

                cropToPadding = it.getBoolean(R.styleable.AttachmentPreviewView_android_cropToPadding, false)
            }
        }
    }

    /**
     * Displays the attachment.
     *
     * The attachment may be hidden, or blurred, depending on [displayAction] and
     * [useBlurHash].
     *
     * @param glide
     * @param attachment Attachment to display.
     * @param displayAction If [Show][AttachmentDisplayAction.Show] the media preview
     * is displayed. If [Hide][AttachmentDisplayAction.Hide] either [mediaPreviewUnloadedDrawable]
     * or the blur hash is shown, depending on [useBlurHash].
     * @param useBlurHash If true the blur hash is used in two cases; if [displayAction] is
     * [Show][AttachmentDisplayAction.Show] the blur hash is used while the image is loading.
     * And if [displayAction] is [Hide][AttachmentDisplayAction.Hide] the blur hash is used
     * instead of the image. In both cases if [useBlurHash] is false [mediaPreviewUnloadedDrawable]
     * is used.
     * @param size Optional pixel size for the attachment view.
     */
    fun bind(
        glide: RequestManager,
        attachment: Attachment,
        displayAction: AttachmentDisplayAction,
        useBlurHash: Boolean,
        size: Size? = null,
    ) {
        val url = attachment.previewUrl
        val description = attachment.description
        val hasDescription = description?.isNotBlank() == true

        val blurHash = if (useBlurHash) attachment.blurhash else null
        val placeholder = placeholder(blurHash, size)

        with(binding.previewImageView) {
            when {
                url == null -> {
                    loadDrawableInto(glide, this, placeholder, size)
                    contentDescription = attachment.getContentDescription(context)
                }

                displayAction is AttachmentDisplayAction.Hide -> {
                    loadDrawableInto(glide, this, placeholder, size)
                    contentDescription = displayAction.reason.getFormattedDescription(context)
                }

                else -> {
                    loadUrlInto(glide, this, url, attachment.meta?.focus, placeholder)
                    contentDescription = attachment.getContentDescription(context)
                }
            }

            foreground = if (attachment.type.isPlayable() && displayAction is AttachmentDisplayAction.Show) {
                AppCompatResources.getDrawable(context, R.drawable.play_indicator_overlay)
            } else {
                null
            }
        }

        binding.previewMediaDescriptionIndicator.visible(hasDescription)
    }

    /**
     * Loads a placeholder image in to [mediaPreviewImageView], clearing the focal point.
     *
     * @param glide
     * @param mediaPreviewImageView
     * @param placeholder [Drawable] to load.
     * @param size (optional) Pixel dimensions to use for [placeholder].
     */
    private fun loadDrawableInto(
        glide: RequestManager,
        mediaPreviewImageView: MediaPreviewImageView,
        placeholder: Drawable,
        size: Size? = null,
    ) {
        mediaPreviewImageView.removeFocalPoint()
        glide.load(placeholder).apply {
            (size?.let { override(size.width, size.height) } ?: this)
                .centerInside()
                .into(mediaPreviewImageView)
        }
        return
    }

    /**
     * Loads the image at [url] into [mediaPreviewImageView] using [glide].
     *
     * @param glide
     * @param mediaPreviewImageView
     * @param url URL of the image to load.
     * @param focus Focus to use. May be null if there is no focus.
     * @param placeholder The drawable to use as the placeholder while [url] is
     * loading.
     */
    private fun loadUrlInto(
        glide: RequestManager,
        mediaPreviewImageView: MediaPreviewImageView,
        url: String,
        focus: Attachment.Focus?,
        placeholder: Drawable,
    ) {
        if (focus != null) {
            mediaPreviewImageView.setFocalPoint(focus)
            glide.load(url)
                .placeholder(placeholder)
                .centerInside()
                .addListener(mediaPreviewImageView)
                .into(mediaPreviewImageView)
        } else {
            mediaPreviewImageView.removeFocalPoint()
            glide.load(url)
                .placeholder(placeholder)
                .centerInside()
                .into(mediaPreviewImageView)
        }
    }

    /**
     * @return A [Drawable] for use as a placeholder, sized to [size].
     *
     * @param blurHash Optional blurHash to use. If not provided [mediaPreviewUnloadedDrawable]
     * is used as the placeholder.
     * @param size Optional size to use.
     */
    fun placeholder(blurHash: String?, size: Size?): Drawable {
        size ?: return blurHash?.let { decodeBlurHash(context, it) } ?: mediaPreviewUnloadedDrawable

        val aspectRatio = if (size.height == 0 || size.width == 0) 1.7778 else size.width.toDouble() / size.height
        val height = 32
        val width = (height * aspectRatio).toInt()

        return blurHash?.let { decodeBlurHash(context, it, width, height) }
            ?: PaintDrawable(mediaPreviewUnloadedColor).apply {
                intrinsicWidth = width
                intrinsicHeight = height
            }
    }
}
