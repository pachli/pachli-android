/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.search.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.R
import app.pachli.core.model.HashTag
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.LinkListener
import app.pachli.databinding.ItemHashtagBinding

class SearchHashtagsAdapter(private val linkListener: LinkListener) :
    PagingDataAdapter<HashTag, BindingHolder<ItemHashtagBinding>>(HASHTAG_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemHashtagBinding> {
        val binding = ItemHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemHashtagBinding>, position: Int) {
        getItem(position)?.let { tag ->
            with(holder.binding) {
                text1.text = root.context.getString(R.string.title_tag, tag.name)

                val usage = tag.history.sumOf { it.uses }
                val accounts = tag.history.sumOf { it.accounts }
                val days = tag.history.size

                tagStats.text = root.resources.getString(
                    R.string.followed_hashtags_summary_fmt,
                    root.resources.getQuantityString(R.plurals.followed_hashtags_posts_count_fmt, usage, usage),
                    root.resources.getQuantityString(R.plurals.followed_hashtags_accounts_count_fmt, accounts, accounts),
                    root.resources.getQuantityString(R.plurals.followed_hashtags_days_count_fmt, days, days),
                )

                root.setOnClickListener { linkListener.onViewTag(tag.name) }
            }
        }
    }

    companion object {
        val HASHTAG_COMPARATOR = object : DiffUtil.ItemCallback<HashTag>() {
            override fun areContentsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem.name == newItem.name

            override fun areItemsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem.name == newItem.name
        }
    }
}
