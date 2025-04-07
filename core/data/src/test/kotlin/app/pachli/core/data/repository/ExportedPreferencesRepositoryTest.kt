/*
 * Copyright 2025 Pachli Association
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

import android.content.ContentResolver
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.InstanceConfiguration
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SCHEMA_VERSION
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onSuccess
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class ExportedPreferencesRepositoryTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var instanceInfoRepository: InstanceInfoRepository

    @Inject
    lateinit var accountManager: AccountManager

    private lateinit var exportedPreferencesRepository: ExportedPreferencesRepository

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

    private val contentResolver = mock<ContentResolver>()
    private val contentResolverBuffer = ByteArrayOutputStream()

    private val adapter = Moshi.Builder().add(TaggedNumberAdapter.Factory()).build().adapter<ExportedPreferences>()

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getCustomEmojis() } doReturn success(emptyList())
            onBlocking { getInstanceV2() } doReturn failure()
            onBlocking { getInstanceV1(anyOrNull()) } doReturn success(
                InstanceV1(
                    uri = "https://example.token",
                    version = "2.6.3",
                    maxTootChars = 500,
                    pollConfiguration = null,
                    configuration = InstanceConfiguration(),
                    pleroma = null,
                    uploadLimit = null,
                    rules = emptyList(),
                ),
            )
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFilters() } doReturn success(emptyList())
            onBlocking { getContentFiltersV1() } doReturn success(emptyList())
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
            domain = "example.com",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }

        // Mock the content resolver used by ExportedPreferencesRepository. Requests
        // to open a file always write to contentResolverBuffer, requests to read from
        // a file always read from contentResolverBuffer.
        reset(contentResolver)
        contentResolverBuffer.reset()
        contentResolver.stub {
            on { openOutputStream(any(), any()) } doReturn contentResolverBuffer
            on { openInputStream(any()) } doAnswer { contentResolverBuffer.toString().byteInputStream() }
        }

        exportedPreferencesRepository = ExportedPreferencesRepository(
            contentResolver = contentResolver,
            sharedPreferencesRepository = sharedPreferencesRepository,
            accountManager = accountManager,
        )
    }

    @Test
    fun exportOk() = runTest {
        // Given initial conditions.

        // When - successful export.
        val exportResult = exportedPreferencesRepository.export(Uri.EMPTY)
        assertThat(exportResult).isInstanceOf(Ok::class.java)

        // Then - exported file has expected contents.
        val exported = adapter.fromJson(contentResolverBuffer.toString())!!
        assertThat(exported.version).isEqualTo(1)
        assertThat(exported.v1.sharedPreferences[PrefKeys.SCHEMA_VERSION]).isEqualTo(SCHEMA_VERSION)
        assertThat(exported.v1.accounts[0].accountId).isEqualTo("1")
        assertThat(exported.v1.accounts[0].domain).isEqualTo("example.com")
    }

    @Test
    fun importOverwritesSharedPref() = runTest {
        // Given - successful export.
        val exportResult = exportedPreferencesRepository.export(Uri.EMPTY)
        assertThat(exportResult).isInstanceOf(Ok::class.java)

        // When - changing a preference value.
        val origConfirmStatusLanguage = sharedPreferencesRepository.confirmStatusLanguage
        val newConfirmStatusLanguage = !origConfirmStatusLanguage
        sharedPreferencesRepository.confirmStatusLanguage = newConfirmStatusLanguage
        assertThat(sharedPreferencesRepository.confirmStatusLanguage).isEqualTo(newConfirmStatusLanguage)

        // Then - successfully importing the preferences should reset the preference value.
        val importResult = exportedPreferencesRepository.import(Uri.EMPTY)
        assertThat(importResult).isInstanceOf(Ok::class.java)
        assertThat(sharedPreferencesRepository.confirmStatusLanguage).isEqualTo(origConfirmStatusLanguage)
    }

    @Test
    fun importOverwritesFromRedactedAccount() = runTest {
        accountManager.accountsFlow.test {
            // Given - successful export
            val exportResult = exportedPreferencesRepository.export(Uri.EMPTY)
            assertThat(exportResult).isInstanceOf(Ok::class.java)

            // When - changing an account's preference.
            var account = expectMostRecentItem().first()

            val origAlwaysOpenSpoiler = account.alwaysOpenSpoiler
            val newAlwaysOpenSpoiler = !origAlwaysOpenSpoiler

            accountManager.setAlwaysOpenSpoiler(account.id, newAlwaysOpenSpoiler)
            account = awaitItem().first()
            assertThat(account.alwaysOpenSpoiler).isEqualTo(newAlwaysOpenSpoiler)

            // Then -- succesfully importing the preferences should reset the preference value
            println("Importing")
            val importResult = exportedPreferencesRepository.import(Uri.EMPTY)
            assertThat(importResult).isInstanceOf(Ok::class.java)
            account = awaitItem().first()
            assertThat(account.alwaysOpenSpoiler).isEqualTo(origAlwaysOpenSpoiler)
        }
    }
}
