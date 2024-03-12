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

package app.pachli.core.database.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = true, generator = "sealed:kind")
sealed interface TabData {
    @TypeLabel("home")
    data object Home : TabData

    @TypeLabel("notifications")
    data object Notifications : TabData

    @TypeLabel("local")
    data object Local : TabData

    @TypeLabel("federated")
    data object Federated : TabData

    @TypeLabel("direct")
    data object Direct : TabData

    @TypeLabel("trending_tags")
    data object TrendingTags : TabData

    @TypeLabel("trending_links")
    data object TrendingLinks : TabData

    @TypeLabel("trending_statuses")
    data object TrendingStatuses : TabData

    /**
     * @property tags List of one or more hashtags (without the leading '#')
     *     to show in the tab.
     */
    @TypeLabel("hashtag")
    @JsonClass(generateAdapter = true)
    data class Hashtag(val tags: List<String>) : TabData

    @TypeLabel("list")
    @JsonClass(generateAdapter = true)
    data class UserList(val listId: String, val title: String) : TabData

    @TypeLabel("bookmarks")
    data object Bookmarks : TabData
}

fun defaultTabs() = listOf(
    TabData.Home,
    TabData.Notifications,
    TabData.Local,
    TabData.Direct,
)
