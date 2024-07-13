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
import app.pachli.core.data.model.NewFilterKeyword
import app.pachli.core.data.repository.NewFilter
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import com.github.michaelbull.result.Ok
import java.util.Date
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FiltersRepositoryTestCreate : BaseFiltersRepositoryTest() {
    private val filterWithTwoKeywords = NewFilter(
        title = "new filter",
        contexts = setOf(FilterContext.HOME),
        expiresIn = 300,
        action = Action.WARN,
        keywords = listOf(
            NewFilterKeyword(keyword = "first", wholeWord = false),
            NewFilterKeyword(keyword = "second", wholeWord = true),
        ),
    )

    @Test
    fun `creating v2 filter should send correct requests`() = runTest {
        mastodonApi.stub {
            onBlocking { getFilters() } doReturn success(emptyList())
            onBlocking { createFilter(any(), any(), any(), any()) } doAnswer { call ->
                success(
                    NetworkFilter(
                        id = "1",
                        title = call.getArgument(0),
                        contexts = call.getArgument(1),
                        action = call.getArgument(2),
                        expiresAt = Date(System.currentTimeMillis() + (call.getArgument<String>(3).toInt() * 1000)),
                        keywords = emptyList(),
                    ),
                )
            }
            onBlocking { addFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(
                    FilterKeyword(
                        id = "1",
                        keyword = call.getArgument(1),
                        wholeWord = call.getArgument(2),
                    ),
                )
            }
        }

        filtersRepository.filters.test {
            advanceUntilIdle()

            // TODO: Check result
            filtersRepository.createFilter(filterWithTwoKeywords)
            advanceUntilIdle()

            // createFilter should have been called once, with the correct arguments.
            verify(mastodonApi, times(1)).createFilter(
                title = filterWithTwoKeywords.title,
                contexts = filterWithTwoKeywords.contexts,
                filterAction = filterWithTwoKeywords.action,
                expiresInSeconds = filterWithTwoKeywords.expiresIn.toString(),
            )

            // To create the keywords addFilterKeyword should have been called twice.
            verify(mastodonApi, times(1)).addFilterKeyword("1", "first", false)
            verify(mastodonApi, times(1)).addFilterKeyword("1", "second", true)

            // Filters should have been refreshed
            verify(mastodonApi, times(2)).getFilters()

            cancelAndConsumeRemainingEvents()
        }
    }

    // Test that "expiresIn = 0" in newFilter is converted to "".
    @Test
    fun `expiresIn of 0 is converted to empty string`() = runTest {
        mastodonApi.stub {
            onBlocking { getFilters() } doReturn success(emptyList())
            onBlocking { createFilter(any(), any(), any(), any()) } doAnswer { call ->
                success(
                    NetworkFilter(
                        id = "1",
                        title = call.getArgument(0),
                        contexts = call.getArgument(1),
                        action = call.getArgument(2),
                        expiresAt = null,
                        keywords = emptyList(),
                    ),
                )
            }
            onBlocking { addFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(
                    FilterKeyword(
                        id = "1",
                        keyword = call.getArgument(1),
                        wholeWord = call.getArgument(2),
                    ),
                )
            }
        }

        // The v2 filter creation test covers most things, this just verifies that
        // createFilter converts a "0" expiresIn to the empty string.
        filtersRepository.filters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getFilters()

            val filterWithZeroExpiry = filterWithTwoKeywords.copy(expiresIn = 0)
            filtersRepository.createFilter(filterWithZeroExpiry)
            advanceUntilIdle()

            verify(mastodonApi, times(1)).createFilter(
                title = filterWithZeroExpiry.title,
                contexts = filterWithZeroExpiry.contexts,
                filterAction = filterWithZeroExpiry.action,
                expiresInSeconds = "",
            )

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `creating v1 filter should create one filter per keyword`() = runTest {
        mastodonApi.stub {
            onBlocking { getFiltersV1() } doReturn success(emptyList())
            onBlocking { createFilterV1(any(), any(), any(), any(), any()) } doAnswer { call ->
                success(
                    NetworkFilterV1(
                        id = "1",
                        phrase = call.getArgument(0),
                        contexts = call.getArgument(1),
                        irreversible = call.getArgument(2),
                        wholeWord = call.getArgument(3),
                        expiresAt = Date(System.currentTimeMillis() + (call.getArgument<String>(4).toInt() * 1000)),
                    ),
                )
            }
        }

        serverFlow.update { Ok(SERVER_V1) }

        filtersRepository.filters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getFiltersV1()

            filtersRepository.createFilter(filterWithTwoKeywords)
            advanceUntilIdle()

            // createFilterV1 should have been called twice, once for each keyword
            filterWithTwoKeywords.keywords.forEach { keyword ->
                verify(mastodonApi, times(1)).createFilterV1(
                    phrase = keyword.keyword,
                    context = filterWithTwoKeywords.contexts,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = filterWithTwoKeywords.expiresIn.toString(),
                )
            }

            // Filters should have been refreshed
            verify(mastodonApi, times(2)).getFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
