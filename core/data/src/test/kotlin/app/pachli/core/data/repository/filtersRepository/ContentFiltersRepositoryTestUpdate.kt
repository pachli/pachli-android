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
import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.testing.success
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Date
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Test that ensures the correct API calls are made given an [ContentFilterEdit]. The correct
 * creation of the [ContentFilterEdit] is tested in FilterViewDataTest.kt
 */
@HiltAndroidTest
class ContentFiltersRepositoryTestUpdate : BaseContentFiltersRepositoryTest() {
    private val originalNetworkFilter = NetworkFilter(
        id = "1",
        title = "original filter",
        contexts = setOf(FilterContext.HOME),
        expiresAt = null,
        action = Action.WARN,
        keywords = listOf(
            FilterKeyword(id = "1", keyword = "first", wholeWord = false),
            FilterKeyword(id = "2", keyword = "second", wholeWord = true),
            FilterKeyword(id = "3", keyword = "three", wholeWord = true),
            FilterKeyword(id = "4", keyword = "four", wholeWord = true),
        ),
    )

    private val originalContentFilter = ContentFilter.from(originalNetworkFilter)

    @Test
    fun `v2 update with no keyword changes should only call updateFilter once`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { updateFilter(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer { call ->
                success(
                    originalNetworkFilter.copy(
                        title = call.getArgument(1) ?: originalContentFilter.title,
                        contexts = call.getArgument(2) ?: originalContentFilter.contexts,
                        action = call.getArgument(3) ?: originalContentFilter.action,
                        expiresAt = call.getArgument<String?>(4)?.let {
                            when (it) {
                                "" -> null
                                else -> Date(System.currentTimeMillis() + (it.toInt() * 1000))
                            }
                        },
                    ),
                )
            }
            onBlocking { getFilter(originalNetworkFilter.id) } doReturn success(originalNetworkFilter)
        }

        val update = ContentFilterEdit(id = originalContentFilter.id, title = "new title")

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getContentFilters()

            contentFiltersRepository.updateContentFilter(originalContentFilter, update)
            advanceUntilIdle()

            verify(mastodonApi, times(1)).updateFilter(
                id = update.id,
                title = update.title,
                contexts = update.contexts,
                filterAction = update.action,
                expiresInSeconds = null,
            )

            verify(mastodonApi, times(1)).getFilter(originalContentFilter.id)
            verify(mastodonApi, times(2)).getContentFilters()
            verify(mastodonApi, never()).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `v2 update with keyword changes should call updateFilter and the keyword methods`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { deleteFilterKeyword(any()) } doReturn success(Unit)
            onBlocking { updateFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(FilterKeyword(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
            }
            onBlocking { addFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(FilterKeyword("x", call.getArgument(1), call.getArgument(2)))
            }
            onBlocking { getFilter(any()) } doReturn success(originalNetworkFilter)
        }

        val keywordToAdd = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)
        val keywordToDelete = originalContentFilter.keywords[1]
        val keywordToModify = originalContentFilter.keywords[0].copy(keyword = "new keyword")

        val update = ContentFilterEdit(
            id = originalContentFilter.id,
            keywordsToAdd = listOf(keywordToAdd),
            keywordsToDelete = listOf(keywordToDelete),
            keywordsToModify = listOf(keywordToModify),
        )

        contentFiltersRepository.contentFilters.test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getContentFilters()

            contentFiltersRepository.updateContentFilter(originalContentFilter, update)
            advanceUntilIdle()

            // updateFilter() call should be skipped, as only the keywords have changed.
            verify(mastodonApi, never()).updateFilter(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

            verify(mastodonApi, times(1)).addFilterKeyword(
                originalContentFilter.id,
                keywordToAdd.keyword,
                keywordToAdd.wholeWord,
            )

            verify(mastodonApi, times(1)).deleteFilterKeyword(keywordToDelete.id)

            verify(mastodonApi, times(1)).updateFilterKeyword(
                keywordToModify.id,
                keywordToModify.keyword,
                keywordToModify.wholeWord,
            )

            verify(mastodonApi, times(1)).getFilter(originalContentFilter.id)
            verify(mastodonApi, times(2)).getContentFilters()
            verify(mastodonApi, never()).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
