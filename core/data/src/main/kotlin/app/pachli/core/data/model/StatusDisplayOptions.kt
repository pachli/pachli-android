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

package app.pachli.core.data.model

import app.pachli.core.preferences.CardViewMode
import app.pachli.core.preferences.PronounDisplay

/**
 * @property mediaPreviewEnabled See [app.pachli.core.database.model.AccountEntity.mediaPreviewEnabled].
 * @property showStatsInline If true, statuses in timelines show the counts
 * of replies, reblogs, and favourites.
 *
 * If false, the counts of reblogs and favourites are hidden, and the
 *  count of replies is set to either "0", "1", or "1+" so the user can
 *  tell if there are replies if they click through.
 * @property canQuote True if the server supports sending quote posts.
 * @property pronounDisplay How to display account pronouns.
 */
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
    val hideStatsInDetailedView: Boolean = false,
    @get:JvmName("animateEmojis")
    val animateEmojis: Boolean = false,
    /**
     * If true, statuses in timelines show the counts of replies, reblogs,
     * and favourites.
     *
     * If false, the counts of reblogs and favourites are hidden, and the
     * count of replies is set to either "0", "1", or "1+" so the user can
     * tell if there are replies if they click through.
     */
    @get:JvmName("showStatsInline")
    val showStatsInline: Boolean = false,
    @get:JvmName("showSensitiveMedia")
    val showSensitiveMedia: Boolean = false,
    @get:JvmName("openSpoiler")
    val openSpoiler: Boolean = false,
    @get:JvmName("canTranslate")
    val canTranslate: Boolean = false,
    val canQuote: Boolean = false,
    val renderMarkdown: Boolean = false,

    /**
     * True if the "status info" content should be shown (whether the
     * status is a reblog, reply, etc).
     */
    val showStatusInfo: Boolean = true,

    val pronounDisplay: PronounDisplay = PronounDisplay.WHEN_COMPOSING,
)
