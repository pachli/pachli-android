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

package app.pachli.feature.collections

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
import app.pachli.feature.collections.ICollectionViewModel.NavigationAction

internal class AccountInCollectionAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    private val accept: (ICollectionViewModel.UiAction) -> Unit,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    private val openProfileAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_profile,
        context.getString(app.pachli.core.ui.R.string.action_view_profile),
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

            val viewHolder = recyclerView.findContainingViewHolder(host) as AccountInCollectionViewHolder

            if (!viewHolder.viewData.isEnabled) return

            info.addAction(openProfileAction)

            // TODO: Add the primary action.

            // The profile's bio or description. Might not be spannable (e.g., if empty).
            (viewHolder.binding.accountNote.text as? Spannable)?.let { accountNote ->
                if (accountNote.getLinks().any()) info.addAction(linksAction)
                if (accountNote.getUrlMentions().any()) info.addAction(mentionsAction)
                if (accountNote.getHashtags().any()) info.addAction(hashtagsAction)
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewHolder = recyclerView.findContainingViewHolder(host) as? AccountInCollectionViewHolder ?: return false
            val viewData = viewHolder.viewData

            if (!viewData.isEnabled) return false

            return when (action) {
                app.pachli.core.ui.R.id.action_open_profile -> {
                    interrupt()
                    accept(NavigationAction.ViewAccount(viewData.account.serverId))
                    true
                }

//                app.pachli.core.ui.R.id.action_dismiss_follow_suggestion -> {
//                    interrupt()
//                    accept(UiAction.SuggestionAction.DeleteSuggestion(viewData.pachliAccountId, viewData.suggestion))
//                    true
//                }
//
//                app.pachli.core.ui.R.id.action_follow_account -> {
//                    interrupt()
//                    accept(UiAction.SuggestionAction.AcceptSuggestion(viewData.pachliAccountId, viewData.suggestion))
//                    true
//                }

                app.pachli.core.ui.R.id.action_links -> {
                    val text = viewHolder.binding.accountNote.text as? Spannable ?: return false
                    val links = text.getLinks()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_links_dialog,
                        links.map { it.url },
                    ) { accept(NavigationAction.ViewUrl(links[it].url)) }
                    true
                }

                app.pachli.core.ui.R.id.action_mentions -> {
                    val text = viewHolder.binding.accountNote.text as? Spannable ?: return false
                    val mentions = text.getUrlMentions()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_mentions_dialog,
                        mentions.map { text.subSequence(it).toString() },
                    ) { accept(NavigationAction.ViewUrl(mentions[it].url)) }
                    true
                }

                app.pachli.core.ui.R.id.action_hashtags -> {
                    val text = viewHolder.binding.accountNote.text as? Spannable ?: return false
                    val hashtags = text.getHashtags()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_hashtags_dialog,
                        hashtags.map { "#$it" },
                    ) { accept(NavigationAction.ViewHashtag(hashtags[it].toString())) }
                    true
                }

                else -> super.performAccessibilityAction(host, action, args)
            }
        }
    }

    // TODO: Copied from SuggestionAccessibilityDelegate, maybe make common.
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
