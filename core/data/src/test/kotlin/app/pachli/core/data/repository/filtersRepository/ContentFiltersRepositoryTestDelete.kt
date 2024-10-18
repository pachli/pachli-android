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
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.testing.success
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class ContentFiltersRepositoryTestV2Delete : V2Test() {
    @Test
    fun `delete on v2 server should call delete`() = runTest {
        mastodonApi.stub {
            onBlocking { deleteFilter(anyString()) } doReturn success(Unit)
        }

        // Configure API with a single filter.
        networkFilters.addNetworkFilter("Test filter")
        contentFiltersRepository.refresh(pachliAccountId)

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm the flow contains the expected single filter
            advanceUntilIdle()

            // Confirm flow now contains the new filters.
            assertThat(awaitItem()).isEqualTo(
                ContentFilters(
                    contentFilters = networkFilters.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                ),
            )

            // Delete the filter.
            contentFiltersRepository.deleteContentFilter(pachliAccountId, "0")
            advanceUntilIdle()

            // Confirm the flow contains no filters.
            assertThat(awaitItem()).isEqualTo(ContentFilters.EMPTY)

            verify(mastodonApi, times(1)).deleteFilter("0")

            cancelAndConsumeRemainingEvents()
        }
    }
}

@HiltAndroidTest
class ContentFiltersRepositoryTestV1Delete : V1Test() {
    @Test
    fun `delete on v1 server should call deleteFilterV1`() = runTest {
        mastodonApi.stub {
            onBlocking { deleteFilterV1(anyString()) } doReturn success(Unit)
        }

        // Configure API with a single filter.
        networkFiltersV1.addNetworkFilter("Test filter")
        contentFiltersRepository.refresh(pachliAccountId)

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm the flow contains the expected single filter
            advanceUntilIdle()

            // Confirm flow now contains the new filters.
            assertThat(awaitItem()).isEqualTo(
                ContentFilters(
                    contentFilters = networkFiltersV1.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V1,
                ),
            )

            // Delete the filter.
            contentFiltersRepository.deleteContentFilter(pachliAccountId, "0")
            advanceUntilIdle()

            // Confirm the flow contains no filters.
            assertThat(awaitItem()).isEqualTo(
                ContentFilters.EMPTY.copy(
                    version = ContentFilterVersion.V1,
                ),
            )

            verify(mastodonApi, times(1)).deleteFilterV1("0")

            cancelAndConsumeRemainingEvents()
        }
    }
}
