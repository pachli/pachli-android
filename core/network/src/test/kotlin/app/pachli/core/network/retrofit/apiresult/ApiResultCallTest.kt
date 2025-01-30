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

import app.pachli.core.testing.jsonError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrapError
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import okhttp3.Headers
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response

class ApiResultCallTest {
    private val backingCall = TestCall<String>()
    private val networkApiResultCall = ApiResultCall(backingCall, String::class.java)
    private val jsonHeaders = Headers.Builder()
        .add("content-type: application/json")
        .build()

    @Test
    fun `should throw an error when invoking 'execute'`() {
        assertThrows(UnsupportedOperationException::class.java) {
            networkApiResultCall.execute()
        }
    }

    @Test
    fun `should delegate properties to backing call`() {
        with(networkApiResultCall) {
            assertThat(isExecuted).isEqualTo(backingCall.isExecuted)
            assertThat(isCanceled).isEqualTo(backingCall.isCanceled)
            assertThat(request()).isEqualTo(backingCall.request())
        }
    }

    @Test
    fun `should return new instance when cloned`() {
        val clonedCall = networkApiResultCall.clone()
        assert(clonedCall !== networkApiResultCall)
    }

    @Test
    fun `should cancel backing call as well when cancelled`() {
        networkApiResultCall.cancel()
        assert(backingCall.isCanceled)
    }

    @Test
    fun `should parse successful call as ApiResult-success`() {
        val okResponse = Response.success("Test body", jsonHeaders)

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    assertThat(response.isSuccessful).isTrue()
                    assertThat(response.body()).isEqualTo(
                        ApiResult.from(
                            Request.Builder()
                                .url("https://example.com/")
                                .build(),
                            okResponse,
                            String::class.java,
                        ),
                    )
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )
        backingCall.complete(okResponse)
    }

    @Test
    fun `should require content-type on successful results`() {
        // Test "should parse successful call as ApiResult-success" tested responses with
        // the correct content-type. This test ensures the content-type is required.

        // Given - response that has no content-type
        val okResponse = Response.success("Test body")

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.unwrapError()
                    assertThat(error).isInstanceOf(MissingContentType::class.java)
                    assertThat(response.isSuccessful).isTrue()
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )
        backingCall.complete(okResponse)
    }

    @Test
    fun `should require application-slash-json content-type on successful results`() {
        // Test "should parse successful call as ApiResult-success" tested responses with
        // the correct content-type. This test ensures the content-type is required,
        // and is set to "application/json". If it's not set then the

        // Given - response that has no content-type
        val okResponse = Response.success(
            "Test body",
            Headers.Builder().add("content-type: text/html").build(),
        )

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.unwrapError()
                    assertThat(error).isInstanceOf(WrongContentType::class.java)
                    assertThat((error as WrongContentType).contentType).isEqualTo("text/html")
                    assertThat(response.isSuccessful).isTrue()
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )
        backingCall.complete(okResponse)
    }

    // If the JSON body does not parse as an object with `error` and optional `description`
    // properties then the error message should fall back to the HTTP error message.
    @Test
    fun `should parse call with 404 error code as ApiResult-failure (no JSON)`() {
        val errorResponse = jsonError(404, "")

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.unwrapError()
                    assertThat(error).isInstanceOf(ClientError.NotFound::class.java)

                    val exception = (error as ClientError.NotFound).exception
                    assertThat(exception).isInstanceOf(HttpException::class.java)
                    assertThat(exception.code()).isEqualTo(404)
                    assertThat(error.formatArgs).isEqualTo(arrayOf("HTTP 404 Not Found: GET /foo?x=1"))
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )

        backingCall.complete(errorResponse)
    }

    // If the JSON body *does* parse as an object with an `error` property that should be used
    // as the user visible error message.
    @Test
    fun `should parse call with 404 error code as ApiResult-failure (JSON error message)`() {
        val errorMsg = "JSON error message"
        val errorResponse = jsonError(404, "{\"error\": \"$errorMsg\"}")

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.unwrapError()
                    assertThat(error).isInstanceOf(ClientError.NotFound::class.java)

                    val exception = (error as ClientError.NotFound).exception
                    assertThat(exception).isInstanceOf(HttpException::class.java)
                    assertThat(exception.code()).isEqualTo(404)
                    assertThat(error.formatArgs).isEqualTo(arrayOf("$errorMsg: GET /foo?x=1"))
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )

        backingCall.complete(errorResponse)
    }

    // If the JSON body *does* parse as an object with an `error` property that should be used
    // as the user visible error message.
    @Test
    fun `should parse call with 404 error code as ApiResult-failure (JSON error and description message)`() {
        val errorMsg = "JSON error message"
        val descriptionMsg = "JSON error description"
        val errorResponse = jsonError(404, "{\"error\": \"$errorMsg\", \"description\": \"$descriptionMsg\"}")

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.unwrapError()
                    assertThat(error).isInstanceOf(ClientError.NotFound::class.java)

                    val exception = (error as ClientError.NotFound).exception
                    assertThat(exception).isInstanceOf(HttpException::class.java)
                    assertThat(exception.code()).isEqualTo(404)
                    assertThat(error.formatArgs).isEqualTo(arrayOf("$errorMsg: $descriptionMsg: GET /foo?x=1"))
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )

        backingCall.complete(errorResponse)
    }

    @Test
    fun `should parse call with IOException as ApiResult-failure`() {
        val exception = IOException()

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val err = response.body() as? Err<ApiError>
                    assertThat(err).isInstanceOf(Err::class.java)
                    assertThat(err?.error?.request).isEqualTo(call.request())
                    assertThat(err?.error?.throwable).isEqualTo(exception)
                    assertThat(err?.error?.formatArgs).isEqualTo(arrayOf("GET /foo?x=1"))
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )

        backingCall.completeWithException(exception)
    }
}
