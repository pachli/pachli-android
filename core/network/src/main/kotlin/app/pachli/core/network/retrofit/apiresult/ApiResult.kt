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

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.network.R
import app.pachli.core.network.extensions.getServerErrorMessage
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
 * @param headers HTTP headers from the response
 * @param body Response body, converted to [T]
 * @param code HTTP response code (200, etc)
 */
data class ApiResponse<out T>(
    val headers: Headers,
    val body: T,
    val code: Int,
)

/**
 * A failed response from an API call.
 *
 * @param resourceId String resource ID of the error message.
 * @param throwable The [Throwable] that caused this error. The server
 * message (if it exists), or [Throwable.getLocalizedMessage] will be
 * interpolated in to this string at `%1$s`.
 */
sealed class ApiError(
    @StringRes override val resourceId: Int,
    val throwable: Throwable,
) : PachliError {
    override val formatArgs: Array<out Any>? = (
        throwable.getServerErrorMessage() ?: throwable.localizedMessage?.trim()
        )?.let { arrayOf(it) }
    override val cause: PachliError? = null

    companion object {
        fun from(throwable: Throwable): ApiError {
            return when (throwable) {
                is HttpException -> when (throwable.code()) {
                    in 400..499 -> ClientError.from(throwable)
                    in 500..599 -> ServerError.from(throwable)
                    else -> Unknown(throwable)
                }

                is JsonDataException -> JsonParseError(throwable)
                is IOException -> IoError(throwable)
                else -> Unknown(throwable)
            }
        }
    }

    data class Unknown(val exception: Throwable) : ApiError(
        R.string.error_generic_fmt,
        exception,
    )
}

sealed class HttpError(
    @StringRes override val resourceId: Int,
    open val exception: HttpException,
) : ApiError(resourceId, exception)

/** 4xx errors */
sealed class ClientError(
    @StringRes override val resourceId: Int,
    exception: HttpException,
) : HttpError(resourceId, exception) {
    companion object {
        fun from(exception: HttpException): ClientError {
            return when (exception.code()) {
                400 -> BadRequest(exception)
                401 -> Unauthorized(exception)
                404 -> NotFound(exception)
                410 -> Gone(exception)
                429 -> RateLimit(exception)
                else -> UnknownClientError(exception)
            }
        }
    }

    /** 400 Bad request */
    data class BadRequest(override val exception: HttpException) :
        ClientError(R.string.error_generic_fmt, exception)

    /** 401 Unauthorized */
    data class Unauthorized(override val exception: HttpException) :
        ClientError(R.string.error_generic_fmt, exception)

    /** 404 Not found */
    data class NotFound(override val exception: HttpException) :
        ClientError(R.string.error_404_not_found_fmt, exception)

    /** 410 Gone */
    data class Gone(override val exception: HttpException) :
        ClientError(R.string.error_generic_fmt, exception)

    /** 429 Rate limit */
    data class RateLimit(override val exception: HttpException) :
        ClientError(R.string.error_429_rate_limit_fmt, exception)

    /** All other 4xx client errors */
    data class UnknownClientError(override val exception: HttpException) :
        ClientError(R.string.error_generic_fmt, exception)
}

/** 5xx errors */
sealed class ServerError(
    @StringRes override val resourceId: Int,
    exception: HttpException,
) : HttpError(resourceId, exception) {
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
    data class Internal(override val exception: HttpException) :
        ServerError(R.string.error_generic_fmt, exception)

    /** 501 Not implemented */
    data class NotImplemented(override val exception: HttpException) :
        ServerError(R.string.error_404_not_found_fmt, exception)

    /** 502 Bad gateway */
    data class BadGateway(override val exception: HttpException) :
        ServerError(R.string.error_generic_fmt, exception)

    /** 503 Service unavailable */
    data class ServiceUnavailable(override val exception: HttpException) :
        ServerError(R.string.error_generic_fmt, exception)

    /** All other 5xx server errors */
    data class UnknownServerError(override val exception: HttpException) :
        ServerError(R.string.error_generic_fmt, exception)
}

/**
 * The server sent a response without a content type. Note that the underlying
 * response in [exception] may be a success, as the server may have sent a 2xx
 * without a content-type.
 */
data class MissingContentType(val exception: HttpException) :
    ApiError(R.string.error_missing_content_type_fmt, exception)

/**
 * The server sent a response with the wrong content type (not "application/json")
 * Note that the underlying response in [exception] may be a success, as the server
 * may have sent a 2xx with the wrong content-type.
 */
data class WrongContentType(val contentType: String, val exception: HttpException) :
    ApiError(R.string.error_wrong_content_type_fmt, exception) {
    override val formatArgs: Array<out Any>
        get() = super.formatArgs?.let {
            arrayOf(contentType, *it)
        } ?: arrayOf(contentType)
}

data class JsonParseError(val exception: JsonDataException) :
    ApiError(R.string.error_json_data_fmt, exception)

data class IoError(val exception: IOException) :
    ApiError(R.string.error_network_fmt, exception)

/**
 * Creates an [ApiResult] from a [Response].
 */
fun <T> Result.Companion.from(response: Response<T>, successType: Type): ApiResult<T> {
    response.headers()["content-type"]?.let { contentType ->
        if (!contentType.startsWith("application/json")) {
            return Err(WrongContentType(contentType, HttpException(response)))
        }
    } ?: return Err(MissingContentType(HttpException(response)))

    if (!response.isSuccessful) {
        val err = ApiError.from(HttpException(response))
        return Err(err)
    }

    // Skip body processing for successful responses expecting Unit
    if (successType == Unit::class.java) {
        @Suppress("UNCHECKED_CAST")
        return Ok(ApiResponse(response.headers(), Unit as T, response.code()))
    }

    response.body()?.let { body ->
        return Ok(ApiResponse(response.headers(), body, response.code()))
    }

    return Err(ApiError.from(HttpException(response)))
}
