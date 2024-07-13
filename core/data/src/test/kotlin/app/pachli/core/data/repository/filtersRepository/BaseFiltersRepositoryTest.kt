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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.accounts.AccountManager
import app.pachli.core.data.repository.FiltersRepository
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.network.Server
import app.pachli.core.network.ServerKind
import app.pachli.core.network.ServerOperation
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import app.pachli.core.network.retrofit.apiresult.ApiResult
import app.pachli.core.testing.rules.MainCoroutineRule
import at.connyduck.calladapter.networkresult.NetworkResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
abstract class BaseFiltersRepositoryTest {
    @get:Rule(order = 0)
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var accountManager: AccountManager

    protected lateinit var mastodonApi: MastodonApi

    private lateinit var nodeInfoApi: NodeInfoApi

    protected lateinit var filtersRepository: FiltersRepository

    val serverFlow = MutableStateFlow(Ok(SERVER_V2))

    @Before
    fun setup() {
        mastodonApi = mock {
            onBlocking { getInstanceV2() } doAnswer { null }
            onBlocking { getInstanceV1() } doAnswer { null }
        }

        nodeInfoApi = mock {
            onBlocking { nodeInfoJrd() } doReturn NetworkResult.success(
                UnvalidatedJrd(
                    listOf(
                        UnvalidatedJrd.Link(
                            "http://nodeinfo.diaspora.software/ns/schema/2.1",
                            "https://example.com",
                        ),
                    ),
                ),
            )
            onBlocking { nodeInfo(any()) } doReturn NetworkResult.success(
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "4.2.0")),
            )
        }

        val serverRepository: ServerRepository = mock {
            whenever(it.flow).thenReturn(serverFlow)
        }

        filtersRepository = FiltersRepository(
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

// TODO: These should get copied to core.testing, and used everywhere else
// that needs this.
//
// Or, put them in ApiResult extensions.

fun <T> success(data: T): ApiResult<T> = Ok(ApiResponse(Headers.headersOf(), data, 200))
fun <T> failure(code: Int = 404, body: String): ApiResult<T> =
    Err(
        ApiError.from(
            HttpException(
                Response.error<String>(
                    body.toResponseBody(),
                    okhttp3.Response.Builder()
                        .request(okhttp3.Request.Builder().url("http://localhost/").build())
                        .protocol(Protocol.HTTP_1_1)
                        .addHeader("content-type", "application/json")
                        .code(code)
                        .message(body)
                        .build(),
                ),
            ),
        ),
    )

fun <T> T.ok() = Ok(this)
