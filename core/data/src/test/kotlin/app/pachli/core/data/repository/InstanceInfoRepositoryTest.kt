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

package app.pachli.core.data.repository

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.database.AppDatabase
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.InstanceConfiguration
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config

open class PachliHiltApplication : Application()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class InstanceInfoRepositoryTest {
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
    lateinit var instanceInfoRepository: InstanceInfoRepository

    @Inject
    lateinit var appDatabase: AppDatabase

    /**
     * Tests set this to return a customised fake [InstanceV1].
     *
     * After setting this tests must call [InstanceInfoRepository.reload] so
     * the repository re-fetches the data.
     */
    private var instanceResponseCallback: (() -> InstanceV1) = { getInstanceWithCustomConfiguration() }

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
            onBlocking { getCustomEmojis() } doReturn success(emptyList())
            onBlocking { getInstanceV2() } doReturn failure()
            onBlocking { getInstanceV1(anyOrNull()) } doAnswer {
                instanceResponseCallback.invoke().let { success(it) }
            }
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
            onBlocking { accountFollowing(any(), anyOrNull(), any()) } doReturn success(emptyList())
            onBlocking { accountFollowing(any(), anyOrNull(), any()) } doReturn success(emptyList())
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

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun whenMaximumTootCharsIsNull_defaultLimitIsUsed() = runTest {
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null) }

        instanceInfoRepository.instanceInfo.test {
            instanceInfoRepository.reload(accountManager.activeAccount)
            advanceUntilIdle()
            assertEquals(DEFAULT_CHARACTER_LIMIT, expectMostRecentItem().maxChars)
        }
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }

        instanceInfoRepository.instanceInfo.test {
            instanceInfoRepository.reload(accountManager.activeAccount)
            advanceUntilIdle()
            assertEquals(customMaximum, expectMostRecentItem().maxChars)
        }
    }

    @Test
    fun whenOnlyLegacyMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum) }

        instanceInfoRepository.instanceInfo.test {
            instanceInfoRepository.reload(accountManager.activeAccount)
            advanceUntilIdle()
            assertEquals(customMaximum, expectMostRecentItem().maxChars)
        }
    }

    @Test
    fun whenOnlyConfigurationMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }

        instanceInfoRepository.instanceInfo.test {
            instanceInfoRepository.reload(accountManager.activeAccount)
            advanceUntilIdle()
            assertEquals(customMaximum, expectMostRecentItem().maxChars)
        }
    }

    @Test
    fun whenDifferentCharLimitsArePopulated_statusConfigurationLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum * 2)) }

        instanceInfoRepository.instanceInfo.test {
            instanceInfoRepository.reload(accountManager.activeAccount)
            advanceUntilIdle()
            assertEquals(customMaximum * 2, expectMostRecentItem().maxChars)
        }
    }

    private fun getInstanceWithCustomConfiguration(maximumLegacyTootCharacters: Int? = null, configuration: InstanceConfiguration = InstanceConfiguration()): InstanceV1 {
        return InstanceV1(
            uri = "https://example.token",
            version = "2.6.3",
            maxTootChars = maximumLegacyTootCharacters,
            pollConfiguration = null,
            configuration = configuration,
            pleroma = null,
            uploadLimit = null,
            rules = emptyList(),
        )
    }

    private fun getCustomInstanceConfiguration(
        maximumStatusCharacters: Int? = null,
        charactersReservedPerUrl: Int? = null,
    ): InstanceConfiguration {
        var result = InstanceConfiguration()

        maximumStatusCharacters?.let {
            result = result.copy(
                statuses = result.statuses.copy(
                    maxCharacters = it,
                ),
            )
        }

        charactersReservedPerUrl?.let {
            result = result.copy(
                statuses = result.statuses.copy(
                    charactersReservedPerUrl = it,
                ),
            )
        }

        return result
    }
}
