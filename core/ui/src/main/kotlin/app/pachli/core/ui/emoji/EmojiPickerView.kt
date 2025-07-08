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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import app.pachli.core.model.Emoji
import app.pachli.core.ui.R
import app.pachli.core.ui.databinding.EmojiPickerBinding
import com.bumptech.glide.Glide

/**
 * Compound view displaying a grid of emojis for selection and a text control
 * allowing the user to search / filter the grid.
 *
 * @property emojis List of [Emoji] to display.
 * @property animate True if emojis should be animated.
 * @property clickListener [EmojiClickListener] called when the user clicks
 * an emoji.
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding: EmojiPickerBinding

    /** Text string to use as the label for when [Emoji.category] is null. */
    private val labelNoCategory = context.getString(R.string.label_emoji_no_category)

    private val adapter = EmojiAdapter(Glide.with(this), labelNoCategory)

    var emojis: List<Emoji> = emptyList()
        set(value) {
            adapter.emojis = value
        }

    var animate: Boolean = false
        set(value) {
            adapter.animate = value
        }

    var clickListener: EmojiClickListener? = null
        set(value) {
            adapter.onClick = value
        }

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = EmojiPickerBinding.inflate(inflater, this)

        binding.emojiGrid.adapter = adapter
        binding.emojiFilter.editText?.doAfterTextChanged { adapter.filter.filter(it) }
    }
}
