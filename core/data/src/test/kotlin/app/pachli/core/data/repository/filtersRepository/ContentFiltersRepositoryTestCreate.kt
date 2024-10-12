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
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import app.pachli.core.model.NewContentFilter
import app.pachli.core.model.NewContentFilterKeyword
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterAction as NetworkFilterAction
import app.pachli.core.network.model.FilterContext as NetworkFilterContext
import app.pachli.core.network.model.FilterKeyword as NetworkFilterKeyword
import app.pachli.core.network.model.FilterV1 as NetworkFilterV1
import app.pachli.core.testing.success
import com.github.michaelbull.result.get
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Date
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Filter to use for testing.
 *
 * Has multiple keywords to ensure they are handled correctly.
 */
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

@HiltAndroidTest
class ContentFiltersRepositoryTestCreate : V2Test() {
    @Test
    fun `creating v2 filter should have correct result`() = runTest {
        /** Record the derived filter expiry time for comparison testing. */
        var expiresAt = Date()

        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { createFilter(any<NewContentFilter>()) } doAnswer { call ->
                val newContentFilter = call.getArgument<NewContentFilter>(0)
                val expiresIn = newContentFilter.expiresIn
                expiresAt = Date(System.currentTimeMillis() + (expiresIn * 1000))

                success(
                    NetworkFilter(
                        id = "1",
                        title = newContentFilter.title,
                        contexts = newContentFilter.contexts.map { NetworkFilterContext.from(it) }.toSet(),
                        filterAction = NetworkFilterAction.from(newContentFilter.filterAction),
                        expiresAt = expiresAt,
                        keywords = newContentFilter.keywords.mapIndexed { index, kw ->
                            NetworkFilterKeyword(
                                id = index.toString(),
                                keyword = kw.keyword,
                                wholeWord = kw.wholeWord,
                            )
                        },
                    ),
                )
            }
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm there are no filters.
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(
                ContentFilters(
                    contentFilters = emptyList(),
                    version = ContentFilterVersion.V2,
                ),
            )

            // Create a new filter.
            val result = contentFiltersRepository.createContentFilter(pachliAccountId, filterWithTwoKeywords)
            advanceUntilIdle()
            val expected = ContentFilter(
                id = "1",
                title = filterWithTwoKeywords.title,
                contexts = filterWithTwoKeywords.contexts,
                expiresAt = expiresAt,
                filterAction = filterWithTwoKeywords.filterAction,
                keywords = filterWithTwoKeywords.keywords.mapIndexed { index, newContentFilterKeyword ->
                    FilterKeyword(
                        id = index.toString(),
                        keyword = newContentFilterKeyword.keyword,
                        wholeWord = newContentFilterKeyword.wholeWord,
                    )
                },
            )

            // createContentFilter should return the expected new filter
            assertThat(result.get()).isEqualTo(expected)

            // createFilter should have been called once, with the correct arguments.
            verify(mastodonApi, times(1)).createFilter(filterWithTwoKeywords)

            // Database should contain the expected filters.
            val entity = contentFiltersDao.getByAccount(pachliAccountId)
            assertThat(entity).isEqualTo(
                ContentFiltersEntity(
                    accountId = pachliAccountId,
                    contentFilters = listOf(expected),
                    version = ContentFilterVersion.V2,
                ),
            )

            // The flow should have emitted a new set of filters that includes the one just added.
            val filters = expectMostRecentItem()
            assertThat(filters).isEqualTo(
                ContentFilters(
                    contentFilters = listOf(expected),
                    version = ContentFilterVersion.V2,
                ),
            )

            cancelAndConsumeRemainingEvents()
        }
    }
}

@HiltAndroidTest
class ContentFiltersRepositoryTestCreateV1 : V1Test() {
    @Test
    fun `creating v1 filter should create one filter per keyword`() = runTest {
        /** Record the derived filter expiry time for comparison testing. */
        var expiresAt = Date()

        /** Next ID to use when creating network filters. */
        var nextFilterId = 1

        // Initialise with no existing filters, and API stubs for creating V1 filters.
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { createFilterV1(any(), any(), any(), any(), any()) } doAnswer { call ->
                val expiresIn = call.getArgument<String>(4).toInt()
                expiresAt = Date(System.currentTimeMillis() + (expiresIn * 1000))

                success(
                    NetworkFilterV1(
                        id = (nextFilterId++).toString(),
                        phrase = call.getArgument(0),
                        contexts = call.getArgument(1),
                        irreversible = call.getArgument(2),
                        wholeWord = call.getArgument(3),
                        expiresAt = expiresAt,
                    ),
                )
            }
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm there are no filters
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(
                ContentFilters.EMPTY.copy(
                    version = ContentFilterVersion.V1,
                ),
            )

            // Create a new filter.
            val result = contentFiltersRepository.createContentFilter(pachliAccountId, filterWithTwoKeywords)
            advanceUntilIdle()
            val expected = ContentFilter(
                id = "2",
                title = filterWithTwoKeywords.keywords[1].keyword,
                contexts = filterWithTwoKeywords.contexts,
                expiresAt = expiresAt,
                filterAction = filterWithTwoKeywords.filterAction,
                keywords = listOf(
                    FilterKeyword(
                        id = "0",
                        keyword = filterWithTwoKeywords.keywords[1].keyword,
                        wholeWord = filterWithTwoKeywords.keywords[1].wholeWord,
                    ),
                ),
            )

            // createContentFilter should return the expected new filter
            assertThat(result.get()).isEqualTo(expected)

            // createFilterV1 should have been called twice, once for each keyword
            filterWithTwoKeywords.keywords.forEach { keyword ->
                verify(mastodonApi, times(1)).createFilterV1(
                    phrase = keyword.keyword,
                    context = filterWithTwoKeywords.contexts.map {
                        NetworkFilterContext.from(it)
                    }.toSet(),
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = filterWithTwoKeywords.expiresIn.toString(),
                )
            }

            // Database should contain the expected filters.
            val entity = contentFiltersDao.getByAccount(pachliAccountId)
            assertThat(entity).isEqualTo(
                ContentFiltersEntity(
                    accountId = pachliAccountId,
                    contentFilters = listOf(expected),
                    version = ContentFilterVersion.V1,
                ),
            )

            // The flow should have emitted a new set of filters that includes the one just added.
            val filters = expectMostRecentItem()
            assertThat(filters).isEqualTo(
                ContentFilters(
                    contentFilters = listOf(expected),
                    version = ContentFilterVersion.V1,
                ),
            )

            cancelAndConsumeRemainingEvents()
        }
    }
}
