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

package app.pachli.core.data.repository.filtersRepository

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.data.repository.HiltTestApplication_Application
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.network.Server
import app.pachli.core.network.ServerKind
import app.pachli.core.network.ServerOperation
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.testing.rules.MainCoroutineRule
import com.github.michaelbull.result.Ok
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

open class PachliHiltApplication : Application()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
abstract class BaseContentFiltersRepositoryTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var mastodonApi: MastodonApi

    protected lateinit var contentFiltersRepository: ContentFiltersRepository

    val serverFlow = MutableStateFlow(Ok(SERVER_V2))

    private val serverRepository: ServerRepository = mock {
        whenever(it.flow).thenReturn(serverFlow)
    }

    @Before
    fun setup() {
        hilt.inject()

        reset(mastodonApi)

        contentFiltersRepository = ContentFiltersRepository(
            TestScope(),
            mastodonApi,
            serverRepository,
        )
    }

    companion object {
        val SERVER_V2 = Server(
            kind = ServerKind.MASTODON,
            version = Version(4, 2, 0),
            capabilities = mapOf(
                Pair(ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER, "1.0.0".toVersion(true)),
            ),
        )
        val SERVER_V1 = Server(
            kind = ServerKind.MASTODON,
            version = Version(4, 2, 0),
            capabilities = mapOf(
                Pair(ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT, "1.1.0".toVersion(true)),
            ),
        )
    }
}
