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

package app.pachli.components.instanceinfo

import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.db.InstanceDao
import app.pachli.entity.Instance
import app.pachli.entity.InstanceConfiguration
import app.pachli.entity.StatusConfiguration
import app.pachli.network.MastodonApi
import at.connyduck.calladapter.networkresult.NetworkResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class InstanceInfoRepositoryTest {
    private var instanceResponseCallback: (() -> Instance)? = null

    private var accountManager: AccountManager = mock {
        on { activeAccount } doReturn AccountEntity(
            id = 1,
            domain = "mastodon.test",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true,
            lastVisibleHomeTimelineStatusId = null,
            notificationsFilter = "['follow']",
            mediaPreviewEnabled = true,
            alwaysShowSensitiveMedia = true,
            alwaysOpenSpoiler = true,
        )
    }

    private val instanceDao: InstanceDao = mock()

    private lateinit var mastodonApi: MastodonApi

    private lateinit var instanceInfoRepository: InstanceInfoRepository

    // Sets up the test. Needs to be called by hand as the mastodonApi mock needs to
    // be created *after* each test has set [instanceResponseCallback]
    private fun setup() {
        mastodonApi = mock {
            onBlocking { getCustomEmojis() } doReturn NetworkResult.success(emptyList(),)
            onBlocking { getInstance() } doReturn instanceResponseCallback?.invoke().let { instance ->
                if (instance == null) {
                    NetworkResult.failure(Throwable())
                } else {
                    NetworkResult.success(instance)
                }
            }
        }

        instanceInfoRepository = InstanceInfoRepository(
            mastodonApi,
            instanceDao,
            accountManager,
        )
    }

    @Test
    fun whenMaximumTootCharsIsNull_defaultLimitIsUsed() = runTest {
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null) }
        setup()
        val instanceInfo = instanceInfoRepository.getInstanceInfo()
        assertEquals(InstanceInfoRepository.DEFAULT_CHARACTER_LIMIT, instanceInfo.maxChars)
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        setup()
        val instanceInfo = instanceInfoRepository.getInstanceInfo()
        assertEquals(customMaximum, instanceInfo.maxChars)
    }

    @Test
    fun whenOnlyLegacyMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum) }
        setup()
        val instanceInfo = instanceInfoRepository.getInstanceInfo()
        assertEquals(customMaximum, instanceInfo.maxChars)
    }

    @Test
    fun whenOnlyConfigurationMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        setup()
        val instanceInfo = instanceInfoRepository.getInstanceInfo()
        assertEquals(customMaximum, instanceInfo.maxChars)
    }

    @Test
    fun whenDifferentCharLimitsArePopulated_statusConfigurationLimitIsUsed() = runTest {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum * 2)) }
        setup()
        val instanceInfo = instanceInfoRepository.getInstanceInfo()
        assertEquals(customMaximum * 2, instanceInfo.maxChars)
    }

    private fun getInstanceWithCustomConfiguration(maximumLegacyTootCharacters: Int? = null, configuration: InstanceConfiguration? = null): Instance {
        return Instance(
            uri = "https://example.token",
            version = "2.6.3",
            maxTootChars = maximumLegacyTootCharacters,
            pollConfiguration = null,
            configuration = configuration,
            maxMediaAttachments = null,
            pleroma = null,
            uploadLimit = null,
            rules = emptyList(),
        )
    }

    private fun getCustomInstanceConfiguration(maximumStatusCharacters: Int? = null, charactersReservedPerUrl: Int? = null): InstanceConfiguration {
        return InstanceConfiguration(
            statuses = StatusConfiguration(
                maxCharacters = maximumStatusCharacters,
                maxMediaAttachments = null,
                charactersReservedPerUrl = charactersReservedPerUrl,
            ),
            mediaAttachments = null,
            polls = null,
        )
    }
}
