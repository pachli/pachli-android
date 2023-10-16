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

package app.pachli.util

data class StatusDisplayOptions(
    @get:JvmName("animateAvatars")
    val animateAvatars: Boolean = false,
    @get:JvmName("mediaPreviewEnabled")
    val mediaPreviewEnabled: Boolean = true,
    @get:JvmName("useAbsoluteTime")
    val useAbsoluteTime: Boolean = false,
    @get:JvmName("showBotOverlay")
    val showBotOverlay: Boolean = true,
    @get:JvmName("useBlurhash")
    val useBlurhash: Boolean = true,
    @get:JvmName("cardViewMode")
    val cardViewMode: CardViewMode = CardViewMode.NONE,
    @get:JvmName("confirmReblogs")
    val confirmReblogs: Boolean = true,
    @get:JvmName("confirmFavourites")
    val confirmFavourites: Boolean = false,
    @get:JvmName("hideStats")
    val hideStats: Boolean = false,
    @get:JvmName("animateEmojis")
    val animateEmojis: Boolean = false,
    @get:JvmName("showStatsInline")
    val showStatsInline: Boolean = false,
    @get:JvmName("showSensitiveMedia")
    val showSensitiveMedia: Boolean = false,
    @get:JvmName("openSpoiler")
    val openSpoiler: Boolean = false,
)
