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

package app.pachli.util

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.components.timeline.MainCoroutineRule
import app.pachli.fakes.InMemorySharedPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesRepositoryTest {
    private lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setup() {
        sharedPreferencesRepository = SharedPreferencesRepository(
            InMemorySharedPreferences(),
            TestScope(),
        )
    }

    @Test
    fun `adding a key-value emits the key`() = runTest {
        // Given empty preferences
        assertThat(sharedPreferencesRepository.contains("testKey")).isFalse()

        // When
        sharedPreferencesRepository.edit(commit = true) {
            putBoolean("testKey", true)
        }

        // Then
        sharedPreferencesRepository.changes.test {
            assertThat(awaitItem()).isEqualTo("testKey")
            assertThat(sharedPreferencesRepository.getBoolean("testKey", false)).isTrue()
        }
    }

    @Test
    fun `modifying a key-value emits the key`() = runTest {
        // Given preferences with testKey -> true
        sharedPreferencesRepository = SharedPreferencesRepository(
            InMemorySharedPreferences(mapOf("testKey" to true)),
            TestScope(),
        )
        assertThat(sharedPreferencesRepository.contains("testKey")).isTrue()
        assertThat(sharedPreferencesRepository.getBoolean("testKey", false)).isTrue()

        // When
        sharedPreferencesRepository.edit(commit = true) {
            putBoolean("testKey", false)
        }

        // Then
        sharedPreferencesRepository.changes.test {
            assertThat(awaitItem()).isEqualTo("testKey")
            assertThat(sharedPreferencesRepository.getBoolean("testKey", true)).isFalse()
        }
    }

    @Test
    fun `deleting a key-value emits the key`() = runTest {
        // Given preferences with testKey -> true
        sharedPreferencesRepository = SharedPreferencesRepository(
            InMemorySharedPreferences(mapOf("testKey" to true)),
            TestScope(),
        )
        assertThat(sharedPreferencesRepository.contains("testKey")).isTrue()
        assertThat(sharedPreferencesRepository.getBoolean("testKey", false)).isTrue()

        // When
        sharedPreferencesRepository.edit(commit = true) {
            remove("testKey")
        }

        // Then
        sharedPreferencesRepository.changes.test {
            assertThat(awaitItem()).isEqualTo("testKey")
            assertThat(sharedPreferencesRepository.contains("testKey")).isFalse()
        }
    }

    @Test
    fun `clearing emits null`() = runTest {
        // Given preferences with testKey -> true
        sharedPreferencesRepository = SharedPreferencesRepository(
            InMemorySharedPreferences(mapOf("testKey" to true)),
            TestScope(),
        )
        assertThat(sharedPreferencesRepository.contains("testKey")).isTrue()
        assertThat(sharedPreferencesRepository.getBoolean("testKey", false)).isTrue()

        // When
        sharedPreferencesRepository.edit(commit = true) {
            clear()
        }

        // Then
        sharedPreferencesRepository.changes.test {
            assertThat(awaitItem()).isNull()
            assertThat(sharedPreferencesRepository.contains("testKey")).isFalse()
        }
    }
}
