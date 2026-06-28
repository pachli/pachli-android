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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.CollectionCardViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Collection
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.model.collection.CollectionDisplayReason
import app.pachli.core.ui.databinding.CollectionCardBinding
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

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

    private val avatarRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_24dp)

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

            with(binding.name) {
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

            with(binding.itemCount) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardItemCountTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardItemCountTextColor, 0))
            }

            val avatarBackground = it.getDrawable(DR.styleable.CollectionCardView_collectionCardAvatarBackground)
            binding.avatar1.background = avatarBackground
            binding.avatar2.background = avatarBackground
            binding.avatar3.background = avatarBackground
            binding.avatar4.background = avatarBackground
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
        // Distinguish between discoverable or not
        // Distinguish between sensitive or not. If sensitive:
        //  - Hide avatars
        //  - Hide description
        //  - Require clickthrough
        //  - Implies we need CollectionViewData to store this
        // Distinguish between "User is in the collection" or not
        // Distinguish between "User owns the collection" or not
        // Show created/updated information?

//        val hide = (viewData.sensitive && !statusDisplayOptions.showSensitiveMedia) || viewData.displayAction is CollectionDisplayAction.Hide
//        val show = viewData.displayAction is CollectionDisplayAction.Show
//
//        // if ((viewData.sensitive && !statusDisplayOptions.showSensitiveMedia) && viewData.displayAction !is CollectionDisplayAction.Show) {
//        if (hide && !show) {
//            bindSensitive(viewData, listener)
//            return
//        }

        if (viewData.displayAction is CollectionDisplayAction.Hide) {
            bindSensitive(viewData, listener)
            return
        }

        val timelineCollection = viewData.timelineCollection

        val avatarIcons = timelineCollection.itemIconUrls.filterNotNull().take(4)

        // TODO: Respect animateIcons

        avatarIcons.getOrNull(0)?.let {
            loadAvatar(glide, it, avatar1, avatarRadius, false)
        } ?: avatar1.setImageDrawable(null)

        avatarIcons.getOrNull(1)?.let {
            loadAvatar(glide, it, avatar2, avatarRadius, false)
        } ?: avatar2.setImageDrawable(null)

        avatarIcons.getOrNull(2)?.let {
            loadAvatar(glide, it, avatar3, avatarRadius, false)
        } ?: avatar3.setImageDrawable(null)

        avatarIcons.getOrNull(3)?.let {
            loadAvatar(glide, it, avatar4, avatarRadius, false)
        } ?: avatar4.setImageDrawable(null)

        name.text = viewData.name

        if (showOwner) {
            ownerHandle.text = timelineCollection.ownerAccount?.let {
                // TODO: Emojify, etc.
                it.name
            } ?: "Unknown user"
            ownerHandle.show()
        } else {
            ownerHandle.hide()
        }

        if (viewData.description.isBlank()) {
            description.hide()
        } else {
            // TODO: SetContent to set clickable text
            description.text = viewData.description
            description.show()
        }

        itemCount.text = resources.getQuantityString(
            R.plurals.collection_item_count,
            timelineCollection.itemIconUrls.size,
            timelineCollection.itemIconUrls.size,
        )
        itemCount.show()

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

        val showDivider = isMember || viewData.sensitive
        controlDivider.visible(showDivider)

        collectionRemoveSelf.setOnClickListener { listener.onRemoveUserFromCollection(viewData) }
        collectionRemoveSelf.visible(isMember)

        hideCollectionButton.show()
        hideCollectionButton.setOnClickListener {
            listener.onCollectionDisplayActionChange(
                viewData,
                (viewData.displayAction as? CollectionDisplayAction.Show)?.originalAction ?: CollectionDisplayAction.Hide(CollectionDisplayReason.UserAction),
            )
        }
    }

    private fun bindSensitive(viewData: CollectionCardViewData, listener: CollectionCardActionListener): Unit = with(binding) {
        avatar1.setImageDrawable(null)
        avatar2.setImageDrawable(null)
        avatar3.setImageDrawable(null)
        avatar4.setImageDrawable(null)

        name.text = viewData.name

        ownerHandle.hide()
        // description.hide()
        description.text = when (viewData.displayAction) {
            is CollectionDisplayAction.Hide -> (viewData.displayAction as CollectionDisplayAction.Hide).reason.getFormattedDescription(description.context)
            is CollectionDisplayAction.Show -> "displayaction.show"
            null -> "null displayaction"
        }
        description.setOnClickListener {
            listener.onCollectionDisplayActionChange(
                viewData,
                CollectionDisplayAction.Show(originalAction = viewData.displayAction as CollectionDisplayAction.Hide?),
            )
        }
        itemCount.hide()
        discoverable.hide()
        hideCollectionButton.hide()
    }

    /** @return UX string explaining why a collection has been hidden. */
    private fun CollectionDisplayReason.getFormattedDescription(context: Context) = when (this) {
        CollectionDisplayReason.Sensitive -> "Sensitive content. Tap to show."
        CollectionDisplayReason.UserAction -> "You hid this. Tap to show."
    }
}
