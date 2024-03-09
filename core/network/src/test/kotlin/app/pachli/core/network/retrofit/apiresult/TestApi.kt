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

package app.pachli.core.network.retrofit.apiresult

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

/**
 * Sample data class to use while testing. Models a database of
 * sites, with a URL and a title.
 */
@JsonClass(generateAdapter = true)
data class Site(
    val url: String,
    val title: String,
)

interface TestApi {
    @GET("/site")
    suspend fun getSiteAsync(): ApiResult<Site>

    @GET("/site")
    fun getSiteSync(): ApiResult<Site>

    @GET("/sites")
    fun getSitesAsync(): ApiResult<List<Site>>
}
