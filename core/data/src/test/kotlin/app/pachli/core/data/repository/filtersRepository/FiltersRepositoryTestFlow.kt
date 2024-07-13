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

import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterV1 as NetworkFilterV1
import app.cash.turbine.test
import app.pachli.core.data.model.Filter
import app.pachli.core.data.repository.FilterVersion.V1
import app.pachli.core.data.repository.FilterVersion.V2
import app.pachli.core.data.repository.Filters
import app.pachli.core.data.repository.FiltersError
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.retrofit.apiresult.ClientError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

class FiltersRepositoryTestFlow : BaseFiltersRepositoryTest() {
    @Test
    fun `filters flow returns empty list when there are no v2 filters`() = runTest {
        mastodonApi.stub {
            onBlocking { getFiltersV1() } doReturn failure(body = "v1 should not be called")
            onBlocking { getFilters() } doReturn success(emptyList())
        }

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(Filters(version = V2, filters = emptyList()))
        }
    }

    @Test
    fun `filters flow contains initial set of v2 filters`() = runTest {
        val expiresAt = Date()

        mastodonApi.stub {
            onBlocking { getFiltersV1() } doReturn failure(body = "v1 should not be called")
            onBlocking { getFilters() } doReturn success(
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

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(
                Filters(
                    version = V2,
                    filters = listOf(
                        Filter(
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
            onBlocking { getFilters() } doReturn failure(body = "v2 should not be called")
            onBlocking { getFiltersV1() } doReturn success(emptyList())
        }
        serverFlow.update { Ok(SERVER_V1) }

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(Filters(version = V1, filters = emptyList()))
        }
    }

    @Test
    fun `filters flow contains initial set of v1 filters`() = runTest {
        val expiresAt = Date()

        mastodonApi.stub {
            onBlocking { getFilters() } doReturn failure(body = "v2 should not be called")
            onBlocking { getFiltersV1() } doReturn success(
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

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val filters = item.get()
            assertThat(filters).isEqualTo(
                Filters(
                    version = V1,
                    filters = listOf(
                        Filter(
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
            onBlocking { getFilters() } doReturn failure(body = "{\"error\": \"error message\"}")
        }

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val error = item.getError() as? FiltersError.GetFiltersError
            assertThat(error?.error).isInstanceOf(ClientError.NotFound::class.java)
            assertThat(error?.error?.formatArgs).isEqualTo(arrayOf("error message"))
        }
    }

    @Test
    fun `HTTP 404 for v1 filters returns correct error type`() = runTest {
        mastodonApi.stub {
            onBlocking { getFiltersV1() } doReturn failure(body = "{\"error\": \"error message\"}")
        }

        serverFlow.update { Ok(SERVER_V1) }

        filtersRepository.filters.test {
            advanceUntilIdle()
            val item = expectMostRecentItem()
            val error = item.getError() as? FiltersError.GetFiltersError
            assertThat(error?.error).isInstanceOf(ClientError.NotFound::class.java)
            assertThat(error?.error?.formatArgs).isEqualTo(arrayOf("error message"))
        }
    }
}
