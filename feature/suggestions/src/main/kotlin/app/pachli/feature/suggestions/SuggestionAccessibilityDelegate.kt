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

import android.os.Bundle
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.view.View
import androidx.core.text.getSpans
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate

/**
 * Accessibility delegate for items in [SuggestionViewHolder].
 *
 * Each item shows actions to:
 *
 * - Open the suggested account's profile
 * - Dismiss the suggestion
 * - Follow the account
 *
 * If the account's bio includes any links, mentions, or hashtags then actions to
 * show those in a dialog allowing the user to copy/activate one are also included.
 */
internal class SuggestionAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    private val accept: (UiAction) -> Unit,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    private val openProfileAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_profile,
        context.getString(app.pachli.core.ui.R.string.action_view_profile),
    )

    private val deleteSuggestionAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_dismiss_follow_suggestion,
        context.getString(R.string.action_dismiss_follow_suggestion),
    )

    private val followAccountAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_follow_account,
        context.getString(R.string.action_follow_account),
    )

    private val linksAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_links,
        context.getString(app.pachli.core.ui.R.string.action_links),
    )

    private val mentionsAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_mentions,
        context.getString(app.pachli.core.ui.R.string.action_mentions),
    )

    private val hashtagsAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_hashtags,
        context.getString(app.pachli.core.ui.R.string.action_hashtags),
    )

    override fun getItemDelegate(): AccessibilityDelegateCompat = delegate

    private val delegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host) as SuggestionViewHolder

            if (!viewHolder.viewData.isEnabled) return

            info.addAction(openProfileAction)
            info.addAction(deleteSuggestionAction)
            info.addAction(followAccountAction)

            val text = viewHolder.binding.accountNote.text as Spannable

            if (text.getLinks().any()) info.addAction(linksAction)
            if (text.getUrlMentions().any()) info.addAction(mentionsAction)
            if (text.getHashtags().any()) info.addAction(hashtagsAction)
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewHolder = recyclerView.findContainingViewHolder(host) as? SuggestionViewHolder ?: return false
            val viewData = viewHolder.viewData
            val text = viewHolder.binding.accountNote.text as Spannable

            if (!viewData.isEnabled) return false

            return when (action) {
                app.pachli.core.ui.R.id.action_open_profile -> {
                    interrupt()
                    accept(UiAction.NavigationAction.ViewAccount(viewData.suggestion.account.id))
                    true
                }

                app.pachli.core.ui.R.id.action_dismiss_follow_suggestion -> {
                    interrupt()
                    accept(UiAction.SuggestionAction.DeleteSuggestion(viewData.suggestion))
                    true
                }

                app.pachli.core.ui.R.id.action_follow_account -> {
                    interrupt()
                    accept(UiAction.SuggestionAction.AcceptSuggestion(viewData.suggestion))
                    true
                }

                app.pachli.core.ui.R.id.action_links -> {
                    val links = (viewHolder.binding.accountNote.text as Spannable).getLinks()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_links_dialog,
                        links.map { it.url },
                    ) { accept(UiAction.NavigationAction.ViewUrl(links[it].url)) }
                    true
                }

                app.pachli.core.ui.R.id.action_mentions -> {
                    val mentions = text.getUrlMentions()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_mentions_dialog,
                        mentions.map { text.subSequence(it).toString() },
                    ) { accept(UiAction.NavigationAction.ViewUrl(mentions[it].url)) }
                    true
                }

                app.pachli.core.ui.R.id.action_hashtags -> {
                    val hashtags = text.getHashtags()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_hashtags_dialog,
                        hashtags.map { "#$it" },
                    ) { accept(UiAction.NavigationAction.ViewHashtag(hashtags[it].toString())) }
                    true
                }

                else -> super.performAccessibilityAction(host, action, args)
            }
        }
    }

    companion object {
        // This is required because account notes don't break mentions out in to
        // a separate `mentions` property or similar. See
        // https://github.com/mastodon/mastodon/issues/27745 for the feature request.
        /** @return [URLSpan]s that have anchor text that looks like an at-mention. */
        @JvmStatic
        fun Spanned.getUrlMentions(): List<URLSpan> = getSpans<URLSpan>(0, length)
            .filter { subSequence(it).isMention() }
    }
}
