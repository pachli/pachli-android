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

import app.pachli.core.network.di.MastodonApiModule
import app.pachli.core.network.retrofit.MastodonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.mockito.kotlin.mock
import javax.inject.Singleton

/**
 * Provides an empty mock. Use like:
 *
 * ```kotlin
 * @Inject
 * lateinit var mastodonApi: MastodonApi
 *
 * // ...
 *
 * @Before
 * fun setup() {
 *     hilt.inject()
 *
 *     reset(mastodonApi)
 *     mastodonApi.stub {
 *         onBlocking { someFunction() } doReturn SomeValue
 *         // ...
 *     }
 *
 *     // ...
 * }
 * ```
 */
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [MastodonApiModule::class],
)
@Module
object FakeMastodonApiModule {
    @Provides
    @Singleton
    fun providesApi(): MastodonApi = mock()
}
