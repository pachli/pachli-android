/*
 * Copyright 2023 Pachli Association
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

package app.pachli.core.network.retrofit

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstanceV1SwitchAuthInterceptorTest {

    private val mockWebServer = MockWebServer()

    @Before
    fun setup() {
        mockWebServer.start()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should make regular request when requested`() {
        mockWebServer.enqueue(MockResponse())

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor())
            .build()

        val request = Request.Builder()
            .get()
            .url(mockWebServer.url("/test"))
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)
    }

    @Test
    fun `should make request to instance requested in special header`() {
        mockWebServer.enqueue(MockResponse())

        val interceptor = InstanceSwitchAuthInterceptor()
        interceptor.credentials = InstanceSwitchAuthInterceptor.Credentials(
            accessToken = "fakeToken",
            domain = "test.domain",
        )

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + ":" + mockWebServer.port + "/test")
            .header(MastodonApi.DOMAIN_HEADER, mockWebServer.hostName)
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)

        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to current instance when requested and user is logged in`() {
        mockWebServer.enqueue(MockResponse())

        val interceptor = InstanceSwitchAuthInterceptor()
        interceptor.credentials = InstanceSwitchAuthInterceptor.Credentials(
            accessToken = "fakeToken",
            domain = mockWebServer.hostName,
        )

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + ":" + mockWebServer.port + "/test")
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)

        assertEquals("Bearer fakeToken", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should fail to make request when request to current instance is requested but no user is logged in`() {
        mockWebServer.enqueue(MockResponse())

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor())
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + "/test")
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(400, response.code)
        assertEquals(0, mockWebServer.requestCount)
    }
}
