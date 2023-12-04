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

package app.pachli.core.mastodon.model

import app.pachli.core.network.model.MediaUploadResult
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** endpoints defined in this interface will be called with a higher timeout than usual
 * which is necessary for media uploads to succeed on some servers
 */
interface MediaUploadApi {
    @Multipart
    @POST("api/v2/media")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part description: MultipartBody.Part? = null,
        @Part focus: MultipartBody.Part? = null,
    ): Response<MediaUploadResult>
}
