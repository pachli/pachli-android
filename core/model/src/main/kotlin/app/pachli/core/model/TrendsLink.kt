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

package app.pachli.core.model

import com.squareup.moshi.JsonClass

enum class PreviewCardKind {
    UNKNOWN,
    LINK,
    PHOTO,
    VIDEO,
    RICH,
}

/**
 * Representation of a Mastodon [PreviewCard](https://docs.joinmastodon.org/entities/PreviewCard/)
 */
interface PreviewCard {
    val url: String
    val title: String
    val description: String
    val kind: PreviewCardKind
    val authorName: String
    val authorUrl: String
    val providerName: String
    val providerUrl: String
    val html: String
    val width: Int
    val height: Int
    val image: String?
    val embedUrl: String
    val blurhash: String?
    val authors: List<PreviewCardAuthor>?
}

/**
 * An author of a link in a [PreviewCard].
 *
 * @property name Author's name, equivalent to [PreviewCard.authorName]
 * @property url Author's URL, equivalent to [PreviewCard.authorUrl]
 * @property account Author's account information, may be null if the link target
 * did not include metadata about the author's account.
 */
@JsonClass(generateAdapter = true)
data class PreviewCardAuthor(
    val name: String,
    val url: String,
    val account: TimelineAccount? = null,
)

data class LinkHistory(
    val day: String,
    val accounts: Int,
    val uses: Int,
)

/** Represents a https://docs.joinmastodon.org/entities/PreviewCard/#trends-link */
data class TrendsLink(
    override val url: String,
    override val title: String,
    override val description: String,
    override val kind: PreviewCardKind,
    override val authorName: String = "",
    override val authorUrl: String,
    override val providerName: String,
    override val providerUrl: String,
    override val html: String,
    override val width: Int,
    override val height: Int,
    override val image: String? = null,
    override val embedUrl: String,
    override val blurhash: String? = null,
    override val authors: List<PreviewCardAuthor>? = null,
    val history: List<LinkHistory>,
) : PreviewCard
