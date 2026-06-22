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

package app.pachli.core.data.model

import app.pachli.core.model.NodeInfo
import app.pachli.core.model.Server
import app.pachli.core.model.ServerKind
import app.pachli.core.model.ServerKind.AKKOMA
import app.pachli.core.model.ServerKind.FIREFISH
import app.pachli.core.model.ServerKind.FRIENDICA
import app.pachli.core.model.ServerKind.GOTOSOCIAL
import app.pachli.core.model.ServerKind.MASTODON
import app.pachli.core.model.ServerKind.PLEROMA
import app.pachli.core.model.ServerKind.UNKNOWN
import app.pachli.core.model.ServerLimits
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_ACCOUNT_QUOTE_POLICY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_ACTION_BLUR
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_FROM
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_QUOTE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_GET
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_QUOTE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_SCHEDULED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_TIMELINES_LINK
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Configuration
import app.pachli.core.network.model.Contact
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.InstanceV2Accounts
import app.pachli.core.network.model.InstanceV2Polls
import app.pachli.core.network.model.InstanceV2Statuses
import app.pachli.core.network.model.InstanceV2Translation
import app.pachli.core.network.model.InstanceV2Urls
import app.pachli.core.network.model.MediaAttachments
import app.pachli.core.network.model.Registrations
import app.pachli.core.network.model.Thumbnail
import app.pachli.core.network.model.Usage
import app.pachli.core.network.model.Users
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.github.z4kn4fein.semver.toVersion
import java.io.BufferedReader
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class ServerTest(
    private val input: Triple<String, NodeInfo.Software, InstanceV2>,
    private val want: Result<Server, Server.Error>,
) {
    companion object {
        private val defaultInstance = InstanceV2(
            domain = "example.com",
            title = "",
            version = "",
            sourceUrl = "",
            description = "",
            usage = Usage(users = Users(activeMonth = 1)),
            thumbnail = Thumbnail(url = "", blurhash = null, versions = null),
            languages = emptyList(),
            configuration = Configuration(
                urls = InstanceV2Urls(streamingApi = ""),
                accounts = InstanceV2Accounts(maxFeaturedTags = 1),
                statuses = InstanceV2Statuses(
                    maxCharacters = 500,
                    maxMediaAttachments = 4,
                    charactersReservedPerUrl = 23,
                ),
                mediaAttachments = MediaAttachments(
                    supportedMimeTypes = emptyList(),
                    imageSizeLimit = 10485760,
                    imageMatrixLimit = 16777216,
                    videoSizeLimit = 41943040,
                    videoFrameRateLimit = 1,
                    videoMatrixLimit = 1,
                ),
                polls = InstanceV2Polls(
                    maxOptions = 4,
                    maxCharactersPerOption = 50,
                    minExpiration = 300,
                    maxExpiration = 604800,
                ),
                translation = InstanceV2Translation(enabled = false),
            ),
            registrations = Registrations(
                enabled = false,
                approvalRequired = false,
                message = null,
            ),
            contact = Contact(
                email = "",
                account = Account(
                    id = "1",
                    localUsername = "",
                    username = "",
                    displayName = null,
                    createdAt = null,
                    note = "",
                    url = "",
                    avatar = "",
                    header = "",
                    locked = false,
                ),
            ),
            rules = emptyList(),
        )

        @Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf(
                    Triple(
                        "Mastodon 4.0.0 has expected capabilities",
                        NodeInfo.Software("mastodon", "4.0.0"),
                        defaultInstance.copy(version = "4.0.0"),
                    ),
                    Ok(
                        Server(
                            kind = MASTODON,
                            version = "4.0.0".toVersion(),
                            rawVersion = "4.0.0",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_FROM to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Mastodon 4.0.0 has translate 1.0.0",
                        NodeInfo.Software("mastodon", "4.0.0"),
                        defaultInstance.copy(
                            version = "4.0.0",
                            configuration = defaultInstance.configuration.copy(
                                translation = InstanceV2Translation(enabled = true),
                            ),
                        ),
                    ),
                    Ok(
                        Server(
                            kind = MASTODON,
                            version = "4.0.0".toVersion(),
                            rawVersion = "4.0.0",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_FROM to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_TRANSLATE to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Mastodon 4.2.0 has translate 1.1.0",
                        NodeInfo.Software("mastodon", "4.2.0"),
                        defaultInstance.copy(
                            version = "4.2.0",
                            configuration = defaultInstance.configuration.copy(
                                translation = InstanceV2Translation(enabled = true),
                            ),
                        ),
                    ),
                    Ok(
                        Server(
                            kind = MASTODON,
                            version = "4.2.0".toVersion(),
                            rawVersion = "4.2.0",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_FROM to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_TRANSLATE to "1.1.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "GoToSocial has no translation, filtering, or scheduling",
                        NodeInfo.Software("gotosocial", "0.13.1 git-ccecf5a"),
                        defaultInstance.copy(version = "0.13.1 git-ccecf5a"),
                    ),
                    Ok(
                        Server(
                            kind = GOTOSOCIAL,
                            version = "0.13.1".toVersion(),
                            rawVersion = "0.13.1 git-ccecf5a",
                            capabilities = emptyMap(),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "GoToSocial 0.15.0 has client filters",
                        NodeInfo.Software("gotosocial", "0.15.0 git-ccecf5a"),
                        defaultInstance.copy(version = "0.15.0 git-ccecf5a"),
                    ),
                    Ok(
                        Server(
                            kind = GOTOSOCIAL,
                            version = "0.15.0".toVersion(),
                            rawVersion = "0.15.0 git-ccecf5a",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "GoToSocial 0.16.0 has server filters",
                        NodeInfo.Software("gotosocial", "0.16.0 git-ccecf5a"),
                        defaultInstance.copy(version = "0.16.0 git-ccecf5a"),
                    ),
                    Ok(
                        Server(
                            kind = GOTOSOCIAL,
                            version = "0.16.0".toVersion(),
                            rawVersion = "0.16.0 git-ccecf5a",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_FROM to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Pleroma can't server filter, can schedule",
                        NodeInfo.Software("pleroma", "2.6.50-875-g2eb5c453.service-origin+soapbox"),
                        defaultInstance.copy(version = "2.6.50-875-g2eb5c453.service-origin+soapbox"),
                    ),
                    Ok(
                        Server(
                            kind = PLEROMA,
                            version = "2.6.50-875-g2eb5c453.service-origin+soapbox".toVersion(),
                            rawVersion = "2.6.50-875-g2eb5c453.service-origin+soapbox",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Akkoma can filter, schedule",
                        NodeInfo.Software("akkoma", "3.9.3-0-gd83f5f66f-blob"),
                        defaultInstance.copy(version = "3.9.3-0-gd83f5f66f-blob"),
                    ),
                    Ok(
                        Server(
                            kind = AKKOMA,
                            version = "3.9.3-0-gd83f5f66f-blob".toVersion(),
                            rawVersion = "3.9.3-0-gd83f5f66f-blob",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Firefish can't filter",
                        NodeInfo.Software("firefish", "1.1.0-dev29-hf1"),
                        defaultInstance.copy(version = "1.1.0-dev29-hf1"),
                    ),
                    Ok(
                        Server(
                            kind = FIREFISH,
                            version = "1.1.0-dev29-hf1".toVersion(),
                            rawVersion = "1.1.0-dev29-hf1",
                            capabilities = emptyMap(),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Friendica can filter, schedule",
                        NodeInfo.Software("friendica", "2023.05-1542"),
                        defaultInstance.copy(version = "2023.05-1542"),
                    ),
                    Ok(
                        Server(
                            kind = FRIENDICA,
                            version = "2023.5.0".toVersion(),
                            rawVersion = "2023.05-1542",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
                arrayOf(
                    Triple(
                        "Hometown >= 4.5.0 can quote",
                        NodeInfo.Software("hometown", "4.5.10+hometown-1.2.1"),
                        defaultInstance.copy(version = "4.5.10+hometown-1.2.1"),
                    ),
                    Ok(
                        Server(
                            kind = ServerKind.HOMETOWN,
                            version = "4.5.10+hometown-1.2.1".toVersion(),
                            rawVersion = "4.5.10+hometown-1.2.1",
                            capabilities = mapOf(
                                ORG_JOINMASTODON_STATUSES_SCHEDULED to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_CLIENT to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_SERVER to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_FROM to "1.1.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_QUOTE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_TIMELINES_LINK to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_GET to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_FILTERS_ACTION_BLUR to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_ACCOUNT_QUOTE_POLICY to "1.0.0".toVersion(),
                                ORG_JOINMASTODON_STATUSES_QUOTE to "1.0.0".toVersion(),
                            ),
                            limits = ServerLimits(),
                            emojis = emptyList(),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `Server from() with V2 works`() {
        val msg = input.first
        val software = input.second
        val instanceV2 = input.third
        assertWithMessage(msg)
            .that(
                Server.from(
                    software = software,
                    instanceInfo = instanceV2.asModel(),
                    emojis = emptyList(),
                ),
            )
            .isEqualTo(want)
    }
}

class ServerVersionTest {
    private val moshi = Moshi.Builder().build()

    private fun loadJsonAsString(fileName: String): String {
        return javaClass.getResourceAsStream("/$fileName")!!
            .bufferedReader().use(BufferedReader::readText)
    }

    /**
     * Test that parsing all possible versions succeeds.
     *
     * To do this tools/mkserverversions generates a JSON file that
     * contains a map of server names to a list of server version strings
     * that have been seen by Fediverse Observer. These version strings
     * are then parsed and are all expected to parse correctly.
     */
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun parseVersionString() {
        val serverVersions = moshi.adapter<Map<String, Set<String>>>()
            .lenient()
            .fromJson(loadJsonAsString("server-versions.json5"))!!

        // Sanity check that data was loaded correctly. Expect at least 5 distinct keys
        assertWithMessage("number of server types in server-versions.json5 is too low")
            .that(serverVersions.size)
            .isGreaterThan(5)

        for ((name, versions) in serverVersions.entries) {
            for (version in versions) {
                val serverKind = ServerKind.from(
                    NodeInfo.Software(
                        name = name,
                        version = version,
                    ),
                )

                // Skip unknown/unsupported servers, as their version strings
                // could be anything.
                if (serverKind == UNKNOWN) continue

                val result = Server.parseVersionString(
                    serverKind,
                    version,
                )

                assertWithMessage("$name : $version")
                    .that(result)
                    .isInstanceOf(Ok::class.java)
            }
        }
    }
}
