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

import com.github.michaelbull.result.Err
import java.lang.reflect.Type
import retrofit2.Call
import retrofit2.CallAdapter

/**
 * Retrofit call adapters for non-suspending functions that return
 * `Result<ApiResponse<V>, ApiError>`.
 *
 * @param successType The type of the expected successful result (i.e,.
 *     the `V` in `Result<ApiResponse<V>, ApiError>`)
 */
internal class SyncApiResultCallAdapter<T : Any>(
    private val successType: Type,
) : CallAdapter<T, ApiResult<T>> {
    override fun responseType(): Type = successType

    override fun adapt(call: Call<T>): ApiResult<T> {
        return try {
            ApiResult.from(call.request(), call.execute(), successType)
        } catch (e: Exception) {
            Err(ApiError.from(call.request(), e))
        }
    }
}
