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
import app.pachli.core.data.model.from
import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterKeyword
import app.pachli.core.network.model.FilterAction as NetworkFilterAction
import app.pachli.core.network.model.FilterContext as NetworkFilterContext
import app.pachli.core.network.model.FilterKeyword as NetworkFilterKeyword
import app.pachli.core.testing.success
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Date
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
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
class ContentFiltersRepositoryTestUpdate : V2Test() {
    @Test
    fun `v2 update with no keyword changes should only call updateFilter once`() = runTest {
        // Configure API with a single filter.
        networkFilters.addNetworkFilter("Test filter")
        contentFiltersRepository.refresh(pachliAccountId)

        val updatedTitle = "New title"

        mastodonApi.stub {
            // Takes the original network filter and applies the update, returning the updated
            // filter.
            onBlocking { updateFilter(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer { call ->
                val id = call.getArgument<String>(0)
                val originalNetworkFilter = networkFilters.find { it.id == id }!!

                success(
                    originalNetworkFilter.copy(
                        title = call.getArgument(1) ?: originalNetworkFilter.title,
                        contexts = call.getArgument(2) ?: originalNetworkFilter.contexts,
                        filterAction = call.getArgument(3) ?: originalNetworkFilter.filterAction,
                        expiresAt = call.getArgument<String?>(4)?.let {
                            when (it) {
                                "" -> null
                                else -> Date(System.currentTimeMillis() + (it.toInt() * 1000))
                            }
                        },
                    ),
                )
            }
            // Returns a copy of the filter with the modified title.
            onBlocking { getFilter(anyString()) } doAnswer { call ->
                val id = call.getArgument<String>(0)
                success(networkFilters.first { it.id == id }.copy(title = updatedTitle))
            }
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm the initial filters are `networkFilters`. */
            advanceUntilIdle()
            val contentFilters = awaitItem()
            assertThat(contentFilters).isEqualTo(
                ContentFilters(
                    contentFilters = networkFilters.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                ),
            )

            // Update the first filter.
            val originalContentFilter = contentFilters.contentFilters.first()
            val update = ContentFilterEdit(id = originalContentFilter.id, title = updatedTitle)
            contentFiltersRepository.updateContentFilter(
                pachliAccountId,
                originalContentFilter,
                update,
            )
            advanceUntilIdle()

            // Confirm the first filter has the new title.
            val newContentFilter = awaitItem().contentFilters.first()
            assertThat(newContentFilter.title).isEqualTo("New title")

            verify(mastodonApi, times(1)).updateFilter(
                id = update.id,
                title = update.title,
                contexts = update.contexts?.map { NetworkFilterContext.from(it) }?.toSet(),
                filterAction = update.filterAction?.let { NetworkFilterAction.from(it) },
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
        // Configure API with a single filter.
        networkFilters.addNetworkFilter(
            "Test filter",
            keywords = listOf("keyword one", "keyword two", "keyword three", "keyword four"),
        )
        contentFiltersRepository.refresh(pachliAccountId)

        mastodonApi.stub {
            onBlocking { deleteFilterKeyword(any()) } doReturn success(Unit)
            onBlocking { updateFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(NetworkFilterKeyword(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
            }
            onBlocking { addFilterKeyword(any(), any(), any()) } doAnswer { call ->
                success(NetworkFilterKeyword("x", call.getArgument(1), call.getArgument(2)))
            }
            // Return the unmodified filter. The actual network calls are checked, so this
            // simplifies the test by not needing to recreate the modified filter.
            onBlocking { getFilter(any()) } doReturn success(networkFilters.first())
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm the initial filters are `networkFilters`. */
            advanceUntilIdle()
            val contentFilters = awaitItem()
            assertThat(contentFilters).isEqualTo(
                ContentFilters(
                    contentFilters = networkFilters.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                ),
            )

            // Update the first filter.
            val originalContentFilter = contentFilters.contentFilters.first()
            val keywordToAdd = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)
            val keywordToDelete = originalContentFilter.keywords[1]
            val keywordToModify = originalContentFilter.keywords[0].copy(keyword = "new keyword")

            val update = ContentFilterEdit(
                id = originalContentFilter.id,
                keywordsToAdd = listOf(keywordToAdd),
                keywordsToDelete = listOf(keywordToDelete),
                keywordsToModify = listOf(keywordToModify),
            )
            contentFiltersRepository.updateContentFilter(pachliAccountId, originalContentFilter, update)
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
