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
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import app.pachli.R
import app.pachli.core.activity.decodeBlurHash
import app.pachli.core.activity.emojify
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.PreviewCard
import app.pachli.core.network.model.TrendsLink
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

        /** The link timeline */
        TIMELINE_LINK,
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

        previewCardWrapper.setOnClickListener { listener.onClick(card, Target.CARD) }
        cardImage.setOnClickListener { listener.onClick(card, Target.IMAGE) }
        authorInfo.setOnClickListener { listener.onClick(card, Target.BYLINE) }
        timelineLink.setOnClickListener { listener.onClick(card, Target.TIMELINE_LINK) }

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

            Glide.with(cardImage.context)
                .load(decodeBlurHash(cardImage.context, card.blurhash!!))
                .dontTransform()
                .into(cardImage)
        } else {
            cardImage.hide()
        }

        var showBylineDivider = false
        bylineDivider.hide()

        // Determine how to show the author info (if present)
        val author = card.authors?.firstOrNull()
        when {
            // Author has an account, link to that, with their avatar.
            author?.account != null -> {
                val name = author.account?.name.unicodeWrap().emojify(author.account?.emojis, authorInfo, false)
                authorInfo.text = HtmlCompat.fromHtml(
                    authorInfo.context.getString(R.string.preview_card_byline_fediverse_account_fmt, name),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                )

                Glide.with(authorInfo.context).load(author.account?.avatar).transform(bylineAvatarTransformation)
                    .placeholder(DR.drawable.avatar_default).into(bylineAvatarTarget)
                authorInfo.show()
                showBylineDivider = true
            }

            // Author has a name but no account. Show the name, clear the avatar.
            // It's not enough that the name is present, it can't be empty, because of
            // https://github.com/mastodon/mastodon/issues/33139).
            !author?.name.isNullOrBlank() -> {
                authorInfo.text = HtmlCompat.fromHtml(
                    authorInfo.context.getString(R.string.preview_card_byline_name_only_fmt, author?.name),
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
        lpCardImage.height = cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_vertical_height)
        lpCardImage.width = ViewGroup.LayoutParams.MATCH_PARENT
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
        lpCardImage.width = cardImage.resources.getDimensionPixelSize(DR.dimen.card_image_horizontal_width)
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
