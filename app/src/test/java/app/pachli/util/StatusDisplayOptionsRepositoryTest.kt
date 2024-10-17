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
import app.pachli.PachliApplication
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.AccountPreferenceDataStore
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.network.di.test.DEFAULT_INSTANCE_V2
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onSuccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class StatusDisplayOptionsRepositoryTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var accountPreferenceDataStore: AccountPreferenceDataStore

    @Inject
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    private val defaultStatusDisplayOptions = StatusDisplayOptions()

    private val account = Account(
        id = "1",
        localUsername = "username",
        username = "username@domain.example",
        displayName = "Display Name",
        createdAt = Date.from(Instant.now()),
        note = "",
        url = "",
        avatar = "",
        header = "",
    )

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2(anyOrNull()) } doReturn success(DEFAULT_INSTANCE_V2)
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { getCustomEmojis() } doReturn failure()
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { listAnnouncements(anyOrNull()) } doReturn success(emptyList())
        }

        reset(nodeInfoApi)
        nodeInfoApi.stub {
            onBlocking { nodeInfoJrd() } doReturn success(
                UnvalidatedJrd(
                    listOf(
                        UnvalidatedJrd.Link(
                            "http://nodeinfo.diaspora.software/ns/schema/2.1",
                            "https://example.com",
                        ),
                    ),
                ),
            )
            onBlocking { nodeInfo(any()) } doReturn success(
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "4.2.0")),
            )
        }

        accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }
    }

    @Test
    fun `default options are correct`() = runTest {
        assertThat(statusDisplayOptionsRepository.flow.value).isEqualTo(defaultStatusDisplayOptions)
    }

    @Test
    fun `changing a preference emits correct value`() = runTest {
        val initial = statusDisplayOptionsRepository.flow.value.animateAvatars

        sharedPreferencesRepository.edit {
            putBoolean(PrefKeys.ANIMATE_GIF_AVATARS, !initial)
        }

        statusDisplayOptionsRepository.flow.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().animateAvatars).isEqualTo(!initial)
        }
    }

    @Test
    fun `changing account preference emits correct value`() = runTest {
        statusDisplayOptionsRepository.flow.test {
            // Given - openSpoiler is an account-level preference
            val initial = awaitItem().openSpoiler

            // When
            accountPreferenceDataStore.putBoolean(PrefKeys.ALWAYS_OPEN_SPOILER, !initial)

            // Then
            assertThat(awaitItem().openSpoiler).isEqualTo(!initial)
        }
    }

    @Test
    fun `changing an account emits correct value`() = runTest {
        // Given -- openSpoiler is an account-level preference, confirm it's changed
        val initial = statusDisplayOptionsRepository.flow.value.openSpoiler
        statusDisplayOptionsRepository.flow.test {
            assertThat(awaitItem().openSpoiler).isEqualTo(initial)
            accountPreferenceDataStore.putBoolean(PrefKeys.ALWAYS_OPEN_SPOILER, !initial)
            assertThat(awaitItem().openSpoiler).isEqualTo(!initial)
        }

        val account = Account(
            id = "2",
            localUsername = "username2",
            username = "username2@domain2.example",
            displayName = "Display Name",
            createdAt = Date.from(Instant.now()),
            note = "",
            url = "",
            avatar = "",
            header = "",
        )

        mastodonApi.stub {
            onBlocking { accountVerifyCredentials() } doReturn success(account)
        }

        // When -- addAccount changes the active account
        accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "domain2.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }

        // Then -- openSpoiler should be reset to the default
        statusDisplayOptionsRepository.flow.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().openSpoiler).isEqualTo(initial)
        }
    }
}
