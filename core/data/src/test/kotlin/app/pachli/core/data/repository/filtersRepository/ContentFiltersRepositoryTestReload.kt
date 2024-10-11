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
import app.pachli.core.testing.failure
import app.pachli.core.testing.success
import com.github.michaelbull.result.Ok
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class ContentFiltersRepositoryTestReload : BaseContentFiltersRepositoryTest() {
    @Test
    fun `reload should trigger a network request`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn failure(body = "v1 should not be called")
            onBlocking { getContentFilters() } doReturn success(emptyList())
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            advanceUntilIdle()
            verify(mastodonApi).getContentFilters()

            contentFiltersRepository.refresh(pachliAccountId)
            advanceUntilIdle()

            verify(mastodonApi, times(2)).getContentFilters()
            verify(mastodonApi, never()).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `changing server should trigger a network request`() = runTest {
        mastodonApi.stub {
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
        }

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            advanceUntilIdle()
            verify(mastodonApi, times(1)).getContentFilters()
            verify(mastodonApi, never()).getContentFiltersV1()

            serverFlow.update { Ok(SERVER_V1) }
            advanceUntilIdle()

            verify(mastodonApi, times(1)).getContentFilters()
            verify(mastodonApi, times(1)).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
