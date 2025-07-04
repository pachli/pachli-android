/*
 * Copyright 2018 charlag
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

package app.pachli.core.network.di

import android.content.Context
import android.os.Build
import app.pachli.core.common.util.versionName
import app.pachli.core.model.VersionAdapter
import app.pachli.core.network.BuildConfig
import app.pachli.core.network.json.BooleanIfNull
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.EnumConstantConverterFactory
import app.pachli.core.network.json.Guarded
import app.pachli.core.network.json.HasDefault
import app.pachli.core.network.json.InstantJsonAdapter
import app.pachli.core.network.json.LenientRfc3339DateJsonAdapter
import app.pachli.core.network.json.UriAdapter
import app.pachli.core.network.model.MediaUploadApi
import app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NewContentFilterConverterFactory
import app.pachli.core.network.retrofit.apiresult.ApiResultCallAdapterFactory
import app.pachli.core.network.util.localHandshakeCertificates
import app.pachli.core.preferences.ProxyConfiguration
import app.pachli.core.preferences.SharedPreferencesRepository
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import timber.log.Timber

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {

    @Provides
    @Singleton
    fun providesMoshi(): Moshi = Moshi.Builder()
        .add(Date::class.java, LenientRfc3339DateJsonAdapter())
        .add(Instant::class.java, InstantJsonAdapter())
        .add(UriAdapter())
        .add(VersionAdapter())
        .add(Guarded.Factory())
        .add(HasDefault.Factory())
        .add(DefaultIfNull.Factory())
        .add(BooleanIfNull.Factory())
        .build()

    @Provides
    @Singleton
    fun providesHttpClient(
        @ApplicationContext context: Context,
        preferences: SharedPreferencesRepository,
        instanceSwitchAuthInterceptor: InstanceSwitchAuthInterceptor,
    ): OkHttpClient {
        val versionName = versionName(context)
        val httpProxyEnabled = preferences.httpProxyEnabled
        val httpServer = preferences.httpProxyServer ?: ""
        val httpPort = preferences.httpProxyPort
        val cacheSize = 25 * 1024 * 1024L // 25 MiB
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                /**
                 * Add a custom User-Agent that contains Pachli, Android and OkHttp Version to all requests
                 * Example:
                 * User-Agent: Pachli/1.1.2 Android/5.0.2 OkHttp/4.9.0
                 * */
                val requestWithUserAgent = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Pachli/$versionName Android/${Build.VERSION.RELEASE} OkHttp/${OkHttp.VERSION}",
                    )
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(context.cacheDir, cacheSize))

        if (httpProxyEnabled) {
            ProxyConfiguration.create(httpServer, httpPort)?.also { conf ->
                val address = InetSocketAddress.createUnresolved(IDN.toASCII(conf.hostname), conf.port)
                builder.proxy(Proxy(Proxy.Type.HTTP, address))
            } ?: Timber.w("Invalid proxy configuration: (%s, %d)", httpServer, httpPort)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // API 23 (Android 7) requires the Let's Encrypt certificates, and does not use
            // network_security_config.xml.
            val handshakeCertificates = localHandshakeCertificates(context)
            builder.sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager)
        }

        return builder
            .apply {
                addInterceptor(instanceSwitchAuthInterceptor)
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(
        httpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder().baseUrl("https://" + MastodonApi.PLACEHOLDER_DOMAIN)
            .client(httpClient)
            .addConverterFactory(EnumConstantConverterFactory)
            .addConverterFactory(NewContentFilterConverterFactory)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(ApiResultCallAdapterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providesMediaUploadApi(retrofit: Retrofit, okHttpClient: OkHttpClient): MediaUploadApi {
        val longTimeOutOkHttpClient = okHttpClient.newBuilder()
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .build()

        return retrofit.newBuilder()
            .client(longTimeOutOkHttpClient)
            .build()
            .create()
    }
}
