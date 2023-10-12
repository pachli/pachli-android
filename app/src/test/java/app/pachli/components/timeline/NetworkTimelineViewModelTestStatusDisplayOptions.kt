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

import androidx.core.content.edit
import app.cash.turbine.test
import app.pachli.appstore.PreferenceChangedEvent
import app.pachli.settings.PrefKeys
import app.pachli.util.CardViewMode
import app.pachli.util.StatusDisplayOptions
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [StatusDisplayOptions] are handled correctly.
 *
 * - Is the initial value taken from values in sharedPreferences and account?
 * - Does the make() function correctly use an updated preference?
 * - Is the correct update emitted when a relevant preference changes?
 */
// TODO: With the exception of the types, this is identical to
// NotificationsViewModelTestStatusDisplayOptions
class NetworkTimelineViewModelTestStatusDisplayOptions : NetworkTimelineViewModelTestBase() {

    private val defaultStatusDisplayOptions = StatusDisplayOptions(
        animateAvatars = false,
        mediaPreviewEnabled = true, // setting in NotificationsViewModelTestBase
        useAbsoluteTime = false,
        showBotOverlay = true,
        useBlurhash = true,
        cardViewMode = CardViewMode.NONE,
        confirmReblogs = true,
        confirmFavourites = false,
        hideStats = false,
        animateEmojis = false,
        showStatsInline = false,
        showSensitiveMedia = true, // setting in NotificationsViewModelTestBase
        openSpoiler = true, // setting in NotificationsViewModelTestBase
    )

    @Test
    fun `initial settings are from sharedPreferences and activeAccount`() = runTest {
        viewModel.statusDisplayOptions.test {
            val item = awaitItem()
            assertThat(item).isEqualTo(defaultStatusDisplayOptions)
        }
    }

    @Test
    fun `make() uses updated preference`() = runTest {
        // Prior, should be false
        assertThat(defaultStatusDisplayOptions.animateAvatars).isFalse()

        // Given; just a change to one preferences
        sharedPreferences.edit {
            putBoolean(PrefKeys.ANIMATE_GIF_AVATARS, true)
        }

        // When
        val updatedOptions = defaultStatusDisplayOptions.make(
            sharedPreferences,
            PrefKeys.ANIMATE_GIF_AVATARS,
            accountManager.activeAccount!!,
        )

        // Then, should be true
        assertThat(updatedOptions.animateAvatars).isTrue()
    }

    @Test
    fun `PreferenceChangedEvent emits new StatusDisplayOptions`() = runTest {
        // Prior, should be false
        viewModel.statusDisplayOptions.test {
            val item = expectMostRecentItem()
            assertThat(item.animateAvatars).isFalse()
        }

        // Given
        sharedPreferences.edit {
            putBoolean(PrefKeys.ANIMATE_GIF_AVATARS, true)
        }

        // When
        eventHub.dispatch(PreferenceChangedEvent(PrefKeys.ANIMATE_GIF_AVATARS))

        // Then, should be true
        viewModel.statusDisplayOptions.test {
            val item = expectMostRecentItem()
            assertThat(item.animateAvatars).isTrue()
        }
    }
}
