/* Copyright 2022 Tusky Contributors
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

package app.pachli.components.compose

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import androidx.annotation.WorkerThread
import app.pachli.R
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.formatNumber
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Emoji
import app.pachli.core.model.TimelineAccount
import app.pachli.core.ui.databinding.ItemAutocompleteAccountBinding
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.setRoles
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemAutocompleteEmojiBinding
import app.pachli.databinding.ItemAutocompleteHashtagBinding
import com.bumptech.glide.RequestManager
import kotlinx.coroutines.runBlocking

class ComposeAutoCompleteAdapter(
    private val glide: RequestManager,
    private val autocompletionProvider: AutocompletionProvider,
    private val animateAvatar: Boolean,
    private val animateEmojis: Boolean,
    private val showBotBadge: Boolean,
) : BaseAdapter(), Filterable {

    private var resultList: List<AutocompleteResult> = emptyList()

    override fun getCount() = resultList.size

    override fun getItem(index: Int): AutocompleteResult {
        return resultList[index]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getFilter(): Filter {
        return object : Filter() {

            override fun convertResultToString(resultValue: Any): CharSequence {
                return when (resultValue) {
                    is AutocompleteResult.AccountResult -> formatUsername(resultValue)
                    is AutocompleteResult.HashtagResult -> formatHashtag(resultValue)
                    is AutocompleteResult.EmojiResult -> formatEmoji(resultValue)
                    else -> ""
                }
            }

            @WorkerThread
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null) {
                    // runBlocking here is OK because this happens in a worker thread
                    val results = runBlocking {
                        autocompletionProvider.search(constraint.toString())
                    }
                    filterResults.values = results
                    filterResults.count = results.size
                }
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                if (results.count > 0) {
                    resultList = results.values as List<AutocompleteResult>
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemViewType = getItemViewType(position)
        val context = parent.context

        val view: View = convertView ?: run {
            val layoutInflater = LayoutInflater.from(context)
            val binding = when (itemViewType) {
                ACCOUNT_VIEW_TYPE -> ItemAutocompleteAccountBinding.inflate(layoutInflater)
                HASHTAG_VIEW_TYPE -> ItemAutocompleteHashtagBinding.inflate(layoutInflater)
                EMOJI_VIEW_TYPE -> ItemAutocompleteEmojiBinding.inflate(layoutInflater)
                else -> throw AssertionError("unknown view type")
            }
            binding.root.tag = binding
            binding.root
        }

        when (val binding = view.tag) {
            is ItemAutocompleteAccountBinding -> {
                val account = (getItem(position) as AutocompleteResult.AccountResult).account
                binding.username.text = context.getString(DR.string.post_username_format, account.username)
                binding.displayName.text = account.name.emojify(
                    glide,
                    account.emojis,
                    binding.displayName,
                    animateEmojis,
                )
                val avatarRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_42dp)
                loadAvatar(
                    glide,
                    account.avatar,
                    binding.avatar,
                    avatarRadius,
                    animateAvatar,
                )
                binding.avatarBadge.visible(showBotBadge && account.bot)

                binding.roleChipGroup.setRoles(account.roles)
            }
            is ItemAutocompleteHashtagBinding -> {
                val result = getItem(position) as AutocompleteResult.HashtagResult
                binding.hashtag.text = formatHashtag(result)
                binding.usage7d.text = formatNumber(result.usage7d.toLong(), 10000)
            }
            is ItemAutocompleteEmojiBinding -> {
                val emoji = (getItem(position) as AutocompleteResult.EmojiResult).emoji
                binding.shortcode.text = context.getString(R.string.emoji_shortcode_format, emoji.shortcode)
                glide.load(emoji.url).into(binding.preview)
            }
        }
        return view
    }

    override fun getViewTypeCount() = 3

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AutocompleteResult.AccountResult -> ACCOUNT_VIEW_TYPE
            is AutocompleteResult.HashtagResult -> HASHTAG_VIEW_TYPE
            is AutocompleteResult.EmojiResult -> EMOJI_VIEW_TYPE
        }
    }

    sealed interface AutocompleteResult {
        data class AccountResult(val account: TimelineAccount) : AutocompleteResult

        data class HashtagResult(val hashtag: String, val usage7d: Int) : AutocompleteResult

        data class EmojiResult(val emoji: Emoji) : AutocompleteResult
    }

    interface AutocompletionProvider {
        suspend fun search(token: String): List<AutocompleteResult>
    }

    companion object {
        private const val ACCOUNT_VIEW_TYPE = 0
        private const val HASHTAG_VIEW_TYPE = 1
        private const val EMOJI_VIEW_TYPE = 2

        private fun formatUsername(result: AutocompleteResult.AccountResult): String {
            return "@${result.account.username}"
        }

        private fun formatHashtag(result: AutocompleteResult.HashtagResult): String {
            return "#${result.hashtag}"
        }

        private fun formatEmoji(result: AutocompleteResult.EmojiResult): String {
            return ":${result.emoji.shortcode}:"
        }
    }
}
