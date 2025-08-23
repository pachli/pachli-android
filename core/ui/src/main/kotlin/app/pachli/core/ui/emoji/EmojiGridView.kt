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
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.ui.R

/**
 * Displays a grid of emojis in a [RecyclerView] using [GridLayoutManager].
 *
 * Each emoji is displayed in 48dp x 48dp span in the grid. Category headings
 * span the full width of the grid.
 *
 * The [adapter] must be an [EmojiAdapter].
 */
internal class EmojiGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {
    /**
     * Layout manager for the grid. Initial span count is 1, the actual span count is
     * determined in [onMeasure].
     */
    private val gridLayoutManager = GridLayoutManager(context, 1, GridLayoutManager.VERTICAL, false)

    /** Width of an [app.pachli.databinding.ItemEmojiButtonBinding] in pixels. */
    private val pxSpanWidth = dpToPx(40f, context.resources.displayMetrics)

    /**
     * Determines the size of each span based on the item's view type.
     *
     * [Headers][app.pachli.databinding.ItemEmojiHeaderBinding] take the full
     * width of the layout, everything else uses one span.
     */
    private val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (adapter?.getItemViewType(position)) {
                R.layout.item_emoji_category -> gridLayoutManager.spanCount
                else -> 1
            }
        }
    }.apply {
        isSpanIndexCacheEnabled = true
        isSpanGroupIndexCacheEnabled = true
    }

    init {
        clipToPadding = false
        gridLayoutManager.spanSizeLookup = spanSizeLookup
        layoutManager = gridLayoutManager
    }

    /**
     * Computes the number of spans to show based on the width of the view and
     * width of each span.
     */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        val spanCount = 1.coerceAtLeast((measuredWidth / pxSpanWidth).toInt())
        gridLayoutManager.spanCount = spanCount
    }
}
