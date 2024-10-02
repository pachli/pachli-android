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
import app.pachli.core.data.model.NewContentFilterKeyword
import app.pachli.core.data.repository.NewContentFilter
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.model.FilterV1 as NetworkFilterV1
import app.pachli.core.testing.success
import com.github.michaelbull.result.Ok
import dagger.hilt.android.testing.HiltAndroidTest
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

@HiltAndroidTest
class ContentFiltersRepositoryTestCreate : BaseContentFiltersRepositoryTest() {
    private val filterWithTwoKeywords = NewContentFilter(
        title = "new filter",
        contexts = setOf(FilterContext.HOME),
        expiresIn = 300,
        filterAction = FilterAction.WARN,
        keywords = listOf(
            NewContentFilterKeyword(keyword = "first", wholeWord = false),
            NewContentFilterKeyword(keyword = "second", wholeWord = true),
        ),
    )

    @Test
    fun `creating v2 filter should send correct requests`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { createFilter(any(), any(), any(), any()) } doAnswer { call ->
                success(
                    NetworkFilter(
                        id = "1",
                        title = call.getArgument(0),
                        contexts = call.getArgument(1),
                        filterAction = call.getArgument(2),
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

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()

            contentFiltersRepository.createContentFilter(filterWithTwoKeywords)
            advanceUntilIdle()

            // createFilter should have been called once, with the correct arguments.
            verify(mastodonApi, times(1)).createFilter(
                title = filterWithTwoKeywords.title,
                contexts = filterWithTwoKeywords.contexts,
                filterAction = filterWithTwoKeywords.filterAction,
                expiresInSeconds = filterWithTwoKeywords.expiresIn.toString(),
            )

            // To create the keywords addFilterKeyword should have been called twice.
            verify(mastodonApi, times(1)).addFilterKeyword("1", "first", false)
            verify(mastodonApi, times(1)).addFilterKeyword("1", "second", true)

            // Filters should have been refreshed
            verify(mastodonApi, times(2)).getContentFilters()

            cancelAndConsumeRemainingEvents()
        }
    }

    // Test that "expiresIn = 0" in newFilter is converted to "".
    @Test
    fun `expiresIn of 0 is converted to empty string`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { createFilter(any(), any(), any(), any()) } doAnswer { call ->
                success(
                    NetworkFilter(
                        id = "1",
                        title = call.getArgument(0),
                        contexts = call.getArgument(1),
                        filterAction = call.getArgument(2),
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
        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getContentFilters()

            val filterWithZeroExpiry = filterWithTwoKeywords.copy(expiresIn = 0)
            contentFiltersRepository.createContentFilter(filterWithZeroExpiry)
            advanceUntilIdle()

            verify(mastodonApi, times(1)).createFilter(
                title = filterWithZeroExpiry.title,
                contexts = filterWithZeroExpiry.contexts,
                filterAction = filterWithZeroExpiry.filterAction,
                expiresInSeconds = "",
            )

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `creating v1 filter should create one filter per keyword`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
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

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getContentFiltersV1()

            contentFiltersRepository.createContentFilter(filterWithTwoKeywords)
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
            verify(mastodonApi, times(2)).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
