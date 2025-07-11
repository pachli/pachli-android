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

import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@HasDefault
enum class PreviewCardKind {
    @Default
    UNKNOWN,

    @Json(name = "link")
    LINK,

    @Json(name = "photo")
    PHOTO,

    @Json(name = "video")
    VIDEO,

    @Json(name = "rich")
    RICH,

    ;

    fun asModel() = when (this) {
        UNKNOWN -> app.pachli.core.model.PreviewCardKind.UNKNOWN
        LINK -> app.pachli.core.model.PreviewCardKind.LINK
        PHOTO -> app.pachli.core.model.PreviewCardKind.PHOTO
        VIDEO -> app.pachli.core.model.PreviewCardKind.VIDEO
        RICH -> app.pachli.core.model.PreviewCardKind.RICH
    }
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
) {
    fun asModel() = app.pachli.core.model.PreviewCardAuthor(
        name = name,
        url = url,
        account = account?.asModel(),
    )
}

@JvmName("iterablePreviewCardAuthorAsModel")
fun Iterable<PreviewCardAuthor>.asModel() = map { it.asModel() }

@JsonClass(generateAdapter = true)
data class LinkHistory(
    val day: String,
    val accounts: Int,
    val uses: Int,
) {
    fun asModel() = app.pachli.core.model.LinkHistory(
        day = day,
        accounts = accounts,
        uses = uses,
    )
}

@JvmName("iterableLinkHistoryAsModel")
fun Iterable<LinkHistory>.asModel() = map { it.asModel() }

/** Represents a https://docs.joinmastodon.org/entities/PreviewCard/#trends-link */
@JsonClass(generateAdapter = true)
data class TrendsLink(
    override val url: String,
    override val title: String,
    override val description: String,
    @Json(name = "type") override val kind: PreviewCardKind,
    @Json(name = "author_name") override val authorName: String = "",
    @Json(name = "author_url") override val authorUrl: String,
    @Json(name = "provider_name") override val providerName: String,
    @Json(name = "provider_url") override val providerUrl: String,
    override val html: String,
    override val width: Int,
    override val height: Int,
    override val image: String? = null,
    @Json(name = "embed_url") override val embedUrl: String,
    override val blurhash: String? = null,
    override val authors: List<PreviewCardAuthor>? = null,
    val history: List<LinkHistory>,
) : PreviewCard {
    fun asModel() = app.pachli.core.model.TrendsLink(
        url = url,
        title = title,
        description = description,
        kind = kind.asModel(),
        authorName = authorName,
        authorUrl = authorUrl,
        providerName = providerName,
        providerUrl = providerUrl,
        html = html,
        width = width,
        height = height,
        image = image,
        embedUrl = embedUrl,
        blurhash = blurhash,
        authors = authors?.asModel(),
        history = history.asModel(),
    )
}

@JvmName("iterableTrendsLinkAsModel")
fun Iterable<TrendsLink>.asModel() = map { it.asModel() }
