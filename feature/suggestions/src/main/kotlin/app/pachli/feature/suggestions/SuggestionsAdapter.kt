/*
 * Copyright 2024 Pachli Association
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

package app.pachli.feature.suggestions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.Suggestion
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.setClickableText
import app.pachli.feature.suggestions.UiAction.NavigationAction
import app.pachli.feature.suggestions.UiAction.SuggestionAction
import app.pachli.feature.suggestions.databinding.ItemSuggestionBinding

/**
 * Adapter for [Suggestion].
 *
 * Suggestions are shown with buttons to dismiss the suggestion or follow the
 * account.
 */
// TODO: This is quite similar to AccountAdapter, so if some functionality can be
// made common. See things like FollowRequestViewHolder.setupWithAccount as well.
internal class SuggestionsAdapter(
    private var animateEmojis: Boolean,
    private var animateAvatars: Boolean,
    private var showBotOverlay: Boolean,
    private val accept: (UiAction) -> Unit,
) : ListAdapter<Suggestion, ViewHolder>(SuggestionDiffer) {
    override fun getItemViewType(position: Int) = R.layout.item_suggestion

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, accept)
    }

    fun setAnimateEmojis(animateEmojis: Boolean) {
        if (this.animateEmojis == animateEmojis) return
        this.animateEmojis = animateEmojis
        notifyItemRangeChanged(0, currentList.size)
    }

    fun setAnimateAvatars(animateAvatars: Boolean) {
        if (this.animateAvatars == animateAvatars) return
        this.animateAvatars = animateAvatars
        notifyItemRangeChanged(0, currentList.size)
    }

    fun setShowBotOverlay(showBotOverlay: Boolean) {
        if (this.showBotOverlay == showBotOverlay) return
        this.showBotOverlay = showBotOverlay
        notifyItemRangeChanged(0, currentList.size)
    }

    /** Removes [suggestion] from the current list */
    fun removeSuggestion(suggestion: Suggestion) {
        submitList(currentList.filterNot { it == suggestion })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            currentList[position],
            animateEmojis,
            animateAvatars,
            showBotOverlay,
        )
    }
}

internal class ViewHolder(
    private val binding: ItemSuggestionBinding,
    private val accept: (UiAction) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private lateinit var suggestion: Suggestion

    private val avatarRadius: Int

    /**
     * Link listener for [setClickableText] that generates the appropriate
     * navigation actions.
     */
    private val linkListener = object : LinkListener {
        override fun onViewTag(tag: String) = accept(NavigationAction.ViewHashtag(tag))
        override fun onViewAccount(id: String) = accept(NavigationAction.ViewAccount(id))
        override fun onViewUrl(url: String) = accept(NavigationAction.ViewUrl(url))
    }

    init {
        with(binding) {
            followAccount.setOnClickListener { accept(SuggestionAction.AcceptSuggestion(suggestion)) }
            deleteSuggestion.setOnClickListener { accept(SuggestionAction.DeleteSuggestion(suggestion)) }
            accountNote.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }
            root.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }

            avatarRadius = avatar.context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.avatar_radius_48dp)
        }
    }

    // TODO: Similar to FollowRequestViewHolder.setupWithAccount
    fun bind(
        suggestion: Suggestion,
        animateEmojis: Boolean,
        animateAvatars: Boolean,
        showBotOverlay: Boolean,
    ) {
        this.suggestion = suggestion
        val account = suggestion.account

        with(binding) {
            suggestion.sources.firstOrNull()?.let {
                suggestionReason.text = suggestionReason.context.getString(it.stringResource())
                suggestionReason.show()
            } ?: suggestionReason.hide()

            displayName.text = account.name.unicodeWrap().emojify(account.emojis, itemView, animateEmojis)

            username.text = username.context.getString(app.pachli.core.designsystem.R.string.post_username_format, account.username)

            if (account.note.isBlank()) {
                accountNote.hide()
            } else {
                accountNote.show()
                val emojifiedNote = account.note.parseAsMastodonHtml()
                    .emojify(account.emojis, accountNote, animateEmojis)

                setClickableText(accountNote, emojifiedNote, emptyList(), null, linkListener)
            }

            loadAvatar(account.avatar, avatar, avatarRadius, animateAvatars)
            avatarBadge.visible(showBotOverlay && account.bot)
        }
    }
}

private object SuggestionDiffer : DiffUtil.ItemCallback<Suggestion>() {
    override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem.account == newItem.account
    override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem == newItem
}
