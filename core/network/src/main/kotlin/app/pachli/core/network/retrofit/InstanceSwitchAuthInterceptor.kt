/*
 * Copyright 2022 Tusky Contributors
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

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Explain that AccountManager is not used here to avoid a circular dependency,
// instead, AM injects the Singleton instance and sets credentials when the active
// account changes.

@Singleton
class InstanceSwitchAuthInterceptor @Inject constructor() : Interceptor {
    data class Credentials(val accessToken: String, val domain: String)

    var credentials: Credentials? = null

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()

        // only switch domains if the request comes from retrofit
        return if (originalRequest.url.host == MastodonApi.PLACEHOLDER_DOMAIN) {
            val builder: Request.Builder = originalRequest.newBuilder()
            val instanceHeader = originalRequest.header(MastodonApi.DOMAIN_HEADER)

            if (instanceHeader != null) {
                // use domain explicitly specified in custom header
                builder.url(swapHost(originalRequest.url, instanceHeader))
                builder.removeHeader(MastodonApi.DOMAIN_HEADER)
            } else {
                credentials?.let {
                    if (it.accessToken.isNotEmpty()) {
                        builder.url(swapHost(originalRequest.url, it.domain))
                            .header("Authorization", "Bearer %s".format(it.accessToken))
                    }
                }
            }

            val newRequest: Request = builder.build()

            if (MastodonApi.PLACEHOLDER_DOMAIN == newRequest.url.host) {
                Timber.w("no user logged in or no domain header specified - can't make request to " + newRequest.url)
                return Response.Builder()
                    .code(400)
                    .message("Bad Request")
                    .protocol(Protocol.HTTP_2)
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .request(chain.request())
                    .build()
            }

            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }

    companion object {
        private fun swapHost(url: HttpUrl, host: String): HttpUrl {
            return url.newBuilder().host(host).build()
        }
    }
}
