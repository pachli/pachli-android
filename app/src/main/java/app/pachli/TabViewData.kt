/*
 * Copyright 2023 Pachli Association
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

package app.pachli

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import app.pachli.components.conversation.ConversationsFragment
import app.pachli.components.notifications.NotificationsFragment
import app.pachli.components.timeline.TimelineFragment
import app.pachli.components.trending.TrendingLinksFragment
import app.pachli.components.trending.TrendingTagsFragment
import app.pachli.core.database.model.TabData
import app.pachli.core.network.model.TimelineKind

/**
 * Wrap a [TabData] with additional information to display a tab with that data.
 *
 * @param tabData wrapped [TabData]
 * @param text text to use for this tab when displayed in lists
 * @param icon icon to use when displaying the tab
 * @param fragment [Fragment] to display the tab's contents
 * @param title title to display in the action bar if this tab is active
 */
data class TabViewData(
    val tabData: TabData,
    @StringRes val text: Int,
    @DrawableRes val icon: Int,
    val fragment: () -> Fragment,
    val title: (Context) -> String = { context -> context.getString(text) },
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabViewData

        if (tabData != other.tabData) return false

        return true
    }

    override fun hashCode() = tabData.hashCode()

    companion object {
        fun from(tabData: TabData) = when (tabData) {
            TabData.Home -> TabViewData(
                tabData = tabData,
                text = R.string.title_home,
                icon = R.drawable.ic_home_24dp,
                fragment = { TimelineFragment.newInstance(TimelineKind.Home) },
            )
            TabData.Notifications -> TabViewData(
                tabData = tabData,
                text = R.string.title_notifications,
                icon = R.drawable.ic_notifications_24dp,
                fragment = { NotificationsFragment.newInstance() },
            )
            TabData.Local -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_local,
                icon = R.drawable.ic_local_24dp,
                fragment = { TimelineFragment.newInstance(TimelineKind.PublicLocal) },
            )
            TabData.Federated -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_federated,
                icon = R.drawable.ic_public_24dp,
                fragment = { TimelineFragment.newInstance(TimelineKind.PublicFederated) },
            )
            TabData.Direct -> TabViewData(
                tabData = tabData,
                text = R.string.title_direct_messages,
                icon = R.drawable.ic_reblog_direct_24dp,
                fragment = { ConversationsFragment.newInstance() },
            )
            TabData.TrendingTags -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_trending_hashtags,
                icon = R.drawable.ic_trending_up_24px,
                fragment = { TrendingTagsFragment.newInstance() },
            )
            TabData.TrendingLinks -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_trending_links,
                icon = R.drawable.ic_trending_up_24px,
                fragment = { TrendingLinksFragment.newInstance() },
            )
            TabData.TrendingStatuses -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_trending_statuses,
                icon = R.drawable.ic_trending_up_24px,
                fragment = { TimelineFragment.newInstance(TimelineKind.TrendingStatuses) },
            )
            is TabData.Hashtag -> TabViewData(
                tabData = tabData,
                text = R.string.hashtags,
                icon = R.drawable.ic_hashtag,
                fragment = { TimelineFragment.newInstance(TimelineKind.Tag(tabData.tags)) },
                title = { context ->
                    tabData.tags.joinToString(separator = " ") {
                        context.getString(
                            R.string.title_tag,
                            it,
                        )
                    }
                },
            )
            is TabData.UserList -> TabViewData(
                tabData = tabData,
                text = R.string.list,
                icon = R.drawable.ic_list,
                fragment = {
                    TimelineFragment.newInstance(
                        TimelineKind.UserList(tabData.listId, tabData.title),
                    )
                },
                title = { tabData.title },
            )
            TabData.Bookmarks -> TabViewData(
                tabData = tabData,
                text = R.string.title_bookmarks,
                icon = R.drawable.ic_bookmark_active_24dp,
                fragment = { TimelineFragment.newInstance(TimelineKind.Bookmarks) },
            )
            else -> throw IllegalArgumentException("unknown tab type: $tabData")
        }
    }
}
