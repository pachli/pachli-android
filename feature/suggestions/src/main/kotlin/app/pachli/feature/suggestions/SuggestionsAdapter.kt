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

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.Suggestion
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.setClickableText
import app.pachli.feature.suggestions.SuggestionViewHolder.ChangePayload
import app.pachli.feature.suggestions.UiAction.NavigationAction
import app.pachli.feature.suggestions.UiAction.SuggestionAction
import app.pachli.feature.suggestions.databinding.ItemSuggestionBinding
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Adapter for [Suggestion].
 *
 * Suggestions are shown with buttons to dismiss the suggestion or follow the
 * account.
 */
// TODO: This is quite similar to AccountAdapter, see if some functionality can be
// made common. See things like FollowRequestViewHolder.setupWithAccount as well.
internal class SuggestionsAdapter(
    private var animateAvatars: Boolean,
    private var animateEmojis: Boolean,
    private var showBotOverlay: Boolean,
    private val accept: (UiAction) -> Unit,
) : ListAdapter<SuggestionViewData, SuggestionViewHolder>(SuggestionDiffer) {
    override fun getItemViewType(position: Int) = R.layout.item_suggestion

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestionViewHolder(binding, accept)
    }

    fun setAnimateAvatars(animateAvatars: Boolean) {
        if (this.animateAvatars == animateAvatars) return
        this.animateAvatars = animateAvatars
        notifyItemRangeChanged(0, currentList.size, ChangePayload.AnimateAvatars(animateAvatars))
    }

    fun setAnimateEmojis(animateEmojis: Boolean) {
        if (this.animateEmojis == animateEmojis) return
        this.animateEmojis = animateEmojis
        notifyItemRangeChanged(0, currentList.size, ChangePayload.AnimateEmojis(animateEmojis))
    }

    fun setShowBotOverlay(showBotOverlay: Boolean) {
        if (this.showBotOverlay == showBotOverlay) return
        this.showBotOverlay = showBotOverlay
        notifyItemRangeChanged(0, currentList.size, ChangePayload.ShowBotOverlay(showBotOverlay))
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int, payloads: List<Any?>) {
        val viewData = currentList[position]
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            payloads.filterIsInstance<ChangePayload>().forEach { payload ->
                when (payload) {
                    is ChangePayload.IsEnabled -> holder.bindIsEnabled(payload.isEnabled)
                    is ChangePayload.AnimateAvatars -> holder.bindAvatar(viewData, payload.animateAvatars)
                    is ChangePayload.AnimateEmojis -> holder.bindAnimateEmojis(viewData, payload.animateEmojis)
                    is ChangePayload.ShowBotOverlay -> holder.bindShowBotOverlay(viewData, payload.showBotOverlay)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(
            currentList[position],
            animateEmojis,
            animateAvatars,
            showBotOverlay,
        )
    }
}

/**
 * Manage the display of a single suggestion.
 *
 * @param binding View binding.
 * @param accept Asynchronous receiver of [UiAction].
 */
internal class SuggestionViewHolder(
    internal val binding: ItemSuggestionBinding,
    private val accept: (UiAction) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    internal lateinit var viewData: SuggestionViewData
    private lateinit var suggestion: Suggestion

    private val avatarRadius: Int

    /** Payloads for partial notification of item changes. */
    internal sealed interface ChangePayload {
        /** The [isEnabled] state of the suggestion has changed. */
        data class IsEnabled(val isEnabled: Boolean) : ChangePayload

        /** The [animateAvatars] state of the UI has changed. */
        data class AnimateAvatars(val animateAvatars: Boolean) : ChangePayload

        /** The [animateEmojis] state of the UI has changed. */
        data class AnimateEmojis(val animateEmojis: Boolean) : ChangePayload

        /** The [showBotOverlay] state of the UI has changed. */
        data class ShowBotOverlay(val showBotOverlay: Boolean) : ChangePayload
    }

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

    /** Bind [viewData] to the UI elements. */
    // TODO: Similar to FollowRequestViewHolder.setupWithAccount
    fun bind(
        viewData: SuggestionViewData,
        animateEmojis: Boolean,
        animateAvatars: Boolean,
        showBotOverlay: Boolean,
    ) {
        this.viewData = viewData
        this.suggestion = viewData.suggestion
        val account = suggestion.account

        with(binding) {
            suggestion.sources.firstOrNull()?.let {
                suggestionReason.text = suggestionReason.context.getString(it.stringResource())
                suggestionReason.show()
            } ?: suggestionReason.hide()

            username.text = username.context.getString(app.pachli.core.designsystem.R.string.post_username_format, account.username)

            bindAvatar(viewData, animateAvatars)
            bindAnimateEmojis(viewData, animateEmojis)
            bindShowBotOverlay(viewData, showBotOverlay)
            bindPostStatistics(viewData)
            bindIsEnabled(viewData.isEnabled)

            // Build an accessible content description.
            root.contentDescription = root.context.getString(
                R.string.account_content_description_fmt,
                account.displayName,
                followerCount.text,
                followsCount.text,
                statusesCount.text,
                accountNote.text,
            )

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            root.accessibilityDelegate = null
        }
    }

    /** Enables or disables all views depending on [isEnabled]. */
    fun bindIsEnabled(isEnabled: Boolean) = with(binding) {
        (root as? ViewGroup)?.children?.forEach { it.isEnabled = isEnabled }
        root.isEnabled = isEnabled
    }

    /** Binds the avatar image, respecting [animateAvatars]. */
    fun bindAvatar(viewData: SuggestionViewData, animateAvatars: Boolean) = with(binding) {
        loadAvatar(viewData.suggestion.account.avatar, avatar, avatarRadius, animateAvatars)
    }

    /**
     * Binds the account's [name][app.pachli.core.network.model.Account.name] and
     * [note][app.pachli.core.network.model.Account.note] respecting [animateEmojis].
     */
    fun bindAnimateEmojis(viewData: SuggestionViewData, animateEmojis: Boolean) = with(binding) {
        val account = viewData.suggestion.account
        displayName.text = account.name.unicodeWrap().emojify(account.emojis, itemView, animateEmojis)

        if (account.note.isBlank()) {
            @SuppressLint("SetTextI18n")
            accountNote.text = ""
            accountNote.hide()
        } else {
            accountNote.show()
            val emojifiedNote = account.note.parseAsMastodonHtml()
                .emojify(account.emojis, accountNote, animateEmojis)

            setClickableText(accountNote, emojifiedNote, emptyList(), null, linkListener)
        }
    }

    /**
     * Display's the bot overlay on the avatar image (if appropriate), respecting
     * [showBotOverlay].
     */
    fun bindShowBotOverlay(viewData: SuggestionViewData, showBotOverlay: Boolean) = with(binding) {
        avatarBadge.visible(viewData.suggestion.account.bot && showBotOverlay)
    }

    /** Bind's the account's post statistics. */
    private fun bindPostStatistics(viewData: SuggestionViewData) = with(binding) {
        val account = viewData.suggestion.account

        // Strings all have embedded HTML `<b>...</b>` to render different sections in bold
        // without needing to compute spannable widths from arbitrary content. The `<b>` in
        // the resource strings must have the leading `<` escaped as `&lt;`.

        followerCount.text = HtmlCompat.fromHtml(
            followerCount.context.getString(
                R.string.follower_count_fmt,
                formatNumber(account.followersCount.toLong(), 1000),
            ),
            FROM_HTML_MODE_LEGACY,
        )

        followsCount.text = HtmlCompat.fromHtml(
            followsCount.context.getString(
                R.string.follows_count_fmt,
                formatNumber(account.followingCount.toLong(), 1000),
            ),
            FROM_HTML_MODE_LEGACY,
        )

        // statusesCount can be displayed as either:
        //
        // 1. A count of posts (if the account has no creation date).
        // 2. (1) + a breakdown of posts per week (if there is no "last post" date).
        // 3. (1) + (2) + when the account last posted.
        statusesCount.apply {
            if (account.createdAt == null) {
                text = HtmlCompat.fromHtml(
                    context.getString(
                        R.string.statuses_count_fmt,
                        formatNumber(account.statusesCount.toLong(), 1000),
                    ),
                    FROM_HTML_MODE_LEGACY,
                )
            } else {
                val then = account.createdAt!!.toInstant()
                val now = Instant.now()
                val elapsed = Duration.between(then, now).toDays() / 7.0

                if (account.lastStatusAt == null) {
                    text = HtmlCompat.fromHtml(
                        context.getString(
                            R.string.statuses_count_per_week_fmt,
                            formatNumber(account.statusesCount.toLong(), 1000),
                            (account.statusesCount / elapsed).roundToInt(),
                        ),
                        FROM_HTML_MODE_LEGACY,
                    )
                } else {
                    text = HtmlCompat.fromHtml(
                        context.getString(
                            R.string.statuses_count_per_week_last_fmt,
                            formatNumber(account.statusesCount.toLong(), 1000),
                            (account.statusesCount / elapsed).roundToInt(),
                            DateUtils.getRelativeTimeSpanString(
                                account.lastStatusAt!!.time,
                                now.toEpochMilli(),
                                DateUtils.DAY_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ),
                        ),
                        FROM_HTML_MODE_LEGACY,
                    )
                }
            }
        }
    }
}

private object SuggestionDiffer : DiffUtil.ItemCallback<SuggestionViewData>() {
    override fun areItemsTheSame(oldItem: SuggestionViewData, newItem: SuggestionViewData) = oldItem.suggestion.account.id == newItem.suggestion.account.id
    override fun areContentsTheSame(oldItem: SuggestionViewData, newItem: SuggestionViewData) = oldItem == newItem

    override fun getChangePayload(oldItem: SuggestionViewData, newItem: SuggestionViewData): Any? {
        return when {
            oldItem.isEnabled != newItem.isEnabled -> ChangePayload.IsEnabled(newItem.isEnabled)
            else -> super.getChangePayload(oldItem, newItem)
        }
    }
}
