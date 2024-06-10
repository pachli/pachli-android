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
sealed interface ApiError {
    /** The underlying [Throwable] that triggered the error. */
    // This has to be Throwable, not Exception, because Retrofit exposes
    // errors as Throwable
    val cause: Throwable

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

    data class Unknown(override val cause: Throwable) : ApiError
}

sealed interface HttpError : ApiError {
    override val cause: HttpException
}

/** 4xx errors */
sealed interface ClientError : HttpError {
    companion object {
        fun from(exception: HttpException): ClientError {
            return when (exception.code()) {
                400 -> BadRequest(exception)
                401 -> Unauthorized(exception)
                404 -> NotFound(exception)
                410 -> Gone(exception)
                else -> UnknownClientError(exception)
            }
        }
    }

    /** 400 Bad request */
    data class BadRequest(override val cause: HttpException) : ClientError

    /** 401 Unauthorized */
    data class Unauthorized(override val cause: HttpException) : ClientError

    /** 404 Not found */
    data class NotFound(override val cause: HttpException) : ClientError

    /** 410 Gone */
    data class Gone(override val cause: HttpException) : ClientError

    /** All other 4xx client errors */
    data class UnknownClientError(override val cause: HttpException) : ClientError
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

    /** 500 Internal error */
    data class Internal(override val cause: HttpException) : ServerError

    /** 501 Not implemented */
    data class NotImplemented(override val cause: HttpException) : ServerError

    /** 502 Bad gateway */
    data class BadGateway(override val cause: HttpException) : ServerError

    /** 503 Service unavailable */
    data class ServiceUnavailable(override val cause: HttpException) : ServerError

    /** All other 5xx server errors */
    data class UnknownServerError(override val cause: HttpException) : ServerError
}
data class JsonParse(override val cause: JsonDataException) : ApiError
sealed interface NetworkError : ApiError
data class IO(override val cause: Exception) : NetworkError

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
