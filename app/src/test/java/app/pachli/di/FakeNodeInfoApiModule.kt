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

package app.pachli.di

import app.pachli.core.network.di.NodeInfoApiModule
import app.pachli.core.network.retrofit.NodeInfoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import org.mockito.kotlin.mock

/**
 * Provides an empty mock. Use like:
 *
 * ```kotlin
 * @Inject
 * lateinit var nodeInfoApi: NodeInfoApi
 *
 * // ...
 *
 * @Before
 * fun setup() {
 *     hilt.inject()
 *
 *     reset(nodeInfoApi)
 *     nodeInfoApi.stub {
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
    replaces = [NodeInfoApiModule::class],
)
@Module
object FakeNodeInfoApiModule {
    @Provides
    @Singleton
    fun providesApi(): NodeInfoApi = mock()
}
