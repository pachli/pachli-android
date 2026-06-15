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
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Collection
import app.pachli.core.model.TimelineCollection
import app.pachli.core.ui.databinding.CollectionCardBinding
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager

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
    fun interface OnClickListener {
        fun onClick(collection: Collection)
    }

    private val binding = CollectionCardBinding.inflate(LayoutInflater.from(context), this)

    private val cardCornerRadius = context.resources.getDimensionPixelSize(DR.dimen.card_radius).toFloat()

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
        }
    }

    fun bind(
        glide: RequestManager,
        collection: TimelineCollection,
        listener: OnClickListener?,
    ): Unit = with(binding) {
        name.text = collection.name
        ownerHandle.text = collection.ownerAccount?.let {
            // TODO: Emojify, etc.
            it.name
        } ?: "Unknown user"

        if (collection.description.isBlank()) {
            description.hide()
        } else {
            description.text = "${collection.description}, ${collection.itemIconUrls}"
            description.show()
        }

        itemCount.text = resources.getQuantityString(
            R.plurals.collection_item_count,
            collection.itemIconUrls.size,
            collection.itemIconUrls.size,
        )
    }
}
