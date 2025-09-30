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
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.ui.extensions.aspectRatios
import com.bumptech.glide.RequestManager
import kotlin.math.roundToInt

/** Maximum number of attachments to show in the grid. */
private const val MAX_ATTACHMENTS = 4

/**
 * Function to call when the user clicks on an [Attachment] in the grid.
 *
 * @see [invoke]
 */
fun interface AttachmentClickListener {
    /**
     * Function to call when the user clicks on an [Attachment] in the grid.
     *
     * @param view View the user clicked on.
     * @param index Index of the the attachment the user clicked on.
     */
    operator fun invoke(view: View, index: Int)
}

/**
 * Function to call when the user long-clicks on an [Attachment] in the
 * grid.
 *
 * @see [invoke]
 */
fun interface AttachmentLongClickListener {
    /**
     * Function to call when the user long-clicks on an [Attachment] in the
     * grid.
     *
     * @param view View the user clicked on.
     * @parma index Index of the attachment the user clicked on.
     */
    operator fun invoke(view: View, index: Int)
}

/**
 * Lays out a grid of [Attachment] into [MediaPreviewImageView]s taking their
 * aspect ratios into account.
 *
 * Images are laid out in a 2x2 grid showing up to [MAX_ATTACHMENTS] previews.
 * The number and aspect ratios of the attachments determines the layout.
 *
 * - One attachment takes up the whole grid.
 * - Two attachments are shown side-by-side or one above the other depending on the
 * average of their aspect ratios. Each attachment takes half the grid.
 * - Three attachments are shown with the first attachment taking half the grid, the
 * other two take a quarter of the grid each.
 * - Four attachments take a quarter of the grid each.
 *
 * The grid does not scroll, and the attachments are scaled so the grid is
 * rectangular ensuring any marked [focal point][Attachment.MetaData.focus] in each
 * attachment is visible.
 */
class AttachmentGridView(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val spacing = context.resources.getDimensionPixelOffset(app.pachli.core.designsystem.R.dimen.preview_image_spacing)

    /**
     * An ordered list of aspect ratios used for layout. An image view for each aspect
     * ratio passed will be attached. Supports up to [MAX_ATTACHMENTS], additional
     * ones will be ignored.
     */
    private var aspectRatios: List<Double> = emptyList()

    private var measuredOrientation = LinearLayout.VERTICAL

    /**
     * Displays [attachments] in the grid, loading using [glide]. Each attachment is
     * shown according to [displayAction] and [useBlurHash]. Each click or long-click
     * on an attachment is forwarded to [attachmentClickListener] or
     * [attachmentLongClickListener].
     *
     * @param glide
     * @param attachments List of attachments to show. No matter how many attachments
     * are provided, only [MAX_ATTACHMENTS] are shown. It is the caller's
     * responsibility to ensure all attachments are
     * [previewable][Attachment.isPreviewable].
     * @param displayAction See [AttachmentPreviewView.bind].
     * @param useBlurHash See [AttachmentPreviewView.bind].
     * @param attachmentClickListener Called when the user clicks an attachment in
     * the grid.
     * @param attachmentLongClickListener Called when the user long-clicks an
     * attachment in the grid.
     */
    fun bind(
        glide: RequestManager,
        attachments: List<Attachment>,
        displayAction: AttachmentDisplayAction,
        useBlurHash: Boolean,
        attachmentClickListener: AttachmentClickListener? = null,
        attachmentLongClickListener: AttachmentLongClickListener? = null,
    ) {
        val attachments = attachments.take(MAX_ATTACHMENTS)
        aspectRatios = attachments.aspectRatios()
        removeAllViews()
        attachments.forEachIndexed { index, attachment ->
            val attachmentPreviewView = AttachmentPreviewView(context)
            addView(attachmentPreviewView)
            attachmentPreviewView.bind(glide, attachments[index], displayAction, useBlurHash)
            attachmentClickListener?.let {
                attachmentPreviewView.setOnClickListener {
                    attachmentClickListener(it, index)
                }
            }
            attachmentLongClickListener?.let {
                attachmentPreviewView.setOnLongClickListener {
                    attachmentLongClickListener(it, index)
                    true
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val halfWidth = width / 2 - spacing / 2
        var totalHeight = 0

        when (childCount) {
            1 -> {
                // Image takes the whole 2 x 2 grid.
                val aspect = aspectRatios[0]
                totalHeight += getChildAt(0).measureToAspect(width, aspect)
            }

            2 -> {
                // Show two images either side-by-side or one above the other.
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]

                // Slightly biased towards showing them side-by-side, even if one or both of
                // the images are landscape (aspect > 1 == landscape).
                if ((aspect1 + aspect2) / 2 <= 1.2) {
                    // Stack horizontally
                    measuredOrientation = LinearLayout.HORIZONTAL
                    val height = rowHeight(halfWidth, aspect1, aspect2)
                    totalHeight += height
                    getChildAt(0).measureExactly(halfWidth, height)
                    getChildAt(1).measureExactly(halfWidth, height)
                } else {
                    // Stack vertically
                    measuredOrientation = LinearLayout.VERTICAL
                    totalHeight += getChildAt(0).measureToAspect(width, aspect1.coerceAtLeast(1.8))
                    totalHeight += spacing
                    totalHeight += getChildAt(1).measureToAspect(width, aspect2.coerceAtLeast(1.8))
                }
            }

            3 -> {
                // Show three images, with the first image taking half the grid, and the
                // other two images taking a quarter of the grid each.
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]
                val aspect3 = aspectRatios[2]

                if (aspect1 >= 1) {
                    // First image is landscape/square, takes the top half of the grid.
                    //
                    // |     1     |
                    // -------------
                    // |  2  |  3  |
                    measuredOrientation = LinearLayout.VERTICAL
                    totalHeight += getChildAt(0).measureToAspect(width, aspect1.coerceAtLeast(1.8))
                    totalHeight += spacing
                    val bottomHeight = rowHeight(halfWidth, aspect2, aspect3)
                    totalHeight += bottomHeight
                    getChildAt(1).measureExactly(halfWidth, bottomHeight)
                    getChildAt(2).measureExactly(halfWidth, bottomHeight)
                } else {
                    // First image is portrait, takes the left half of the grid.
                    //
                    // |     |  2  |
                    // |  1  |-----|
                    // |     |  3  |
                    measuredOrientation = LinearLayout.HORIZONTAL
                    val colHeight = getChildAt(0).measureToAspect(halfWidth, aspect1)
                    totalHeight += colHeight
                    val halfHeight = colHeight / 2 - spacing / 2
                    getChildAt(1).measureExactly(halfWidth, halfHeight)
                    getChildAt(2).measureExactly(halfWidth, halfHeight)
                }
            }

            4 -> {
                // Show four images, one per grid square.
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]
                val aspect3 = aspectRatios[2]
                val aspect4 = aspectRatios[3]
                val topHeight = rowHeight(halfWidth, aspect1, aspect2)
                totalHeight += topHeight
                getChildAt(0).measureExactly(halfWidth, topHeight)
                getChildAt(1).measureExactly(halfWidth, topHeight)
                totalHeight += spacing
                val bottomHeight = rowHeight(halfWidth, aspect3, aspect4)
                totalHeight += bottomHeight
                getChildAt(2).measureExactly(halfWidth, bottomHeight)
                getChildAt(3).measureExactly(halfWidth, bottomHeight)
            }
        }

        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        val halfWidth = width / 2 - spacing / 2
        when (childCount) {
            1 -> {
                getChildAt(0).layout(0, 0, width, height)
            }

            2 -> {
                if (measuredOrientation == LinearLayout.VERTICAL) {
                    val child0 = getChildAt(0)
                    val y = child0.measuredHeight
                    child0.layout(0, 0, width, y)
                    getChildAt(1).layout(
                        0,
                        y + spacing,
                        width,
                        y + spacing + getChildAt(1).measuredHeight,
                    )
                } else {
                    getChildAt(0).layout(0, 0, halfWidth, height)
                    getChildAt(1).layout(halfWidth + spacing, 0, width, height)
                }
            }

            3 -> {
                if (measuredOrientation == LinearLayout.VERTICAL) {
                    val child0 = getChildAt(0)
                    val y = child0.measuredHeight
                    child0.layout(0, 0, width, y)
                    getChildAt(1).layout(0, y + spacing, halfWidth, height)
                    getChildAt(2).layout(halfWidth + spacing, y + spacing, width, height)
                } else {
                    val colHeight = getChildAt(0).measuredHeight
                    getChildAt(0).layout(0, 0, halfWidth, colHeight)
                    val halfHeight = colHeight / 2 - spacing / 2
                    getChildAt(1).layout(halfWidth + spacing, 0, width, halfHeight)
                    getChildAt(2).layout(
                        halfWidth + spacing,
                        halfHeight + spacing,
                        width,
                        colHeight,
                    )
                }
            }

            4 -> {
                val topHeight = (getChildAt(0).measuredHeight + getChildAt(1).measuredHeight) / 2
                getChildAt(0).layout(0, 0, halfWidth, topHeight)
                getChildAt(1).layout(halfWidth + spacing, 0, width, topHeight)
                val child2 = getChildAt(2)
                val child3 = getChildAt(3)
                val bottomHeight = (child2.measuredHeight + child3.measuredHeight) / 2
                child2.layout(
                    0,
                    topHeight + spacing,
                    halfWidth,
                    topHeight + spacing + bottomHeight,
                )
                child3.layout(
                    halfWidth + spacing,
                    topHeight + spacing,
                    width,
                    topHeight + spacing + bottomHeight,
                )
            }
        }
    }
}

private fun rowHeight(halfWidth: Int, aspect1: Double, aspect2: Double): Int {
    return ((halfWidth / aspect1 + halfWidth / aspect2) / 2).roundToInt()
}

private fun View.measureToAspect(width: Int, aspect: Double): Int {
    val height = (width / aspect).roundToInt()
    measureExactly(width, height)
    return height
}

private fun View.measureExactly(width: Int, height: Int) {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
}
