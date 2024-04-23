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

package app.pachli.di

import app.pachli.components.compose.MediaUploader
import app.pachli.core.network.di.NetworkModule
import app.pachli.core.network.json.Guarded
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.Date
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import org.mockito.kotlin.mock

@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class],
)
@Module
object FakeNetworkModule {
    @Provides
    @Singleton
    fun providesMoshi(): Moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(Guarded.Factory())
        .build()

    @Provides
    @Singleton
    fun providesHttpClient(): OkHttpClient = mock()

    @Provides
    @Singleton
    fun providesHandshakeCertificats(): HandshakeCertificates = mock()

    @Provides
    @Singleton
    fun providesMediaUploadApi(): MediaUploader = mock()
}
