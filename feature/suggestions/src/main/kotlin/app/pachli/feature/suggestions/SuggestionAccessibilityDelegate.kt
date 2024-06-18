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

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate

/**
 * Accessibility delegate for items in [SuggestionViewHolder].
 *
 * Each item shows actions to:
 *
 * - Open the suggested account's profile
 * - Dismiss the suggestion
 * - Follow the account
 *
 * If the account's bio includes any links or hashtags then actions to show those
 * in a dialog allowing the user to activate one are also included.
 */
internal class SuggestionAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    private val accept: (UiAction) -> Unit,
) : RecyclerViewAccessibilityDelegate(recyclerView) {
    private val context = recyclerView.context

    private val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as AccessibilityManager

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

    private val hashtagsAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_hashtags,
        context.getString(app.pachli.core.ui.R.string.action_hashtags),
    )

    private val delegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host) as SuggestionViewHolder

            if (!viewHolder.viewData.isEnabled) return

            info.addAction(openProfileAction)
            info.addAction(deleteSuggestionAction)
            info.addAction(followAccountAction)

            val links = getLinks(viewHolder)

            // Listed in the same order as ListStatusAccessibilityDelegate to
            // ensure consistent order (links, mentions, hashtags).
            if (links.containsKey(LinkType.Link)) info.addAction(linksAction)

            // Disabling support for mentions at the moment, as the API response
            // doesn't break them out (https://github.com/mastodon/mastodon/issues/27745).
            // if (links.containsKey(LinkType.Mention)) info.addAction(mentionsAction)

            if (links.containsKey(LinkType.HashTag)) info.addAction(hashtagsAction)
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewHolder = recyclerView.findContainingViewHolder(host) as? SuggestionViewHolder ?: return false
            val viewData = viewHolder.viewData

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
                    val links = getLinks(viewHolder)[LinkType.Link] ?: return true
                    showLinksDialog(host.context, links)
                    true
                }

                app.pachli.core.ui.R.id.action_hashtags -> {
                    val hashtags = getLinks(viewHolder)[LinkType.HashTag] ?: return true
                    showHashTagsDialog(host.context, hashtags)
                    true
                }

                else -> super.performAccessibilityAction(host, action, args)
            }
        }

        private fun showLinksDialog(context: Context, links: List<LinkSpanInfo>) = AlertDialog.Builder(context)
            .setTitle(app.pachli.core.ui.R.string.title_links_dialog)
            .setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    links.map { it.link },
                ),
            ) { _, which -> accept(UiAction.NavigationAction.ViewUrl(links[which].link)) }
            .show()
            .let { forceFocus(it.listView) }

        private fun showHashTagsDialog(context: Context, hashtags: List<LinkSpanInfo>) = AlertDialog.Builder(context)
            .setTitle(app.pachli.core.ui.R.string.title_hashtags_dialog)
            .setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    hashtags.map { it.text.subSequence(1, it.text.length) },
                ),
            ) { _, which -> accept(UiAction.NavigationAction.ViewHashtag(hashtags[which].text)) }
            .show()
            .let { forceFocus(it.listView) }
    }

    private fun forceFocus(view: View) {
        interrupt()
        view.post {
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    private fun interrupt() = a11yManager.interrupt()

    override fun getItemDelegate(): AccessibilityDelegateCompat = delegate

    enum class LinkType {
        Mention,
        HashTag,
        Link,
    }

    private fun getLinks(viewHolder: SuggestionViewHolder): Map<LinkType, List<LinkSpanInfo>> {
        val note = viewHolder.binding.accountNote.text
        if (note !is Spannable) return emptyMap()

        return note.getSpans(0, note.length, URLSpan::class.java)
            .map {
                LinkSpanInfo(note.subSequence(note.getSpanStart(it), note.getSpanEnd(it)).toString(), it.url)
            }
            .groupBy {
                when {
                    it.text.startsWith("@") -> LinkType.Mention
                    it.text.startsWith("#") -> LinkType.HashTag
                    else -> LinkType.Link
                }
            }
    }

    private data class LinkSpanInfo(val text: String, val link: String)
}
