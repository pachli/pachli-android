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

import app.cash.turbine.test
import app.pachli.components.timeline.viewmodel.InfallibleUiAction
import app.pachli.core.data.repository.Loadable
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.Timeline
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

@HiltAndroidTest
class CachedTimelineViewModelTestVisibleId : CachedTimelineViewModelTestBase() {

    @Test
    fun `should save status ID to active account`() = runTest {
        assertThat(viewModel.timeline).isEqualTo(Timeline.Home)

        accountManager
            .activeAccountFlow.filterIsInstance<Loadable.Loaded<AccountEntity?>>()
            .filter { it.data != null }
            .map { it.data }
            .test {
                // Given
                assertThat(expectMostRecentItem()!!.lastVisibleHomeTimelineStatusId).isNull()

                // When
                viewModel.accept(InfallibleUiAction.SaveVisibleId("1234"))

                // Then
                assertThat(awaitItem()!!.lastVisibleHomeTimelineStatusId).isEqualTo("1234")
            }
    }
}
