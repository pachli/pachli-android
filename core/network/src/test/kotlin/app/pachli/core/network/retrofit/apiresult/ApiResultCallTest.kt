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
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response

class ApiResultCallTest {
    private val backingCall = TestCall<String>()
    private val networkApiResultCall = ApiResultCall(backingCall, String::class.java)

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
        val okResponse = Response.success("Test body")

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    assertThat(response.isSuccessful).isTrue()
                    assertThat(response.body()).isEqualTo(ApiResult.from(okResponse, String::class.java))
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )
        backingCall.complete(okResponse)
    }

    @Test
    fun `should parse call with 404 error code as ApiResult-failure`() {
        val errorResponse = Response.error<String>(404, "not found".toResponseBody())

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    val error = response.body()?.getError() as? ClientError.NotFound
                    assertThat(error).isInstanceOf(ClientError.NotFound::class.java)
                    assertThat(error?.message).isEqualTo("not found")

                    val throwable = error?.throwable as? HttpException
                    assertThat(throwable).isInstanceOf(HttpException::class.java)
                    assertThat(throwable?.code()).isEqualTo(404)
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
        val error = Err(IO(IOException()))

        networkApiResultCall.enqueue(
            object : Callback<ApiResult<String>> {
                override fun onResponse(call: Call<ApiResult<String>>, response: Response<ApiResult<String>>) {
                    assertThat(response.body()).isEqualTo(error)
                }

                override fun onFailure(call: Call<ApiResult<String>>, t: Throwable) {
                    throw IllegalStateException()
                }
            },
        )

        backingCall.completeWithException(error.error.throwable)
    }
}
