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
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.lang.reflect.Type
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response

/**
 * Result monad modeling a response from the API.
 */
typealias ApiResult<T> = Result<ApiResponse<T>, ApiError>

/**
 * A successful response from an API call.
 *
 * @param headers The HTTP headers from the response
 * @param body The response body, converted to [T]
 */
data class ApiResponse<out T>(
    val headers: Headers,
    val body: T,
)

/**
 * A failed response from an API call.
 */
interface ApiError {
    // This has to be Throwable, not Exception, because Retrofit exposes
    // errors as Throwable
    val throwable: Throwable

    companion object {
        fun from(exception: Throwable): ApiError {
            return when (exception) {
                is HttpException -> when (exception.code()) {
                    in 400..499 -> ClientError.from(exception)
                    in 500..599 -> ServerError.from(exception)
                    else -> Unknown(exception)
                }

                is JsonDataException -> JsonParse(exception)
                is IOException -> IO(exception)
                else -> Unknown(exception)
            }
        }
    }

    data class Unknown(override val throwable: Throwable) : ApiError
}

sealed interface HttpError : ApiError {
    override val throwable: HttpException

    /**
     * The error message for this error, one of (in preference order):
     *
     * - The error body of the response that created this error
     * - The throwable.message
     * - Literal string "Unknown"
     */
    val message
        get() = throwable.response()?.errorBody()?.string() ?: throwable.message() ?: "Unknown"
}

/** 4xx errors */
sealed interface ClientError : HttpError {
    companion object {
        fun from(exception: HttpException): ClientError {
            return when (exception.code()) {
                401 -> Unauthorized(exception)
                404 -> NotFound(exception)
                410 -> Gone(exception)
                else -> UnknownClientError(exception)
            }
        }
    }

    data class Unauthorized(override val throwable: HttpException) : ClientError
    data class NotFound(override val throwable: HttpException) : ClientError
    data class Gone(override val throwable: HttpException) : ClientError
    data class UnknownClientError(override val throwable: HttpException) : ClientError
}

/** 5xx errors */
sealed interface ServerError : HttpError {
    companion object {
        fun from(exception: HttpException): ServerError {
            return when (exception.code()) {
                500 -> Internal(exception)
                501 -> NotImplemented(exception)
                502 -> BadGateway(exception)
                503 -> ServiceUnavailable(exception)
                else -> UnknownServerError(exception)
            }
        }
    }

    data class Internal(override val throwable: HttpException) : ServerError
    data class NotImplemented(override val throwable: HttpException) : ServerError
    data class BadGateway(override val throwable: HttpException) : ServerError
    data class ServiceUnavailable(override val throwable: HttpException) : ServerError
    data class UnknownServerError(override val throwable: HttpException) : ServerError
}
data class JsonParse(override val throwable: JsonDataException) : ApiError
sealed interface NetworkError : ApiError
data class IO(override val throwable: Exception) : NetworkError

/**
 * Creates an [ApiResult] from a [Response].
 */
fun <T> Result.Companion.from(response: Response<T>, successType: Type): ApiResult<T> {
    if (!response.isSuccessful) {
        val err = ApiError.from(HttpException(response))
        return Err(err)
    }

    // Skip body processing for successful responses expecting Unit
    if (successType == Unit::class.java) {
        @Suppress("UNCHECKED_CAST")
        return Ok(ApiResponse(response.headers(), Unit as T))
    }

    response.body()?.let { body ->
        return Ok(ApiResponse(response.headers(), body))
    }

    return Err(ApiError.from(HttpException(response)))
}
