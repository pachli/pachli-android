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

package app.pachli.components.notifications

import app.cash.turbine.test
import app.pachli.core.data.repository.Loadable
import app.pachli.core.database.model.AccountEntity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

@HiltAndroidTest
class NotificationsViewModelTestVisibleId : NotificationsViewModelTestBase() {

    @Test
    fun `should save notification ID to active account`() = runTest {
        accountManager
            .activeAccountFlow.filterIsInstance<Loadable.Loaded<AccountEntity?>>()
            .filter { it.data != null }
            .map { it.data }
            .test {
                // Given
                assertThat(awaitItem()!!.lastNotificationId).isEqualTo("0")

                // When
                viewModel.accept(InfallibleUiAction.SaveVisibleId("1234"))

                // Then
                assertThat(awaitItem()!!.lastNotificationId).isEqualTo("1234")
            }

//        argumentCaptor<Pair<Long, String>>().apply {
//            // When
//            viewModel.accept(InfallibleUiAction.SaveVisibleId("1234"))
//
//            // Then
//            verify(accountManager).setLastNotificationId(capture().first, capture().second)
//            assertThat(this.lastValue.second)
//                .isEqualTo("1234")
//        }
    }
}
