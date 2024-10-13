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

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.HiltTestApplication_Application
import app.pachli.core.data.repository.OfflineFirstContentFiltersRepository
import app.pachli.core.data.source.ContentFiltersLocalDataSource
import app.pachli.core.data.source.ContentFiltersRemoteDataSource
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.dao.ContentFiltersDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Configuration
import app.pachli.core.network.model.Contact
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword as NetworkFilterKeyword
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.InstanceV2Polls
import app.pachli.core.network.model.InstanceV2Statuses
import app.pachli.core.network.model.MediaAttachments
import app.pachli.core.network.model.Registrations
import app.pachli.core.network.model.Thumbnail
import app.pachli.core.network.model.Usage
import app.pachli.core.network.model.Users
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.success
import com.github.michaelbull.result.andThen
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
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

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
abstract class BaseContentFiltersRepositoryTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var localDataSource: ContentFiltersLocalDataSource

    @Inject
    lateinit var remoteDataSource: ContentFiltersRemoteDataSource

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var instanceDao: InstanceDao

    @Inject
    lateinit var contentFiltersDao: ContentFiltersDao

    protected lateinit var contentFiltersRepository: OfflineFirstContentFiltersRepository

    /** Filters that should be returned by mastodonApi.getContentFilters(). */
    protected val networkFilters = mutableListOf<Filter>()

    /** Filters that should be returned by mastodonApi.getContentFiltersV1(). */
    protected val networkFiltersV1 = mutableListOf<FilterV1>()

    protected var pachliAccountId = 0L

    protected val account = Account(
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
}

abstract class V2Test : BaseContentFiltersRepositoryTest() {
    private val instanceV2 = InstanceV2(
        domain = "domain.example",
        title = "Test server",
        version = "4.3.0",
        description = "Test description",
        usage = Usage(users = Users()),
        thumbnail = Thumbnail(
            url = "https://example.com/thumbnail",
            blurhash = null,
            versions = null,
        ),
        languages = emptyList(),
        configuration = Configuration(
            statuses = InstanceV2Statuses(),
            mediaAttachments = MediaAttachments(),
            polls = InstanceV2Polls(),
        ),
        registrations = Registrations(
            enabled = false,
            approvalRequired = false,
            message = null,
        ),
        contact = Contact(),
    )

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            // API calls when registering an account
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2() } doReturn success(instanceV2)
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { getCustomEmojis() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFilters() } doAnswer {
                success(networkFilters)
            }
        }

        networkFilters.clear()

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
        ).andThen {
            pachliAccountId = it
            accountManager.setActiveAccount(it)
        }

        contentFiltersRepository = OfflineFirstContentFiltersRepository(
            TestScope(),
            localDataSource,
            remoteDataSource,
            instanceDao,
        )
    }
}

abstract class V1Test : BaseContentFiltersRepositoryTest() {
    private val instanceV1 = InstanceV1(
        uri = "https://example.com",
        version = "4.3.0",
    )

    @Before
    fun setup() = runTest {
        hilt.inject()

        reset(mastodonApi)
        mastodonApi.stub {
            // API calls when registering an account
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getInstanceV2() } doReturn failure()
            onBlocking { getInstanceV1() } doReturn success(instanceV1)
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
            onBlocking { getContentFiltersV1() } doAnswer { call ->
                success(networkFiltersV1)
            }
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
                UnvalidatedNodeInfo(UnvalidatedNodeInfo.Software("mastodon", "3.9.0")),
            )
        }

        accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        ).andThen {
            pachliAccountId = it
            accountManager.setActiveAccount(it)
        }

        contentFiltersRepository = OfflineFirstContentFiltersRepository(
            TestScope(),
            localDataSource,
            remoteDataSource,
            instanceDao,
        )
    }
}

/**
 * Helper function to add a filter to
 * [BaseContentFiltersRepositoryTest.networkFilters].
 */
fun MutableList<Filter>.addNetworkFilter(
    title: String,
    contexts: Set<FilterContext> = setOf(FilterContext.HOME),
    action: FilterAction = FilterAction.WARN,
    keywords: List<String> = listOf("keyword"),
) {
    add(
        Filter(
            id = this.size.toString(),
            title = title,
            contexts = contexts,
            filterAction = action,
            keywords = keywords.mapIndexed { index, s ->
                NetworkFilterKeyword(
                    id = index.toString(),
                    keyword = s,
                    wholeWord = false,
                )
            },
        ),
    )
}

/**
 * Helper function to add a filter to
 * [BaseContentFiltersRepositoryTest.networkFiltersV1].
 */
fun MutableList<FilterV1>.addNetworkFilter(
    phrase: String,
    contexts: Set<FilterContext> = setOf(FilterContext.HOME),
    irreversible: Boolean = false,
) {
    add(
        FilterV1(
            id = this.size.toString(),
            phrase = phrase,
            contexts = contexts,
            irreversible = irreversible,
            wholeWord = false,
            expiresAt = null,
        ),
    )
}
