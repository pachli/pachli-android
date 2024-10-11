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
import app.pachli.core.testing.success
import com.github.michaelbull.result.Ok
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class ContentFiltersRepositoryTestDelete : BaseContentFiltersRepositoryTest() {
    @Test
    fun `delete on v2 server should call delete and refresh`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { deleteFilter(any()) } doReturn success(Unit)
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            advanceUntilIdle()
            verify(mastodonApi).getContentFilters()

            contentFiltersRepository.deleteContentFilter(pachliAccountId, "1")
            advanceUntilIdle()

            verify(mastodonApi, times(1)).deleteFilter("1")
            verify(mastodonApi, times(2)).getContentFilters()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `delete on v1 server should call delete and refresh`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { deleteFilterV1(any()) } doReturn success(Unit)
        }

        serverFlow.update { Ok(SERVER_V1) }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            advanceUntilIdle()
            verify(mastodonApi).getContentFiltersV1()

            contentFiltersRepository.deleteContentFilter(pachliAccountId, "1")
            advanceUntilIdle()

            verify(mastodonApi, times(1)).deleteFilterV1("1")
            verify(mastodonApi, times(2)).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
