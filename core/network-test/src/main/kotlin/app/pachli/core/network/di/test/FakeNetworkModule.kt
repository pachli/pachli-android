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

package app.pachli.core.network.di.test

import app.pachli.core.model.VersionAdapter
import app.pachli.core.network.di.NetworkModule
import app.pachli.core.network.json.BooleanIfNull
import app.pachli.core.network.json.DefaultIfNull
import app.pachli.core.network.json.Guarded
import app.pachli.core.network.json.InstantJsonAdapter
import app.pachli.core.network.json.LenientRfc3339DateJsonAdapter
import app.pachli.core.network.model.MediaUploadApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant
import java.util.Date
import javax.inject.Singleton
import okhttp3.OkHttpClient
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
        .add(VersionAdapter())
        .add(Date::class.java, LenientRfc3339DateJsonAdapter())
        .add(Instant::class.java, InstantJsonAdapter())
        .add(Guarded.Factory())
        .add(DefaultIfNull.Factory())
        .add(BooleanIfNull.Factory())
        .build()

    @Provides
    @Singleton
    fun providesHttpClient(): OkHttpClient = mock()

    @Provides
    @Singleton
    fun providesMediaUploadApi(): MediaUploadApi = mock()
}
