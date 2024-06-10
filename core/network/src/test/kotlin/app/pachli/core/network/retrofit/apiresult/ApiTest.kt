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

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiTest {
    private var mockWebServer = MockWebServer()

    private lateinit var api: TestApi

    @Before
    fun setup() {
        mockWebServer.start()

        val moshi = Moshi.Builder().build()

        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addCallAdapterFactory(ApiResultCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(OkHttpClient())
            .build()
            .create(TestApi::class.java)
    }

    @After
    fun shutdown() {
        mockWebServer.shutdown()
    }

    private fun mockResponse(responseCode: Int, body: String = "") = MockResponse()
        .setResponseCode(responseCode)
        .setBody(body)

    @Test
    fun `suspending call - should return the correct test object`() = runTest {
        val response =
            mockResponse(
                200,
                """
                {
                    "url": "https://pachli.app",
                    "title": "Pachli"
                }
                """.trimIndent(),
            )

        mockWebServer.enqueue(response)
        val responseObject = api.getSiteAsync().get()?.body

        assertThat(responseObject).isEqualTo(Site("https://pachli.app", "Pachli"))
    }

    @Test
    fun `blocking call - should return the correct test object`() {
        val response =
            mockResponse(
                200,
                """
                {
                    "url": "https://pachli.app",
                    "title": "Pachli"
                }
                """.trimIndent(),
            )

        mockWebServer.enqueue(response)
        val responseObject = api.getSiteSync().get()?.body

        assertThat(responseObject).isEqualTo(Site("https://pachli.app", "Pachli"))
    }

    @Test
    fun `suspending call - returns complex objects`() = runTest {
        val response = mockResponse(
            200,
            """
                [
                    {
                        "url": "https://pachli.app",
                        "title": "Pachli"
                    },
                    {
                        "url": "https://github.com/pachli/pachli-android",
                        "title": "GitHub - Pachli"
                    }
                ]
            """.trimIndent(),
        )

        mockWebServer.enqueue(response)
        val responseObject = api.getSitesAsync().get()?.body

        assertThat(responseObject).isEqualTo(
            listOf(
                Site("https://pachli.app", "Pachli"),
                Site("https://github.com/pachli/pachli-android", "GitHub - Pachli"),
            ),
        )
    }

    @Test
    fun `suspending call - should return an Internal error when the server returns error 500`() = runTest {
        val errorCode = 500
        val response = mockResponse(errorCode)

        mockWebServer.enqueue(response)
        val responseObject = api.getSiteAsync().getError()

        val error = responseObject as? ServerError.Internal
        assertThat(error).isInstanceOf(ServerError.Internal::class.java)
        assertThat(error?.cause?.code()).isEqualTo(500)
        assertThat(error?.cause?.message()).isEqualTo("Server Error")
    }

    @Test
    fun `blocking call - should return an Internal error when the server returns error 500`() {
        val errorCode = 500
        val response = mockResponse(errorCode)

        mockWebServer.enqueue(response)

        val responseObject = api.getSiteSync().getError()

        val error = responseObject as? ServerError.Internal
        assertThat(error).isInstanceOf(ServerError.Internal::class.java)
        assertThat(error?.cause?.code()).isEqualTo(500)
        assertThat(error?.cause?.message()).isEqualTo("Server Error")
    }

    @Test
    fun `suspending call - should return an IO error when the network fails`() {
        mockWebServer.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST })
        val responseObject =
            runBlocking {
                api.getSiteAsync()
            }

        val error = responseObject.getError() as? IO

        assertThat(error).isInstanceOf(IO::class.java)
    }

    @Test
    fun `blocking call - should return an IO error when the network fails`() {
        mockWebServer.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST })
        val responseObject = runBlocking { api.getSiteSync() }

        val error = responseObject.getError() as? IO

        assertThat(error).isInstanceOf(IO::class.java)
    }

    @Test
    fun `suspending call - should return a JsonParse error on incorrect JSON data`() {
        val response = mockResponse(200, """{"wrong": "shape"}""")

        mockWebServer.enqueue(response)
        val responseObject = api.getSitesAsync().getError()

        val error = responseObject as? JsonParse

        assertThat(error).isInstanceOf(JsonParse::class.java)
    }

    @Test
    fun `suspending call - should return an IO(JsonEncodingException) error on invalid JSON`() {
        val response = mockResponse(200, "not even JSON")
        mockWebServer.enqueue(response)

        val responseObject = api.getSitesAsync().getError()

        val error = responseObject as? IO

        // Moshi reports invalid JSON as an IoException wrapping a JsonEncodingException
        assertThat(error).isInstanceOf(IO::class.java)
        assertThat(error?.cause).isInstanceOf(JsonEncodingException::class.java)
    }

    @Test
    fun `suspending call - should pass through non-Result types`() = runTest {
        val response = mockResponse(200)
        mockWebServer.enqueue(response)

        val responseObject = api.getResponseAsync()

        assertThat(responseObject.code()).isEqualTo(200)
        assertThat(responseObject.body()).isInstanceOf(Unit::class.java)
    }

    @Test
    fun `blocking call - should pass through non-Result types`() {
        val response = mockResponse(200)
        mockWebServer.enqueue(response)

        val responseObject = api.getResponseSync().execute()

        assertThat(responseObject.code()).isEqualTo(200)
        assertThat(responseObject.body()).isInstanceOf(Unit::class.java)
    }
}
