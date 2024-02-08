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

package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class PreviewCardKind {
    @Json(name = "link")
    LINK,

    @Json(name = "photo")
    PHOTO,

    @Json(name = "video")
    VIDEO,

    @Json(name = "rich")
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
    val authorName: String? // Not supposed to be null, per API, but seen null in the wild
    val authorUrl: String
    val providerName: String
    val providerUrl: String
    val html: String
    val width: Int
    val height: Int
    val image: String?
    val embedUrl: String
    val blurhash: String?
}

@JsonClass(generateAdapter = true)
data class LinkHistory(
    val day: String,
    val accounts: Int,
    val uses: Int,
)

/** Represents a https://docs.joinmastodon.org/entities/PreviewCard/#trends-link */
@JsonClass(generateAdapter = true)
data class TrendsLink(
    override val url: String,
    override val title: String,
    override val description: String,
    @Json(name = "type") override val kind: PreviewCardKind,
    @Json(name = "author_name") override val authorName: String,
    @Json(name = "author_url") override val authorUrl: String,
    @Json(name = "provider_name") override val providerName: String,
    @Json(name = "provider_url") override val providerUrl: String,
    override val html: String,
    override val width: Int,
    override val height: Int,
    override val image: String? = null,
    @Json(name = "embed_url") override val embedUrl: String,
    override val blurhash: String? = null,
    val history: List<LinkHistory>,
) : PreviewCard
