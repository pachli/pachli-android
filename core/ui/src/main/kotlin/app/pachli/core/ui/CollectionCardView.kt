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

package app.pachli.core.ui

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.Px
import androidx.appcompat.content.res.AppCompatResources
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.CollectionCardViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Collection
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.model.collection.CollectionDisplayReason
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.databinding.CollectionCardBinding
import app.pachli.core.ui.extensions.setMinimumTouchTarget
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import kotlin.math.roundToInt

/**
 * Compound view that displays [Collection].
 *
 * Classes hosting this should provide a [CollectionCardView.OnClickListener]
 * to be notified when the user clicks on parts of the collection.
 */
class CollectionCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding = CollectionCardBinding.inflate(LayoutInflater.from(context), this)

    @Px
    private val avatarDimen: Int

    @Px
    private val avatarCornerRadius: Int // = context.resources.getDimensionPixelSize(DR.dimen.collection_card_avatar_radius)

    private val avatarImageViews: List<ImageView>

    init {
        clipToOutline = true
        clipChildren = true

        context.obtainStyledAttributes(
            attrs,
            DR.styleable.CollectionCardView,
            DR.attr.collectionCardViewStyle,
            DR.style.Pachli_Widget_CollectionCardView,
        ).useInPlace {
            background = it.getDrawable(DR.styleable.CollectionCardView_android_background)
            foreground = it.getDrawable(DR.styleable.CollectionCardView_android_foreground)
            binding.content.background = it.getDrawable(DR.styleable.CollectionCardView_collectionCardBackground)

            with(binding.collectionName) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardNameTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardNameTextColor, 0))
            }

            with(binding.ownerHandle) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardOwnerTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardOwnerTextColor, 0))
            }

            with(binding.description) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardDescriptionTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardDescriptionTextColor, 0))
            }

            with(binding.remainingItems) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardItemCountTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardItemCountTextColor, 0))
            }

            avatarDimen = it.getDimensionPixelSize(DR.styleable.CollectionCardView_collectionCardAvatarSize, -1)
            avatarCornerRadius = it.getDimensionPixelSize(DR.styleable.CollectionCardView_collectionCardAvatarCornerRadius, -1)

            avatarImageViews = listOf(binding.avatar1, binding.avatar2, binding.avatar3, binding.avatar4)

            // Update the size of the drawable in binding.sensitive to match the text size.
            with(binding.sensitive) {
                post {
                    compoundDrawablesRelative.getOrNull(0)?.let { drawable ->
                        val textSize = textSize.roundToInt()
                        val aspectRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                        val targetWidth = (textSize * aspectRatio).roundToInt()
                        drawable.setBounds(0, 0, targetWidth, textSize)
                        setCompoundDrawablesRelative(drawable, null, null, null)
                    }
                }
            }
        }
    }

    /**
     * @param showOwner True if the collection owner's information should be shown.
     * Some displays may not need this (e.g., in a notification about a collection,
     * the owner's information is already shown in the notification).
     * @param isMember True if the user is a member of this collection.
     */
    fun bind(
        glide: RequestManager,
        viewData: CollectionCardViewData,
        statusDisplayOptions: StatusDisplayOptions,
        showOwner: Boolean,
        isMember: Boolean,
        listener: CollectionCardActionListener,
    ): Unit = with(binding) {
        val timelineCollection = viewData.timelineCollection

        val displayAction = viewData.displayAction
        val hide = displayAction is CollectionDisplayAction.Hide

        val remainingItems = timelineCollection.items.size - 4
        if (remainingItems > 0) {
            binding.remainingItems.text = resources.getQuantityString(
                R.plurals.collection_item_n_more,
                remainingItems,
                remainingItems,
            )
            binding.remainingItems.show()
        } else {
            binding.remainingItems.hide()
        }

        // Toggle display button
        val icon = if (hide) DR.drawable.ic_hide_media_24dp else R.drawable.ic_eye_24dp
        changeDisplayActionButton.icon = AppCompatResources.getDrawable(changeDisplayActionButton.context, icon)
        changeDisplayActionButton.show()
        changeDisplayActionButton.setOnClickListener {
            if (hide) {
                listener.onCollectionDisplayActionChange(
                    viewData,
                    CollectionDisplayAction.Show(originalAction = displayAction),
                )
            } else {
                listener.onCollectionDisplayActionChange(
                    viewData,
                    (displayAction as? CollectionDisplayAction.Show)?.originalAction ?: CollectionDisplayAction.Hide(reason = CollectionDisplayReason.UserAction),
                )
            }
        }

        collectionName.text = viewData.name

        ownerHandle.text = timelineCollection.ownerAccount?.username?.let {
            context.getString(DR.string.post_username_format, it)
        } ?: "Unknown user"
        ownerHandle.show()

        if (hide) {
            description.text = displayAction.reason.getFormattedDescription(description.context)
            description.setOnClickListener {
                listener.onCollectionDisplayActionChange(
                    viewData,
                    CollectionDisplayAction.Show(originalAction = viewData.displayAction as CollectionDisplayAction.Hide?),
                )
            }
        } else {
            if (viewData.description.isBlank()) {
                description.hide()
            } else {
                description.text = viewData.description
                description.show()
            }
            description.setOnClickListener(null)
        }

        val shallowTag = viewData.hashtag
        if (hide || shallowTag == null || shallowTag.name.isBlank()) {
            hashtag.hide()
            touchDelegate = null
            hashtag.setOnClickListener(null)
        } else {
            val spannable = SpannableString("#${shallowTag.name}")
            val hashtagSpan = HashtagSpan(shallowTag.name, statusDisplayOptions.linksToUnderline.contains(LinksToUnderline.HASHTAGS), shallowTag.url, null)
            spannable.setSpan(hashtagSpan, 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            hashtag.text = spannable

            hashtag.setMinimumTouchTarget()
            hashtag.setOnClickListener {
                listener.onViewTag(shallowTag.name)
            }
            hashtag.show()
        }

        val avatarIconUrls = timelineCollection.itemIconUrls.filterNotNull().take(4)
        avatarImageViews.forEachIndexed { index, view ->
            val iconUrl = avatarIconUrls.getOrNull(index)
            if (iconUrl == null) {
                view.hide()
                return@forEachIndexed
            }

            if (hide) {
                view.setImageDrawable(AppCompatResources.getDrawable(view.context, DR.drawable.avatar_default))
            } else {
                loadAvatar(glide, iconUrl, view, avatarCornerRadius, statusDisplayOptions.animateAvatars)
            }
            view.show()
        }

//        itemCount.text = resources.getQuantityString(
//            R.plurals.collection_item_count,
//            timelineCollection.itemIconUrls.size,
//            timelineCollection.itemIconUrls.size,
//        )
//        itemCount.show()

        // TODO: Copied from CollectionActivity
        with(binding.discoverable) {
            if (viewData.discoverable) {
                text = context.getString(app.pachli.core.ui.R.string.collection_discoverable_true_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_public, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            } else {
                text = context.getString(app.pachli.core.ui.R.string.collection_discoverable_false_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_lock, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
            show()
        }

        binding.sensitive.visible(viewData.sensitive)

        val showDivider = isMember
        controlDivider.visible(showDivider)

        collectionRemoveSelf.setOnClickListener { listener.onRemoveUserFromCollection(viewData) }
        collectionRemoveSelf.visible(isMember)
    }
}

/** @return UX string explaining why a collection has been hidden. */
private fun CollectionDisplayReason.getFormattedDescription(context: Context) = when (this) {
    CollectionDisplayReason.Sensitive -> "Sensitive content. Tap to show."
    CollectionDisplayReason.UserAction -> "You hid this. Tap to show."
}
