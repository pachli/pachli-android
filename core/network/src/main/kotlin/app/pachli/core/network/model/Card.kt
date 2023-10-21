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

import com.google.gson.annotations.SerializedName

data class Card(
    override val url: String,
    override val title: String,
    override val description: String,
    @SerializedName("type") override val kind: PreviewCardKind,
    @SerializedName("author_name") override val authorName: String,
    @SerializedName("author_url") override val authorUrl: String,
    @SerializedName("provider_name") override val providerName: String,
    @SerializedName("provider_url") override val providerUrl: String,
    override val html: String,
    override val width: Int,
    override val height: Int,
    override val image: String? = null,
    @SerializedName("embed_url") override val embedUrl: String,
    override val blurhash: String? = null,
) : PreviewCard {

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is Card) {
            return false
        }
        val account = other as Card?
        return account?.url == this.url
    }
}
