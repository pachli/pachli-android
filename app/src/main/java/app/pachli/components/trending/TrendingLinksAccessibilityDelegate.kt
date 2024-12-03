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

package app.pachli.components.trending

import android.os.Bundle
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate
import app.pachli.core.ui.di.UseCaseEntryPoint
import app.pachli.view.PreviewCardView
import app.pachli.view.PreviewCardView.Target
import dagger.hilt.android.EntryPointAccessors

/**
 * Accessbility delete for [TrendingLinkViewHolder].
 *
 * Each item shows an action to open the link.
 *
 * If present, an item to show the author's account is also included.
 *
 * If supported, an item to show a timeline of statuses that mention this link
 * is included.
 */
internal class TrendingLinksAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    val listener: PreviewCardView.OnClickListener,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    private val useCaseEntryPoint = EntryPointAccessors.fromApplication<UseCaseEntryPoint>(context.applicationContext)
    val clipboard = useCaseEntryPoint.clipboardUseCase

    private val openLinkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_link,
        context.getString(R.string.action_open_link),
    )

    private val copyLinkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_copy_item,
        context.getString(R.string.action_copy_link),
    )

    private val openBylineAccountAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_byline_account,
        context.getString(R.string.action_open_byline_account),
    )

    private val openTimelineLinkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_timeline_link,
        context.getString(R.string.action_timeline_link),
    )

    private val delegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host)
                as TrendingLinkViewHolder

            info.addAction(openLinkAction)
            info.addAction(copyLinkAction)

            viewHolder.link.authors?.firstOrNull()?.account?.let {
                info.addAction(openBylineAccountAction)
            }

            if ((recyclerView.adapter as? TrendingLinksAdapter)?.showTimelineLink == true) {
                info.addAction(openTimelineLinkAction)
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewHolder = recyclerView.findContainingViewHolder(host)
                as TrendingLinkViewHolder

            return when (action) {
                app.pachli.core.ui.R.id.action_open_link -> {
                    interrupt()
                    listener.onClick(viewHolder.link, Target.CARD)
                    true
                }
                app.pachli.core.ui.R.id.action_copy_item -> {
                    clipboard.copyTextTo(viewHolder.link.url)
                    true
                }
                app.pachli.core.ui.R.id.action_open_byline_account -> {
                    interrupt()
                    listener.onClick(viewHolder.link, Target.BYLINE)
                    true
                }
                app.pachli.core.ui.R.id.action_timeline_link -> {
                    interrupt()
                    listener.onClick(viewHolder.link, Target.TIMELINE_LINK)
                    true
                }
                else -> super.performAccessibilityAction(host, action, args)
            }
        }
    }

    override fun getItemDelegate(): AccessibilityDelegateCompat = delegate
}
