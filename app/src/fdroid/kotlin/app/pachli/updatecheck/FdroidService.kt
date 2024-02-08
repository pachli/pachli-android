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
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@Keep
@JsonClass(generateAdapter = true)
data class FdroidPackageVersion(
    val versionName: String,
    val versionCode: Int,
)

@Keep
@JsonClass(generateAdapter = true)
data class FdroidPackage(
    val packageName: String,
    val suggestedVersionCode: Int,
    val packages: List<FdroidPackageVersion>,
)

interface FdroidService {
    @GET("/api/v1/packages/{package}")
    suspend fun getPackage(
        @Path("package") pkg: String,
    ): NetworkResult<FdroidPackage>
}
