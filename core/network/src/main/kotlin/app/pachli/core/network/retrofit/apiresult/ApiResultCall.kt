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
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal class ApiResultCall<T : Any>(
    private val delegate: Call<T>,
    private val successType: Type,
) : Call<ApiResult<T>> {
    override fun enqueue(callback: Callback<ApiResult<T>>) = delegate.enqueue(
        object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                callback.onResponse(
                    this@ApiResultCall,
                    Response.success(ApiResult.from(call.request(), response, successType)),
                )
            }

            override fun onFailure(call: Call<T>, throwable: Throwable) {
                val err: ApiResult<T> = Err(ApiError.from(call.request(), throwable))

                callback.onResponse(this@ApiResultCall, Response.success(err))
            }
        },
    )

    override fun isExecuted() = delegate.isExecuted

    override fun clone() = ApiResultCall(delegate.clone(), successType)

    override fun isCanceled() = delegate.isCanceled

    override fun cancel() = delegate.cancel()

    override fun execute(): Response<ApiResult<T>> {
        throw UnsupportedOperationException("ApiResultCall doesn't support synchronized execution")
    }

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()
}
