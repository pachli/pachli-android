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
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class ContentFiltersRepositoryTestReload : V2Test() {
    @Test
    fun `reload should trigger a network request and update flow`() = runTest {
        networkFilters.addNetworkFilter("Test filter")

        contentFiltersRepository.getContentFiltersFlow(pachliAccountId).test {
            // Confirm there are no filters at start.
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ContentFilters.EMPTY)

            contentFiltersRepository.refresh(pachliAccountId)
            advanceUntilIdle()

            // Confirm flow now contains the new filters.
            assertThat(awaitItem()).isEqualTo(
                ContentFilters(
                    contentFilters = networkFilters.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                ),
            )

            // getContentFilters() Should be called twice. Once when the account
            // was added, and a second time just now when refresh() was called.
            verify(mastodonApi, times(2)).getContentFilters()

            // V1 version should never be called, as this is a V2 server.
            verify(mastodonApi, never()).getContentFiltersV1()

            cancelAndConsumeRemainingEvents()
        }
    }
}
