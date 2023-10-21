package app.pachli.network

import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.network.retrofit.MastodonApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InstanceSwitchAuthInterceptorTest {

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
            domain = "test.domain"
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
            domain = mockWebServer.hostName
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
