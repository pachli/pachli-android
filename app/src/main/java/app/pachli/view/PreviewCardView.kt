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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import app.pachli.R
import app.pachli.core.activity.decodeBlurHash
import app.pachli.core.activity.emojify
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.PreviewCard
import app.pachli.databinding.PreviewCardBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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

        /** The image */
        IMAGE,

        /** The author byline */
        BYLINE,
    }

    fun interface OnClickListener {
        /** @param target Where on the card the user clicked */
        fun onClick(card: PreviewCard, target: Target)
    }

    private val binding: PreviewCardBinding
    private val cardCornerRadius = context.resources.getDimensionPixelSize(DR.dimen.card_radius).toFloat()

    /** Corner radius of the byline avatar. */
    private val bylineAvatarCornerRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp)

    /** Dimensions (width and height) of the byline avatar. */
    val bylineAvatarDimen = context.resources.getDimensionPixelSize(DR.dimen.card_byline_avatar_dimen)

    /** Transformations to apply when loading the byline avatar. */
    private val bylineAvatarTransformation = MultiTransformation(
        buildList {
            add(CenterCrop())
            add(RoundedCorners(bylineAvatarCornerRadius))
        },
    )

    /** Glide custom target that loads images in to the authorInfo drawable */
    private val bylineAvatarTarget: CustomTarget<Drawable>

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = PreviewCardBinding.inflate(inflater, this)
        orientation = VERTICAL

        bylineAvatarTarget = object : CustomTarget<Drawable>(bylineAvatarDimen, bylineAvatarDimen) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                binding.authorInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(resource, null, null, null)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                binding.authorInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(placeholder, null, null, null)
            }
        }
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
        }?.let {
            cardDescription.text = it
            cardDescription.show()
        } ?: cardDescription.hide()

        previewCardWrapper.setOnClickListener { listener.onClick(card, Target.CARD) }
        cardImage.setOnClickListener { listener.onClick(card, Target.IMAGE) }
        byline.referencedIds.forEach { id ->
            root.findViewById<View>(id).setOnClickListener { listener.onClick(card, Target.BYLINE) }
        }

        cardLink.text = card.url

        previewCardWrapper.clipToOutline = true

        // Either:
        // 1. Card has a (possibly sensitive) image that user wants to see, or
        // 2. Card has a blurhash, use that as the image, or
        // 3. Use R.drawable.card_image_placeholder
        if (statusDisplayOptions.mediaPreviewEnabled && (!sensitive || statusDisplayOptions.showSensitiveMedia) && !card.image.isNullOrBlank()) {
            cardImage.show()
            cardImage.shapeAppearanceModel = if (card.width > card.height) {
                setTopBottomLayout()
            } else {
                setStartEndLayout()
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
            cardImage.show()
            cardImage.shapeAppearanceModel = setStartEndLayout().build()
            cardImage.scaleType = ImageView.ScaleType.CENTER_CROP

            Glide.with(cardImage.context)
                .load(decodeBlurHash(cardImage.context, card.blurhash!!))
                .dontTransform()
                .into(cardImage)
        } else {
            cardImage.hide()
        }

        card.authors?.firstOrNull()?.account?.let { account ->
            val name = account.name.unicodeWrap().emojify(account.emojis, authorInfo, false)
            authorInfo.text = authorInfo.context.getString(R.string.preview_card_byline_fmt, name)

            Glide.with(authorInfo.context).load(account.avatar).transform(bylineAvatarTransformation)
                .placeholder(DR.drawable.avatar_default).into(bylineAvatarTarget)
            byline.show()
        } ?: byline.hide()
    }

    /** Adjusts the layout parameters to place the image above the information views */
    private fun setTopBottomLayout() = with(binding) {
        val cardImageShape = ShapeAppearanceModel.Builder()

        // Move image to top.
        with(cardImage.layoutParams as ConstraintLayout.LayoutParams) {
            height = cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_vertical_height)
            width = ViewGroup.LayoutParams.MATCH_PARENT

            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        }

        // Move cardInfo below image
        with(cardInfo.layoutParams as ConstraintLayout.LayoutParams) {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            topToBottom = cardImage.id

            startToEnd = ConstraintLayout.LayoutParams.UNSET
            topToTop = ConstraintLayout.LayoutParams.UNSET
        }

        cardInfo.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
        cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, cardCornerRadius)
        return@with cardImageShape
    }

    /**
     * Adjusts the layout parameters to place the image at the start, the information at
     * the end.
     */
    private fun setStartEndLayout() = with(binding) {
        val cardImageShape = ShapeAppearanceModel.Builder()

        // Move image to start with fixed width to allow space for cardInfo.
        with(cardImage.layoutParams as ConstraintLayout.LayoutParams) {
            height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            width = cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_horizontal_width)
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        // Move cardInfo to end of image
        with(cardInfo.layoutParams as ConstraintLayout.LayoutParams) {
            startToEnd = binding.cardImage.id
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            startToStart = ConstraintLayout.LayoutParams.UNSET
            topToBottom = ConstraintLayout.LayoutParams.UNSET
        }

        cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
        cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
        return@with cardImageShape
    }
}
