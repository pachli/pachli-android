/*
 * Copyright 2023 Pachli Association
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

package app.pachli.components.timeline

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.components.timeline.viewmodel.NetworkTimelinePagingSource
import app.pachli.components.timeline.viewmodel.Page
import app.pachli.components.timeline.viewmodel.PageCache
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkTimelinePagingSourceTest {
    @Test
    fun `load() with empty pages returns empty list`() = runTest {
        // Given
        val pages = PageCache()
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh("0", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = emptyList<Status>(),
                    prevKey = null,
                    nextKey = null,
                    itemsBefore = 0,
                    itemsAfter = 0,
                ),
            )
    }

    @Test
    fun `load() for an item in a page returns the page containing that item and next, prev keys`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1"))
            upsert(Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2"))
            upsert(Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh("1", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "1")),
                    prevKey = "2",
                    nextKey = "0",
                    itemsBefore = 1,
                    itemsAfter = 1,
                ),
            )
    }

    @Test
    fun `append returns the page after`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1"))
            upsert(Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2"))
            upsert(Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Append("1", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "1")),
                    prevKey = "2",
                    nextKey = "0",
                    itemsBefore = 1,
                    itemsAfter = 1,
                ),
            )
    }

    @Test
    fun `prepend returns the page before`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1"))
            upsert(Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2"))
            upsert(Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Prepend("1", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "1")),
                    prevKey = "2",
                    nextKey = "0",
                    itemsBefore = 1,
                    itemsAfter = 1,
                ),
            )
    }

    @Test
    fun `Refresh with null key returns the latest page`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1"))
            upsert(Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2"))
            upsert(Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "2")),
                    prevKey = null,
                    nextKey = "1",
                    itemsBefore = 0,
                    itemsAfter = 2,
                ),
            )
    }

    @Test
    fun `Append with a too-old key returns empty list`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "20")), nextKey = "10"))
            upsert(Page(data = mutableListOf(mockStatus(id = "10")), prevKey = "20"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Append("9", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    // No page contains key="9" (oldest is key="10"), so empty list
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                    itemsBefore = 0,
                    itemsAfter = 0,
                ),
            )
    }

    @Test
    fun `Prepend with a too-new key returns empty list`() = runTest {
        // Given
        val pages = PageCache().apply {
            upsert(Page(data = mutableListOf(mockStatus(id = "20")), nextKey = "10"))
            upsert(Page(data = mutableListOf(mockStatus(id = "10")), prevKey = "20"))
        }
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Prepend("21", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    // No page contains key="9" (oldest is key="10"), so empty list
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                    itemsBefore = 0,
                    itemsAfter = 0,
                ),
            )
    }
}
