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

package app.pachli.updatecheck

import androidx.annotation.Keep
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

@Keep
data class GitHubReleaseAsset(
    /** File name for the asset, e.g., "113.apk" */
    val name: String,

    /** MIME content type for the asset, e.g., "application/vnd.android.package-archive" */
    @SerializedName("content_type") val contentType: String
)

@Keep
data class GitHubRelease(
    /** URL for the release's web page */
    @SerializedName("html_url") val htmlUrl: String,
    val assets: List<GitHubReleaseAsset>
)

interface GitHubService {
    @GET("/repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): NetworkResult<GitHubRelease>
}
