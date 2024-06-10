/*
 * Copyright 2023 Pachli Association
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

package app.pachli.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import app.pachli.R
import app.pachli.core.activity.decodeBlurHash
import app.pachli.core.common.extensions.hide
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.PreviewCard
import app.pachli.databinding.PreviewCardBinding
import com.bumptech.glide.Glide
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Compound view that displays [PreviewCard].
 *
 * Classes hosting this should provide a [PreviewCardView.OnClickListener] to be notified when the
 * the user clicks on the card.
 */
class PreviewCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    /** Where on the card the user clicked */
    enum class Target {
        /** Any part of the card that's not the image */
        CARD,

        /** The image **/
        IMAGE,
    }

    fun interface OnClickListener {
        /** @param target Where on the card the user clicked */
        fun onClick(target: Target)
    }

    private val binding: PreviewCardBinding
    private val radius = context.resources.getDimensionPixelSize(DR.dimen.card_radius).toFloat()

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = PreviewCardBinding.inflate(inflater, this)
        orientation = VERTICAL
    }

    /**
     * Binds the [PreviewCard] data to the view.
     *
     * @param card The card to bind
     * @param sensitive True if the status that contained this card was marked sensitive
     * @param statusDisplayOptions
     * @param listener
     */
    fun bind(
        card: PreviewCard,
        sensitive: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
        listener: OnClickListener,
    ): Unit = with(binding) {
        cardTitle.text = card.title

        when {
            card.description.isNotBlank() -> card.description
            card.authorName.isNotBlank() -> card.authorName
            else -> null
        }?.let { cardDescription.text = it } ?: cardDescription.hide()

        previewCardWrapper.setOnClickListener { listener.onClick(Target.CARD) }
        cardImage.setOnClickListener { listener.onClick(Target.IMAGE) }

        cardLink.text = card.url

        previewCardWrapper.clipToOutline = true

        // Either:
        // 1. Card has a (possibly sensitive) image that user wants to see, or
        // 2. Card has a blurhash, use that as the image, or
        // 3. Use R.drawable.card_image_placeholder
        if (statusDisplayOptions.mediaPreviewEnabled && (!sensitive || statusDisplayOptions.showSensitiveMedia) && !card.image.isNullOrBlank()) {
            cardImage.shapeAppearanceModel = if (card.width > card.height) {
                setTopBottomLayout()
            } else {
                setLeftRightLayout()
            }.build()

            cardImage.scaleType = ImageView.ScaleType.CENTER_CROP

            val builder = Glide.with(cardImage.context)
                .load(card.image)
                .dontTransform()
            if (statusDisplayOptions.useBlurhash && !card.blurhash.isNullOrBlank()) {
                builder
                    .placeholder(decodeBlurHash(cardImage.context, card.blurhash!!))
                    .into(cardImage)
            } else {
                builder.into(cardImage)
            }
        } else if (statusDisplayOptions.useBlurhash && !card.blurhash.isNullOrBlank()) {
            cardImage.shapeAppearanceModel = setLeftRightLayout().build()
            cardImage.scaleType = ImageView.ScaleType.CENTER_CROP

            Glide.with(cardImage.context)
                .load(decodeBlurHash(cardImage.context, card.blurhash!!))
                .dontTransform()
                .into(cardImage)
        } else {
            cardImage.shapeAppearanceModel = setLeftRightLayout().build()
            cardImage.scaleType = ImageView.ScaleType.CENTER

            Glide.with(cardImage.context)
                .load(R.drawable.card_image_placeholder)
                .into(cardImage)
        }
    }

    /** Adjusts the layout parameters to place the image above the information views */
    private fun setTopBottomLayout() = with(binding) {
        val cardImageShape = ShapeAppearanceModel.Builder()
        previewCardWrapper.orientation = VERTICAL
        cardImage.layoutParams.height =
            cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_vertical_height)
        cardImage.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        cardInfo.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        cardInfo.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius)
        cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, radius)
        return@with cardImageShape
    }

    /** Adjusts the layout parameters to place the image on the left, the information on the right */
    private fun setLeftRightLayout() = with(binding) {
        val cardImageShape = ShapeAppearanceModel.Builder()
        previewCardWrapper.orientation = HORIZONTAL
        cardImage.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        cardImage.layoutParams.width =
            cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_horizontal_width)
        cardInfo.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        cardInfo.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius)
        cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, radius)
        return@with cardImageShape
    }
}
