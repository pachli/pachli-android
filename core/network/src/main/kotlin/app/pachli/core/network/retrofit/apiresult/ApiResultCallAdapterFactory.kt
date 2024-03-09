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

import com.github.michaelbull.result.Result
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit

/**
 * Call adapter factory for `Result<ApiResponse<T>, ApiError>` (aliased to `ApiResult<T>`).
 */
class ApiResultCallAdapterFactory internal constructor() : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        // Check the expected return type:
        //
        // - In suspend calls this is a `retrofit2.Call"
        // - In non-suspend calls this is `Result<V, E>`
        val rawReturnType = getRawType(returnType)
        if (rawReturnType != Call::class.java && rawReturnType != Result::class.java) {
            return null
        }

        // The return type must be parameterised with the expected response body type
        check(returnType is ParameterizedType) {
            "return type must be parameterized as Call<ApiResult<Foo>>, Call<ApiResult<out Foo>>, " +
                "ApiResult<Foo> or ApiResult<out Foo>"
        }

        /**
         * The type of the entire response, as seen by Retrofit. Expected to
         * be `Result<ApiResponse<T>, ApiError>`.
         */
        val responseType = getParameterUpperBound(0, returnType)
        check(responseType is ParameterizedType) {
            "Response must be parameterized as ApiResult<Foo> or ApiResult<out Foo>"
        }

        // If rawReturnType is `Result` then this is a non-suspending call (synchronous)
        // so delegate to SyncApiResultCallAdapter
        if (rawReturnType == Result::class.java) {
            val successBodyType = getParameterUpperBound(0, responseType)
            return SyncApiResultCallAdapter<Any>(successBodyType)
        }

        // If the response type is not Result then we can't handle this type
        if (getRawType(responseType) != Result::class.java) return null

        // Ensure the V in Result<V, E> is ApiResult<T>
        val successType = getParameterUpperBound(0, responseType)
        check(successType is ParameterizedType) { "Success type must be parameterized" }
        if (getRawType(successType) != ApiResponse::class.java) return null

        // Fetch the type T from ApiResult<T>
        val successBodyType = getParameterUpperBound(0, successType)

        return ApiResultCallAdapter<Any>(successBodyType)
    }

    companion object {
        @JvmStatic
        fun create(): ApiResultCallAdapterFactory = ApiResultCallAdapterFactory()
    }
}
