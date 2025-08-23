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

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.model.Emoji
import app.pachli.core.ui.R
import app.pachli.core.ui.databinding.ItemEmojiButtonBinding
import app.pachli.core.ui.databinding.ItemEmojiCategoryBinding
import com.bumptech.glide.RequestManager
import java.util.Locale
import timber.log.Timber

/**
 * Items that can be be displayed in the list of emojis.
 *
 * @property id Unique identifier for this item.
 */
internal sealed interface EmojiListItem {
    val id: String

    /**
     * A wrapped emoji.
     *
     * The emoji's [shortcode] is used as the [id]
     */
    @JvmInline
    value class EmojiItem(val emoji: Emoji) : EmojiListItem {
        override val id: String
            get() = emoji.shortcode

        val shortcode: String
            get() = emoji.shortcode

        val category: String?
            get() = emoji.category

        val staticUrl: String
            get() = emoji.staticUrl

        val url: String
            get() = emoji.url
    }

    /**
     * An emoji category.
     *
     * The [title] is used as the [id].
     */
    data class CategoryItem(val title: String) : EmojiListItem {
        override val id = title
    }
}

/** Interface that viewholders for [EmojiListItem] must implement. */
private interface EmojiViewHolder<T : EmojiListItem> {
    fun bind(viewData: T)
}

/**
 * Filterable adapter to display a list of emojis, sorted by [shortcode][Emoji.shortcode]
 * and grouped by sorted [category][Emoji.category].
 *
 * The filter is applied to the emoji's [shortcode][Emoji.shortcode] and
 * [category][Emoji.category].
 *
 * @property glide
 * @property animate True if emojis should be animated.
 * @property labelNoCategory Text label to use if an Emoji's [category][Emoji.category]
 * is null.
 * @property onClick
 */
internal class EmojiAdapter(
    private val glide: RequestManager,
    private val labelNoCategory: String,
) : ListAdapter<EmojiListItem, RecyclerView.ViewHolder>(diffCallback), Filterable {
    /**
     * List of [Emoji] to display. Uncategorised.
     *
     * Changing this will regenerate the categories and update the displayed data.
     */
    internal var emojis: List<Emoji> = emptyList()
        set(value) {
            field = value
            emojiItems = categoriseEmojis()
            submitList(emojiItems)
        }

    /** True if emojis should be animated. */
    internal var animate: Boolean = false
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /** Listener for clicks on [EmojiListItem.EmojiItem]. */
    internal var onClick: ((Emoji) -> Unit)? = null

    /** Emojis, grouped within the list by category. The list is flat. */
    private var emojiItems: List<EmojiListItem> = categoriseEmojis()

    /**
     * Converts [emojis] (a list of emojis with no categories) to a list
     * with a mix of [EmojiListItem.CategoryItem] and [EmojiListItem.EmojiItem]. This
     * is the unfiltered list, used whenever there is no filter constraint.
     *
     * The list is sorted by category name, and within each category by emoji
     * shortcode.
     */
    private fun categoriseEmojis() = emojis.filter { emoji -> emoji.visibleInPicker == null || emoji.visibleInPicker!! }
        .map { EmojiListItem.EmojiItem(it) }
        .groupBy { it.category ?: labelNoCategory }
        .toSortedMap { k1, k2 -> k1.compareTo(k2, ignoreCase = true) }
        .flatMap {
            buildList {
                add(EmojiListItem.CategoryItem(it.key))
                addAll(it.value.sortedBy { it.shortcode.lowercase(Locale.ROOT) })
            }
        }

    /**
     * Filters emojis by category and shortcode. Any empty categories that occur
     * after filtering are also removed.
     */
    private val filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults? {
            val query = constraint?.trim()
            if (query.isNullOrBlank()) return FilterResults().apply { values = emojiItems }

            // Filter in two passes. This first pass removes any items with a
            // shortcode or category that don't match. All headings are retained.
            val filteredEmojis = emojiItems.filter {
                it is EmojiListItem.CategoryItem ||
                    it is EmojiListItem.EmojiItem &&
                    (
                        it.shortcode.contains(query, ignoreCase = true) ||
                            (it.category ?: labelNoCategory).contains(query, ignoreCase = true) == true
                        )
            }

            // Second pass removes category headings that have no emojis in the category.
            val final = buildList {
                var prevItem: EmojiListItem? = null
                filteredEmojis.forEach {
                    if (prevItem == null) {
                        prevItem = it
                    } else {
                        // Previous item was a category heading, and this is an item
                        // in the category -- add the heading.
                        if (prevItem is EmojiListItem.CategoryItem && it is EmojiListItem.EmojiItem) add(prevItem)

                        // This is an emoji. Irrespective of the previous item, add it.
                        if (it is EmojiListItem.EmojiItem) add(it)
                        prevItem = it
                    }
                }
                // Reached the end of the list. If the final item was an emoji then add it.
                if (prevItem is EmojiListItem.EmojiItem) add(prevItem)
            }

            Timber.d("Filtered emoji to $final")

            return FilterResults().apply { values = final }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            submitList(results?.values as? List<EmojiListItem>)
        }
    }

    override fun getFilter() = filter

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is EmojiListItem.CategoryItem -> R.layout.item_emoji_category
            is EmojiListItem.EmojiItem -> R.layout.item_emoji_button
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_emoji_category -> EmojiCategoryViewHolder(ItemEmojiCategoryBinding.inflate(inflater, parent, false))
            R.layout.item_emoji_button -> EmojiItemViewHolder(glide, ItemEmojiButtonBinding.inflate(inflater, parent, false), animate, onClick)
            else -> throw IllegalStateException("incorrect viewType: $viewType")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getItem(position)?.let {
            when (it) {
                is EmojiListItem.CategoryItem -> (holder as EmojiViewHolder<EmojiListItem.CategoryItem>).bind(it)
                is EmojiListItem.EmojiItem -> (holder as EmojiViewHolder<EmojiListItem.EmojiItem>).bind(it)
            }
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<EmojiListItem>() {
            override fun areItemsTheSame(oldItem: EmojiListItem, newItem: EmojiListItem) = oldItem == newItem

            override fun areContentsTheSame(oldItem: EmojiListItem, newItem: EmojiListItem) = oldItem.id == newItem.id
        }
    }
}

/**
 * Viewholder for an [EmojiListItem.EmojiItem].
 *
 * Loads the emoji with [glide], animated according to [animate].
 *
 * Long-pressing the emoji shows a tooltip with the emoji's shortcode.
 *
 * Clicking the emoji calls [onClick].
 *
 * @property glide
 * @property binding
 * @property animate
 * @property onClick
 */
internal class EmojiItemViewHolder(
    val glide: RequestManager,
    val binding: ItemEmojiButtonBinding,
    val animate: Boolean,
    val onClick: ((Emoji) -> Unit)?,
) : EmojiViewHolder<EmojiListItem.EmojiItem>, RecyclerView.ViewHolder(binding.root) {
    // Inline classes can't be marked lateinit, so this must be nullable
    private var emojiItem: EmojiListItem.EmojiItem? = null

    init {
        onClick?.let {
            binding.root.setOnClickListener { emojiItem?.let { onClick(it.emoji) } }
        }
    }

    override fun bind(viewData: EmojiListItem.EmojiItem) {
        with(binding.root) {
            emojiItem = viewData

            glide.load(if (animate) viewData.url else viewData.staticUrl).into(this)

            contentDescription = viewData.shortcode
            TooltipCompat.setTooltipText(this, viewData.shortcode)
        }
    }
}

/**
 * Viewholder for a [EmojiListItem.CategoryItem].
 *
 * Displays the category text.
 */
internal class EmojiCategoryViewHolder(val binding: ItemEmojiCategoryBinding) : EmojiViewHolder<EmojiListItem.CategoryItem>, RecyclerView.ViewHolder(binding.root) {
    override fun bind(viewData: EmojiListItem.CategoryItem) {
        binding.text1.text = viewData.title
    }
}
