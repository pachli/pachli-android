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
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Collection
import app.pachli.core.model.TimelineCollection
import app.pachli.core.ui.databinding.CollectionCardBinding
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager
import timber.log.Timber

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
        collection: TimelineCollection,
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

        val avatarIcons = collection.itemIconUrls.filterNotNull().take(4)

        Timber.d("icons: $avatarIcons")

        avatarIcons.getOrNull(0)?.let {
            loadAvatar(glide, it, binding.avatar1, avatarRadius, false)
            binding.avatar1.show()
        } ?: {
            binding.avatar1.hide()
        }

        avatarIcons.getOrNull(1)?.let {
            loadAvatar(glide, it, binding.avatar2, avatarRadius, false)
            binding.avatar2.show()
        } ?: {
            binding.avatar2.hide()
        }
        avatarIcons.getOrNull(2)?.let {
            loadAvatar(glide, it, binding.avatar3, avatarRadius, false)
            binding.avatar3.show()
        } ?: {
            binding.avatar3.hide()
        }
        avatarIcons.getOrNull(3)?.let {
            loadAvatar(glide, it, binding.avatar4, avatarRadius, false)
            binding.avatar4.show()
        } ?: {
            binding.avatar4.hide()
        }

        name.text = collection.name

        if (showOwner) {
            ownerHandle.text = collection.ownerAccount?.let {
                // TODO: Emojify, etc.
                it.name
            } ?: "Unknown user"
            ownerHandle.show()
        } else {
            ownerHandle.hide()
        }

        if (collection.description.isBlank()) {
            description.hide()
        } else {
            description.text = collection.description
            description.show()
        }

        itemCount.text = resources.getQuantityString(
            R.plurals.collection_item_count,
            collection.itemIconUrls.size,
            collection.itemIconUrls.size,
        )

        val showDivider = isMember
        binding.controlDivider.visible(showDivider)

        binding.collectionRemoveSelf.setOnClickListener { listener.onRemoveUserFromCollection(collection) }
        binding.collectionRemoveSelf.visible(isMember)
    }
}
