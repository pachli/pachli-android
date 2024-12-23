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

package app.pachli.core.testing

import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Returns an [Ok][Ok] [ApiResult&lt;T>][ApiResult] wrapping [data].
 *
 * @param data Data to wrap in the result.
 * @param code HTTP response code.
 * @param headers Optional additional headers to include in the response. See
 * [Headers.headersOf].
 */
fun <T> success(data: T, code: Int = 200, vararg headers: String): ApiResult<T> =
    Ok(ApiResponse(Headers.headersOf(*headers), data, code))

/**
 * Returns an [Err][Err] [ApiResult&lt;T>][ApiResult] representing an HTTP request
 * failure.
 *
 * @param code HTTP failure code.
 * @param responseBody Data to use as the HTTP response body.
 * @param message (optional) String to use as the HTTP status message.
 * @param method (optional) String to use as the request method
 * @param url (optional) String to use as the request URL
 * @param requestBody (optional) String to use as the request body
 */
fun <T> failure(
    code: Int = 404,
    responseBody: String = "",
    message: String = code.httpStatusMessage(),
    method: String = "GET",
    url: String = "https://example.com",
    requestBody: String? = null,
): ApiResult<T> {
    return Err(
        ApiError.from(
            Request.Builder()
                .method(method, requestBody?.toRequestBody())
                .url(url)
                .build(),
            HttpException(jsonError(code, responseBody, message)),
        ),
    )
}

/**
 * Equivalent to [Response.error] with the content-type set to `application/json`.
 *
 * @param code HTTP failure code.
 * @param body Data to use as the HTTP response body. Should be JSON (unless you are
 * testing the ability to handle invalid JSON).
 * @param message (optional) String to use as the HTTP status message.
 */
fun jsonError(code: Int, body: String, message: String = code.httpStatusMessage()): Response<String> = Response.error(
    body.toResponseBody(),
    okhttp3.Response.Builder()
        .request(Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .addHeader("content-type", "application/json")
        .code(code)
        .message(message)
        .build(),
)

/** Default HTTP status messages for different response codes. */
private fun Int.httpStatusMessage() = when (this) {
    100 -> "Continue"
    101 -> "Switching Protocols"
    103 -> "Early Hints"
    200 -> "OK"
    201 -> "Created"
    202 -> "Accepted"
    203 -> "Non-Authoritative Information"
    204 -> "No Content"
    205 -> "Reset Content"
    206 -> "Partial Content"
    300 -> "Multiple Choices"
    301 -> "Moved Permanently"
    302 -> "Found"
    303 -> "See Other"
    304 -> "Not Modified"
    307 -> "Temporary Redirect"
    308 -> "Permanent Redirect"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    402 -> "Payment Required"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    406 -> "Not Acceptable"
    407 -> "Proxy Authentication Required"
    408 -> "Request Timeout"
    409 -> "Conflict"
    410 -> "Gone"
    411 -> "Length Required"
    412 -> "Precondition Failed"
    413 -> "Request Too Large"
    414 -> "Request-URI Too Long"
    415 -> "Unsupported Media Type"
    416 -> "Range Not Satisfiable"
    417 -> "Expectation Failed"
    500 -> "Internal Server Error"
    501 -> "Not Implemented"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    505 -> "HTTP Version Not Supported"
    511 -> "Network Authentication Required"
    else -> "Unknown"
}
