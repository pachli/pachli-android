/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.ui.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import app.pachli.core.model.Emoji
import app.pachli.core.ui.R
import app.pachli.core.ui.databinding.EmojiPickerBinding
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Compound view displaying a grid of emojis for selection and a text control
 * allowing the user to search / filter the grid.
 *
 * @property emojis List of [Emoji] to display.
 * @property animate True if emojis should be animated.
 * @property onSelectEmoji [EmojiClickListener] called when the user clicks
 * an emoji.
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding: EmojiPickerBinding

    /** Text string to use as the label for when [Emoji.category] is null. */
    private val labelNoCategory = context.getString(R.string.label_emoji_no_category)

    // Using Glide.with like this in a View subclass is OK.
    @SuppressLint("GlideWithViewDetector")
    val glide = Glide.with(this)

    private var detailEmoji: Emoji? = null

    private var showDetail = false

    private val adapter = EmojiAdapter(glide, labelNoCategory).apply {
        onClick = ::onClick
    }

    var emojis: List<Emoji> = emptyList()
        set(value) {
            adapter.emojis = value
        }

    var animate: Boolean = false
        set(value) {
            adapter.animate = value
            field = value
        }

    var onSelectEmoji: ((Emoji) -> Unit)? = null

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = EmojiPickerBinding.inflate(inflater, this)

        binding.emojiGrid.adapter = adapter
        binding.emojiFilter.editText?.doAfterTextChanged { adapter.filter.filter(it) }

        binding.showDetail.setOnCheckedChangeListener { _, checked ->
            showDetail = checked
            bindEmojiDetail()
        }

        binding.emojiDetailImage.setOnClickListener { detailEmoji?.let { onSelectEmoji?.invoke(it) } }

        binding.emojiDetailImage.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(resources.getDimension(app.pachli.core.designsystem.R.dimen.account_avatar_background_radius))
                .build(),
        ).apply {
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(binding.emojiDetailImage, com.google.android.material.R.attr.colorSurfaceContainer))
        }

        bindEmojiDetail()
    }

    private fun onClick(emoji: Emoji) {
        // Update the detail emoji, so the most recent emoji is shown irrespective of
        // whether or not the detail view is open now.
        detailEmoji = emoji

        if (showDetail) {
            bindEmojiDetail()
            return
        }
        onSelectEmoji?.invoke(emoji)
    }

    private fun bindEmojiDetail() {
        binding.emojiDetail.isVisible = showDetail
        detailEmoji?.let { detailEmoji ->
            glide.load(if (animate) detailEmoji.url else detailEmoji.staticUrl).into(binding.emojiDetailImage)
            contentDescription = detailEmoji.shortcode

            // Set the shortcode text. This may need to scroll (starts scrolling if necessary
            // and if isSelected is true).
            with(binding.emojiDetailShortcode) {
                // Disable any scrolling, so the text stays still when initially set.
                isSelected = false

                text = detailEmoji.shortcode

                // Start scrolling, if necessary, with a 1 second delay.
                postDelayed({ isSelected = true }, 1000)
            }
        }
    }
}
