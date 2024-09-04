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

import app.cash.turbine.test
import app.pachli.core.data.model.ContentFilter
import app.pachli.core.data.repository.ContentFilterVersion.V1
import app.pachli.core.data.repository.ContentFilterVersion.V2
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.data.repository.ContentFiltersError
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.model.FilterV1 as NetworkFilterV1
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.testing.failure
import app.pachli.core.testing.success
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Date
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@HiltAndroidTest
class ContentFiltersRepositoryTestFlow : BaseContentFiltersRepositoryTest() {
    @Test
    fun `filters flow returns empty list when there are no v2 filters`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn failure(body = "v1 should not be called")
            onBlocking { getContentFilters() } doReturn success(emptyList())
        }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(ContentFilters(version = V2, contentFilters = emptyList()))
        }
    }

    @Test
    fun `filters flow contains initial set of v2 filters`() = runTest {
        val expiresAt = Date()

        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn failure(body = "v1 should not be called")
            onBlocking { getContentFilters() } doReturn success(
                listOf(
                    NetworkFilter(
                        id = "1",
                        title = "test filter",
                        contexts = setOf(FilterContext.HOME),
                        action = Action.WARN,
                        expiresAt = expiresAt,
                        keywords = listOf(FilterKeyword(id = "1", keyword = "foo", wholeWord = true)),
                    ),
                ),
            )
        }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(
                ContentFilters(
                    version = V2,
                    contentFilters = listOf(
                        ContentFilter(
                            id = "1",
                            title = "test filter",
                            contexts = setOf(FilterContext.HOME),
                            action = Action.WARN,
                            expiresAt = expiresAt,
                            keywords = listOf(
                                FilterKeyword(id = "1", keyword = "foo", wholeWord = true),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `filters flow returns empty list when there are no v1 filters`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn failure(body = "v2 should not be called")
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
        }
        serverFlow.update { Ok(SERVER_V1) }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(ContentFilters(version = V1, contentFilters = emptyList()))
        }
    }

    @Test
    fun `filters flow contains initial set of v1 filters`() = runTest {
        val expiresAt = Date()

        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn failure(body = "v2 should not be called")
            onBlocking { getContentFiltersV1() } doReturn success(
                listOf(
                    NetworkFilterV1(
                        id = "1",
                        phrase = "some_phrase",
                        contexts = setOf(FilterContext.HOME),
                        expiresAt = expiresAt,
                        irreversible = true,
                        wholeWord = true,
                    ),
                ),
            )
        }

        serverFlow.update { Ok(SERVER_V1) }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(
                ContentFilters(
                    version = V1,
                    contentFilters = listOf(
                        ContentFilter(
                            id = "1",
                            title = "some_phrase",
                            contexts = setOf(FilterContext.HOME),
                            action = Action.WARN,
                            expiresAt = expiresAt,
                            keywords = listOf(
                                FilterKeyword(id = "1", keyword = "some_phrase", wholeWord = true),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `HTTP 404 for v2 filters returns correct error type`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn failure(body = "{\"error\": \"error message\"}")
        }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val error = item.getError() as? ContentFiltersError.GetContentFiltersError
            assertThat(error?.error).isInstanceOf(ClientError.NotFound::class.java)
            assertThat(error?.error?.formatArgs).isEqualTo(arrayOf("error message"))
        }
    }

    @Test
    fun `HTTP 404 for v1 filters returns correct error type`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn failure(body = "{\"error\": \"error message\"}")
        }

        serverFlow.update { Ok(SERVER_V1) }

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val error = item.getError() as? ContentFiltersError.GetContentFiltersError
            assertThat(error?.error).isInstanceOf(ClientError.NotFound::class.java)
            assertThat(error?.error?.formatArgs).isEqualTo(arrayOf("error message"))
        }
    }
}
