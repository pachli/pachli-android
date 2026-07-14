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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.CollectionCardViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Collection
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.model.collection.CollectionDisplayReason
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.databinding.CollectionCardBinding
import app.pachli.core.ui.extensions.contentDescription
import app.pachli.core.ui.extensions.setMinimumTouchTarget
import app.pachli.core.ui.extensions.useInPlace
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import kotlin.math.roundToInt

/**
 * Compound view that displays [Collection].
 *
 * Classes hosting this should provide a [CollectionCardActionListener]
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
    private val avatarCornerRadius: Int

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

            with(binding.discoverable) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardDiscoverableTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardDiscoverableTextColor, 0))
            }

            avatarDimen = it.getDimensionPixelSize(DR.styleable.CollectionCardView_collectionCardAvatarSize, -1)
            avatarCornerRadius = it.getDimensionPixelSize(DR.styleable.CollectionCardView_collectionCardAvatarCornerRadius, -1)

            avatarImageViews = listOf(binding.avatar1, binding.avatar2, binding.avatar3, binding.avatar4)

            with(binding.sensitive) {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    it.getDimension(DR.styleable.CollectionCardView_collectionCardSensitiveTextSize, 0f),
                )
                setTextColor(it.getColor(DR.styleable.CollectionCardView_collectionCardSensitiveTextColor, 0))

                // Update the size of the drawable in binding.sensitive to match the text size.
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
     */
    fun bind(
        glide: RequestManager,
        viewData: CollectionCardViewData,
        statusDisplayOptions: StatusDisplayOptions,
        showOwner: Boolean,
        listener: CollectionCardActionListener,
    ): Unit = with(binding) {
        val timelineCollection = viewData.timelineCollection

        val displayAction = viewData.displayAction

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
        changeDisplayActionButton.setOnClickListener {
            listener.onCollectionDisplayActionChange(
                viewData,
                (displayAction as? CollectionDisplayAction.Show)?.originalAction ?: CollectionDisplayAction.Hide(reason = CollectionDisplayReason.UserAction),
            )
        }

        collectionName.text = viewData.name

        if (showOwner) {
            ownerHandle.text = timelineCollection.account?.username?.let {
                context.getString(DR.string.post_username_format, it)
            } ?: timelineCollection.accountId
            ownerHandle.show()
        } else {
            ownerHandle.hide()
        }

        if (viewData.description.isBlank()) {
            description.hide()
        } else {
            description.text = viewData.description
            description.show()
        }

        val shallowTag = viewData.hashtag
        if (shallowTag == null || shallowTag.name.isBlank()) {
            hashtag.hide()
            touchDelegate = null
            hashtag.setOnClickListener(null)
        } else {
            val spannable = SpannableString("#${shallowTag.name}")
            val hashtagSpan = HashtagSpan(shallowTag.name, statusDisplayOptions.linksToUnderline.contains(LinksToUnderline.HASHTAGS), shallowTag.url, null)
            spannable.setSpan(hashtagSpan, 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            hashtag.text = spannable

            hashtag.setMinimumTouchTarget()
            hashtag.setOnClickListener { listener.onViewTag(shallowTag.name) }
            hashtag.show()
        }

        val avatarIconUrls = timelineCollection.itemIconUrls.filterNotNull().take(4)
        avatarImageViews.forEachIndexed { index, view ->
            val iconUrl = avatarIconUrls.getOrNull(index)
            if (iconUrl == null) {
                view.hide()
                return@forEachIndexed
            }

            loadAvatar(glide, iconUrl, view, avatarCornerRadius, statusDisplayOptions.animateAvatars)
            view.show()
        }

        with(binding.discoverable) {
            if (viewData.discoverable) {
                text = context.getString(R.string.collection_discoverable_true_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_public, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            } else {
                text = context.getString(R.string.collection_discoverable_false_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_lock, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
            show()
        }

        binding.sensitive.visible(viewData.sensitive)

        val showDivider = viewData.isMember
        controlDivider.visible(showDivider)

        collectionRemoveSelf.setOnClickListener { listener.onRevokeUserFromCollection(viewData) }
        collectionRemoveSelf.visible(viewData.isMember)

        if (displayAction is CollectionDisplayAction.Hide) {
            blurView.setupWith(binding.blurTarget).setBlurRadius(4f)

            collectionHiddenName.text = viewData.timelineCollection.name.unicodeWrap()
            collectionHiddenAction.text = displayAction.reason.getFormattedDescription(context)
            collectionHidden.setOnClickListener {
                listener.onCollectionDisplayActionChange(
                    viewData,
                    CollectionDisplayAction.Show(originalAction = displayAction),
                )
            }
            // Capture all other clicks. This is different behaviour to sensitive media
            // display, where clicking anywhere on the blurred image will show it. Might
            // change this based on user feedback.
            blurView.setOnClickListener { /* Capture all other clicks */ }
            blurView.show()

            // Need to invalidate otherwise the blur is not calculated for some reason.
            blurView.invalidate()
        } else {
            blurView.hide()
            blurView.setOnClickListener(null)
            collectionHidden.setOnClickListener(null)
        }

        contentDescription = when (displayAction) {
            is CollectionDisplayAction.Hide -> buildString {
                // Hidden?
                // "Collection X (optional), by Y."
                // "Contains sensitive content." or "You hid this."
                val ownerAccount = viewData.timelineCollection.account
                if (ownerAccount != null && showOwner) {
                    append(
                        context.getString(
                            R.string.collection_content_description_name_and_owner,
                            viewData.name,
                            ownerAccount.contentDescription(context),
                        ),
                    )
                } else {
                    append(context.getString(R.string.collection_content_description_name, viewData.name))
                }
                append("\n")
                append(displayAction.reason.getFormattedDescription(context))
            }

            is CollectionDisplayAction.Show -> buildString {
                // Not hidden?
                //
                // "Collection X (optional), by Y."
                // "(optional) <description>."
                // "(optional) #hashtag"
                // "Contains X accounts, (including yours)."
                // "(optional) Discoverable in search results..."
                // "(optional) Includes sensitive content..."
                val ownerAccount = viewData.timelineCollection.account
                if (ownerAccount != null && showOwner) {
                    append(
                        context.getString(
                            R.string.collection_content_description_name_and_owner,
                            viewData.name,
                            ownerAccount.contentDescription(context),
                        ),
                    )
                } else {
                    append(context.getString(R.string.collection_content_description_name, viewData.name))
                }

                if (viewData.description.isNotBlank()) {
                    append("\n")
                    append(viewData.description)
                }

                if (viewData.timelineCollection.hashtag?.name?.isNotBlank() == true) {
                    append("\n")
                    append("#${viewData.timelineCollection.hashtag?.name}")
                }

                if (viewData.isMember) {
                    append("\n")
                    append(
                        context.resources.getQuantityString(
                            R.plurals.collection_content_description_accounts_is_member,
                            viewData.timelineCollection.items.size,
                            viewData.timelineCollection.items.size,
                        ),
                    )
                } else {
                    append("\n")
                    append(
                        context.resources.getQuantityString(
                            R.plurals.collection_content_description_accounts_is_not_member,
                            viewData.timelineCollection.items.size,
                            viewData.timelineCollection.items.size,
                        ),
                    )
                }

                if (viewData.discoverable) {
                    append("\n")
                    append(context.getString(R.string.collection_discoverable_true_label))
                } else {
                    append("\n")
                    append(context.getString(R.string.collection_discoverable_false_label))
                }

                if (viewData.sensitive) {
                    append("\n")
                    append(context.getString(R.string.collection_sensitive_label))
                }
            }
        }
    }
}

/** @return UX string explaining why a collection has been hidden. */
private fun CollectionDisplayReason.getFormattedDescription(context: Context) = when (this) {
    CollectionDisplayReason.Sensitive -> context.getString(R.string.collection_hidden_sensitive_title)
    CollectionDisplayReason.UserAction -> context.getString(R.string.collection_hidden_user_action_title)
}
