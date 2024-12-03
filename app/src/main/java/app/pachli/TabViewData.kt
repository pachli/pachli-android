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
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.Fragment
import app.pachli.components.conversation.ConversationsFragment
import app.pachli.components.notifications.NotificationsFragment
import app.pachli.components.timeline.TimelineFragment
import app.pachli.components.trending.TrendingLinksFragment
import app.pachli.components.trending.TrendingTagsFragment
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.network.model.Status

/**
 * Wrap a [Timeline] with additional information to display a tab with that
 * timeline.
 *
 * @param timeline wrapped [Timeline]
 * @param text text to use for this tab when displayed in lists
 * @param icon icon to use when displaying the tab
 * @param fragment [Fragment] to display the tab's contents
 * @param title title to display in the action bar if this tab is active
 * @param composeIntent intent to launch [app.pachli.components.compose.ComposeActivity] for this timeline, or null
 *     if composing from this timeline is not supported
 */
data class TabViewData(
    val timeline: Timeline,
    @StringRes val text: Int,
    @DrawableRes val icon: Int,
    val fragment: () -> Fragment,
    val title: (Context) -> String = { context -> context.getString(text) },
    val composeIntent: ((Context, Long) -> Intent)? = { context, pachliAccountId -> ComposeActivityIntent(context, pachliAccountId) },
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabViewData

        return timeline == other.timeline
    }

    override fun hashCode() = timeline.hashCode()

    companion object {
        fun from(pachliAccountId: Long, timeline: Timeline) = when (timeline) {
            Timeline.Home -> TabViewData(
                timeline = timeline,
                text = R.string.title_home,
                icon = R.drawable.ic_home_24dp,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            Timeline.Notifications -> TabViewData(
                timeline = timeline,
                text = R.string.title_notifications,
                icon = R.drawable.ic_notifications_24dp,
                fragment = { NotificationsFragment.newInstance(pachliAccountId) },
            )
            Timeline.PublicLocal -> TabViewData(
                timeline = timeline,
                text = R.string.title_public_local,
                icon = R.drawable.ic_local_24dp,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            Timeline.PublicFederated -> TabViewData(
                timeline = timeline,
                text = R.string.title_public_federated,
                icon = R.drawable.ic_public_24dp,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            Timeline.Conversations -> TabViewData(
                timeline = timeline,
                text = R.string.title_direct_messages,
                icon = R.drawable.ic_reblog_direct_24dp,
                fragment = { ConversationsFragment.newInstance(pachliAccountId) },
            ) { context, pachliAccountId ->
                ComposeActivityIntent(
                    context,
                    pachliAccountId,
                    ComposeActivityIntent.ComposeOptions(visibility = Status.Visibility.PRIVATE),
                )
            }
            Timeline.TrendingHashtags -> TabViewData(
                timeline = timeline,
                text = R.string.title_public_trending_hashtags,
                icon = R.drawable.ic_trending_up_24px,
                fragment = { TrendingTagsFragment.newInstance(pachliAccountId) },
                composeIntent = null,
            )
            Timeline.TrendingLinks -> TabViewData(
                timeline = timeline,
                text = R.string.title_public_trending_links,
                icon = R.drawable.ic_newspaper_24,
                fragment = { TrendingLinksFragment.newInstance(pachliAccountId) },
            )
            Timeline.TrendingStatuses -> TabViewData(
                timeline = timeline,
                text = R.string.title_public_trending_statuses,
                icon = R.drawable.ic_whatshot_24,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            is Timeline.Hashtags -> TabViewData(
                timeline = timeline,
                text = R.string.hashtags,
                icon = R.drawable.ic_hashtag,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
                title = { context ->
                    timeline.tags.joinToString(separator = " ") {
                        context.getString(R.string.title_tag, it)
                    }
                },
            ) { context, pachliAccountId ->
                val tag = timeline.tags.first()
                ComposeActivityIntent(
                    context,
                    pachliAccountId,
                    ComposeActivityIntent.ComposeOptions(
                        content = getString(context, R.string.title_tag_with_initial_position).format(tag),
                        initialCursorPosition = ComposeActivityIntent.ComposeOptions.InitialCursorPosition.START,
                    ),
                )
            }

            is Timeline.UserList -> TabViewData(
                timeline = timeline,
                text = R.string.list,
                icon = app.pachli.core.ui.R.drawable.ic_list,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
                title = { timeline.title },
            )
            Timeline.Bookmarks -> TabViewData(
                timeline = timeline,
                text = R.string.title_bookmarks,
                icon = R.drawable.ic_bookmark_active_24dp,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            Timeline.Favourites -> TabViewData(
                timeline = timeline,
                text = R.string.title_favourites,
                icon = R.drawable.ic_favourite_filled_24dp,
                fragment = { TimelineFragment.newInstance(pachliAccountId, timeline) },
            )
            is Timeline.Link -> throw IllegalArgumentException("can't add to tab: $timeline")
            is Timeline.User.Pinned -> throw IllegalArgumentException("can't add to tab: $timeline")
            is Timeline.User.Posts -> throw IllegalArgumentException("can't add to tab: $timeline")
            is Timeline.User.Replies -> throw IllegalArgumentException("can't add to tab: $timeline")
        }
    }
}
