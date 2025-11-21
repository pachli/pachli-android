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
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.PreviewCard
import app.pachli.core.model.TrendsLink
import app.pachli.core.ui.databinding.PreviewCardBinding
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Compound view that displays [app.pachli.core.model.PreviewCard].
 *
 * Classes hosting this should provide a [PreviewCardView.OnClickListener] to be notified when the
 * the user clicks on the card.
 */
class PreviewCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs) {
    /** Where on the card the user clicked */
    enum class Target {
        /** Any part of the card that's not the image */
        CARD,

        /** The image */
        IMAGE,

        /** The author byline */
        BYLINE,

        /** The link timeline */
        TIMELINE_LINK,
    }

    fun interface OnClickListener {
        /** @param target Where on the card the user clicked */
        fun onClick(card: PreviewCard, target: Target)
    }

    private val binding = PreviewCardBinding.inflate(LayoutInflater.from(context), this)

    private val cardCornerRadius = context.resources.getDimensionPixelSize(DR.dimen.card_radius).toFloat()

    /** Dimensions (width and height) of the byline avatar. */
    val bylineAvatarDimen: Int

    /**
     * Height of the preview image, if the image is stacked vertically above
     * the preview content.
     */
    private val imageVerticalHeight: Int

    /**
     * Width of the preview image, if the image is laid out horizontally next
     * to the preview content.
     */
    private val imageHorizontalWidth: Int

    /** Transformations to apply when loading the byline avatar. */
    private val bylineAvatarTransformation: MultiTransformation<Bitmap>

    /** Glide custom target that loads images in to the authorInfo drawable */
    private val bylineAvatarTarget: CustomTarget<Drawable>

    init {
        // Set here instead of an XML attribute as the attribute requires API 31
        clipToOutline = true
        clipChildren = true

        context.obtainStyledAttributes(
            attrs,
            DR.styleable.PreviewCardView,
            DR.attr.previewCardViewStyle,
            DR.style.Pachli_Widget_PreviewCardView,
        ).useInPlace {
            background = it.getDrawable(DR.styleable.PreviewCardView_android_background)
            foreground = it.getDrawable(DR.styleable.PreviewCardView_android_foreground)

            binding.cardInfo.background = it.getDrawable(DR.styleable.PreviewCardView_previewCardInfoBackground)

            with(binding.cardTitle) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.PreviewCardView_previewCardTitleTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.PreviewCardView_previewCardTitleTextColor, 0))
            }
            with(binding.cardDescription) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.PreviewCardView_previewCardDescriptionTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.PreviewCardView_previewCardDescriptionTextColor, 0))
            }
            with(binding.authorInfo) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.PreviewCardView_previewCardAuthorTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.PreviewCardView_previewCardAuthorTextColor, 0))
            }
            with(binding.timelineLink) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.PreviewCardView_previewCardAuthorTimelineLinkTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.PreviewCardView_previewCardAuthorTimelineLinkTextColor, 0))
            }

            bylineAvatarDimen = it.getDimensionPixelSize(DR.styleable.PreviewCardView_previewCardAvatarSize, -1)
            imageVerticalHeight = it.getDimensionPixelSize(DR.styleable.PreviewCardView_previewCardImageVerticalHeight, -1)
            imageHorizontalWidth = it.getDimensionPixelSize(DR.styleable.PreviewCardView_previewCardImageHorizontalWidth, -1)

            val bylineAvatarCornerRadius = it.getDimensionPixelSize(DR.styleable.PreviewCardView_previewCardAvatarCornerRadius, -1)

            bylineAvatarTransformation = MultiTransformation(
                buildList {
                    add(CenterCrop())
                    add(RoundedCorners(bylineAvatarCornerRadius))
                },
            )
        }

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
     * @param showTimelineLink True if the UI to view a timeline of statuses about this link
     * should be shown.
     * @param listener
     */
    fun bind(
        glide: RequestManager,
        card: PreviewCard,
        sensitive: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
        showTimelineLink: Boolean,
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

        setOnClickListener { listener.onClick(card, Target.CARD) }

        cardImage.setOnClickListener { listener.onClick(card, Target.IMAGE) }
        authorInfo.setOnClickListener { listener.onClick(card, Target.BYLINE) }
        timelineLink.setOnClickListener { listener.onClick(card, Target.TIMELINE_LINK) }

        cardLink.text = card.url

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

            val builder = glide.load(card.image)
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

            glide.load(decodeBlurHash(cardImage.context, card.blurhash!!))
                .dontTransform()
                .into(cardImage)
        } else {
            glide.clear(cardImage)
            cardImage.hide()
        }

        var showBylineDivider = false
        bylineDivider.hide()

        // Determine how to show the author info (if present)
        val author = card.authors?.firstOrNull()
        when {
            // Author has an account, link to that, with their avatar.
            author?.account != null -> {
                val name = author.account?.name.unicodeWrap()
                authorInfo.text = HtmlCompat.fromHtml(
                    authorInfo.context.getString(R.string.preview_card_byline_fediverse_account_fmt, name),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                ).emojify(glide, author.account?.emojis, authorInfo, false)

                glide.load(author.account?.avatar).transform(bylineAvatarTransformation)
                    .placeholder(app.pachli.core.designsystem.R.drawable.avatar_default).into(bylineAvatarTarget)
                authorInfo.show()
                showBylineDivider = true
            }

            // Author has a name but no account. Show the name, clear the avatar.
            // It's not enough that the name is present, it can't be empty, because of
            // https://github.com/mastodon/mastodon/issues/33139).
            !author?.name.isNullOrBlank() -> {
                authorInfo.text = HtmlCompat.fromHtml(
                    authorInfo.context.getString(R.string.preview_card_byline_name_only_fmt, author.name),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                )
                authorInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                authorInfo.show()
                showBylineDivider = true
            }

            else -> authorInfo.hide()
        }

        // TrendsLink cards have data about the usage. Show this if the server
        // can generate the timeline.
        if (card is TrendsLink && showTimelineLink) {
            val count = card.history.sumOf { it.uses }
            timelineLink.text = HtmlCompat.fromHtml(
                context.getString(
                    R.string.preview_card_timeline_link_fmt,
                    formatNumber(count.toLong()),
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
            timelineLink.show()
            showBylineDivider = true
        } else {
            timelineLink.hide()
        }

        if (showBylineDivider) bylineDivider.show()
    }

    /** Adjusts the layout parameters to place the image above the information views */
    // Note: Don't try and use with(x.layoutParams) { ... } here, it can fail, see
    // https://issuetracker.google.com/issues/364179209
    private fun setTopBottomLayout() = with(binding) {
        // Move image to top.
        val lpCardImage = cardImage.layoutParams as ConstraintLayout.LayoutParams
        lpCardImage.height = imageVerticalHeight
        lpCardImage.width = LayoutParams.MATCH_PARENT
        lpCardImage.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        cardImage.layoutParams = lpCardImage

        // Move cardInfo below image
        val lpCardInfo = cardInfo.layoutParams as ConstraintLayout.LayoutParams
        lpCardInfo.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lpCardInfo.topToBottom = cardImage.id
        lpCardInfo.startToEnd = ConstraintLayout.LayoutParams.UNSET
        lpCardInfo.topToTop = ConstraintLayout.LayoutParams.UNSET
        cardInfo.layoutParams = lpCardInfo

        // Image top corners should be rounded.
        return@with ShapeAppearanceModel.Builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
            .setTopRightCorner(CornerFamily.ROUNDED, cardCornerRadius)
    }

    /**
     * Adjusts the layout parameters to place the image at the start, the information at
     * the end.
     */
    // Note: Don't try and use with(x.layoutParams) { ... } here, it can fail, see
    // https://issuetracker.google.com/issues/364179209
    private fun setStartEndLayout() = with(binding) {
        // Move image to start with fixed width to allow space for cardInfo.
        val lpCardImage = cardImage.layoutParams as ConstraintLayout.LayoutParams
        lpCardImage.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        lpCardImage.width = imageHorizontalWidth
        lpCardImage.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        cardImage.layoutParams = lpCardImage

        // Move cardInfo to end of image
        val lpCardInfo = cardInfo.layoutParams as ConstraintLayout.LayoutParams
        lpCardInfo.startToEnd = cardImage.id
        lpCardInfo.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        lpCardInfo.startToStart = ConstraintLayout.LayoutParams.UNSET
        lpCardInfo.topToBottom = ConstraintLayout.LayoutParams.UNSET
        cardInfo.layoutParams = lpCardInfo

        // Image left corners should be rounded.
        return@with ShapeAppearanceModel.Builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
            .setBottomLeftCorner(CornerFamily.ROUNDED, cardCornerRadius)
    }
}
