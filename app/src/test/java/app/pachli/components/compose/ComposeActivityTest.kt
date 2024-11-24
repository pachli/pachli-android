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

package app.pachli.components.compose

import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.PachliApplication
import app.pachli.R
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTERS_RESERVED_PER_URL
import app.pachli.core.data.model.InstanceInfo.Companion.DEFAULT_CHARACTER_LIMIT
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.InstanceInfoRepository
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.InstanceConfiguration
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.SearchResult
import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import app.pachli.core.testing.failure
import app.pachli.core.testing.rules.MainCoroutineRule
import app.pachli.core.testing.rules.lazyActivityScenarioRule
import app.pachli.core.testing.success
import at.connyduck.calladapter.networkresult.NetworkResult
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import org.robolectric.fakes.RoboMenuItem

open class PachliHiltApplication : PachliApplication()

@CustomTestApplication(PachliHiltApplication::class)
interface HiltTestApplication

@HiltAndroidTest
@Config(application = HiltTestApplication_Application::class)
@RunWith(AndroidJUnit4::class)
class ComposeActivityTest {
    @get:Rule(order = 0)
    var hilt = HiltAndroidRule(this)

    val dispatcher = StandardTestDispatcher()

    @get:Rule(order = 1)
    val mainCoroutineRule = MainCoroutineRule(dispatcher)

    @get:Rule(order = 2)
    var rule = lazyActivityScenarioRule<ComposeActivity>(
        launchActivity = false,
    )

    private var getInstanceCallback: (() -> InstanceV1)? = null

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var nodeInfoApi: NodeInfoApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var instanceInfoRepository: InstanceInfoRepository

    private var pachliAccountId by Delegates.notNull<Long>()

    val account = Account(
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

        getInstanceCallback = null
        reset(mastodonApi)
        mastodonApi.stub {
            onBlocking { accountVerifyCredentials(anyOrNull(), anyOrNull()) } doReturn success(account)
            onBlocking { getCustomEmojis() } doReturn success(emptyList())
            onBlocking { getInstanceV2() } doReturn failure()
            onBlocking { getInstanceV1() } doAnswer {
                getInstanceCallback?.invoke().let { instance ->
                    if (instance == null) {
                        failure()
                    } else {
                        success(instance)
                    }
                }
            }
            onBlocking { search(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn NetworkResult.success(
                SearchResult(emptyList(), emptyList(), emptyList()),
            )
            onBlocking { getLists() } doReturn success(emptyList())
            onBlocking { listAnnouncements(any()) } doReturn success(emptyList())
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

        pachliAccountId = accountManager.verifyAndAddAccount(
            accessToken = "token",
            domain = "domain.example",
            clientId = "id",
            clientSecret = "secret",
            oauthScopes = "scopes",
        )
            .andThen { accountManager.setActiveAccount(it) }
            .onSuccess { accountManager.refresh(it) }
            .get()!!.id
    }

    /**
     * When tests do something like this (lines marked "->")
     *
     *     fun whenBackButtonPressedNotEmpty_notFinish() = runTest {
     *         rule.launch(intent())
     * ->      dispatcher.scheduler.advanceUntilIdle()
     * ->      accountManager.getPachliAccountFlow(pachliAccountId).first()
     *
     *         rule.scenario.onActivity {
     * ->           dispatcher.scheduler.advanceUntilIdle()
     *             insertSomeTextInContent(it)
     *             clickBack(it)
     *             assertFalse(it.isFinishing)
     *         }
     *     }
     *
     * it's because there's (currently) no easy way for the test to determine
     * that ComposeActivity has finished setting up the UI / loading data from
     * AccountManager and is ready to receive input.
     *
     * TODO: Fix this bug by rewriting ComposeViewModel to drive the UI
     * state of ComposeActivity, and waiting for ComposeViewModel to be
     * ready in tests.
     */

    @Test
    fun whenCloseButtonPressedAndEmpty_finish() {
        rule.launch()
        rule.scenario.onActivity {
            clickUp(it)
            assertTrue(it.isFinishing)
        }
    }

    @Test
    fun whenCloseButtonPressedNotEmpty_notFinish() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it)
            clickUp(it)
            assertFalse(it.isFinishing)
            // We would like to check for dialog but Robolectric doesn't work with AppCompat v7 yet
        }
    }

    @Test
    fun whenModifiedInitialState_andCloseButtonPressed_notFinish() = runTest {
        rule.launch(intent(ComposeOptions(modifiedInitialState = true)))
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            clickUp(it)
            assertFalse(it.isFinishing)
        }
    }

    @Test
    fun whenBackButtonPressedAndEmpty_finish() {
        rule.launch()
        rule.scenario.onActivity {
            clickBack(it)
            assertTrue(it.isFinishing)
        }
    }

    @Test
    fun whenBackButtonPressedNotEmpty_notFinish() = runTest {
        rule.launch(intent())
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()

        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it)
            clickBack(it)
            assertFalse(it.isFinishing)
        }
    }

    @Test
    fun whenModifiedInitialState_andBackButtonPressed_notFinish() = runTest {
        rule.launch(intent(ComposeOptions(modifiedInitialState = true)))
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            clickBack(it)
            assertFalse(it.isFinishing)
        }
    }

    @Test
    fun whenMaximumTootCharsIsNull_defaultLimitIsUsed() = runTest {
        getInstanceCallback = { getInstanceWithCustomConfiguration(null) }
        rule.launch()
        rule.scenario.onActivity {
            assertEquals(DEFAULT_CHARACTER_LIMIT, it.maximumTootCharacters)
        }
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        getInstanceCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(customMaximum, it.maximumTootCharacters)
        }
    }

    @Test
    fun whenOnlyLegacyMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        getInstanceCallback = { getInstanceWithCustomConfiguration(customMaximum) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(customMaximum, it.maximumTootCharacters)
        }
    }

    @Test
    fun whenOnlyConfigurationMaximumTootCharsIsPopulated_customLimitIsUsed() = runTest {
        val customMaximum = 1000
        getInstanceCallback = { getInstanceWithCustomConfiguration(null, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(customMaximum, it.maximumTootCharacters)
        }
    }

    @Test
    fun whenDifferentCharLimitsArePopulated_statusConfigurationLimitIsUsed() = runTest {
        val customMaximum = 1000
        getInstanceCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum * 2)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(customMaximum * 2, it.maximumTootCharacters)
        }
    }

    @Test
    fun whenTextContainsNoUrl_everyCharacterIsCounted() = runTest {
        val content = "This is test content please ignore thx "
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, content)
            assertEquals(content.length, it.viewModel.statusLength.value)
        }
    }

    @Test
    fun whenTextContainsEmoji_emojisAreCountedAsOneCharacter() = runTest {
        val content = "Test 😜"
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, content)
            assertEquals(6, it.viewModel.statusLength.value)
        }
    }

    @Test
    fun whenTextContainsConesecutiveEmoji_emojisAreCountedAsSeparateCharacters() = runTest {
        val content = "Test 😜😜"
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, content)
            assertEquals(7, it.viewModel.statusLength.value)
        }
    }

    @Test
    fun whenTextContainsUrlWithEmoji_ellipsizedUrlIsCountedCorrectly() = runTest {
        val content = "https://🤪.com"
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, content)
            assertEquals(DEFAULT_CHARACTERS_RESERVED_PER_URL, it.viewModel.statusLength.value)
        }
    }

    @Test
    fun whenTextContainsNonEnglishCharacters_lengthIsCountedCorrectly() = runTest {
        val content = "こんにちは. General Kenobi" // "Hello there. General Kenobi"
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, content)
            assertEquals(21, it.viewModel.statusLength.value)
        }
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted() = runTest {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = "Check out this @image #search result: "
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, additionalContent + url)
            assertEquals(
                additionalContent.length + DEFAULT_CHARACTERS_RESERVED_PER_URL,
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized() = runTest {
        val shortUrl = "https://pachli.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = " Check out this @image #search result: "
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, shortUrl + additionalContent + url)
            assertEquals(
                additionalContent.length + (DEFAULT_CHARACTERS_RESERVED_PER_URL * 2),
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized() = runTest {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = " Check out this @image #search result: "
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, url + additionalContent + url)
            assertEquals(
                additionalContent.length + (DEFAULT_CHARACTERS_RESERVED_PER_URL * 2),
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted_withCustomConfiguration() = runTest {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = "Check out this @image #search result: "
        val customUrlLength = 16
        getInstanceCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, additionalContent + url)
            assertEquals(
                additionalContent.length + customUrlLength,
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized_withCustomConfiguration() = runTest {
        val shortUrl = "https://pachli.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 18 // The intention is that this is longer than shortUrl.length
        getInstanceCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, shortUrl + additionalContent + url)
            assertEquals(
                additionalContent.length + (customUrlLength * 2),
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized_withCustomConfiguration() = runTest {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM%3A"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 16
        getInstanceCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        instanceInfoRepository.reload(accountManager.activeAccount)

        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            insertSomeTextInContent(it, url + additionalContent + url)
            assertEquals(
                additionalContent.length + (customUrlLength * 2),
                it.viewModel.statusLength.value,
            )
        }
    }

    @Test
    fun whenSelectionIsEmpty_specialTextIsInsertedAtCaret() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            editor.setText("Some text")

            for (caretIndex in listOf(9, 1, 0)) {
                editor.setSelection(caretIndex)
                it.prependSelectedWordsWith(insertText)
                // Text should be inserted at caret
                assertEquals(
                    "Unexpected value at $caretIndex",
                    insertText,
                    editor.text.substring(caretIndex, caretIndex + insertText.length),
                )

                // Caret should be placed after inserted text
                assertEquals(caretIndex + insertText.length, editor.selectionStart)
                assertEquals(caretIndex + insertText.length, editor.selectionEnd)
            }
        }
    }

    @Test
    fun whenSelectionDoesNotIncludeWordBreak_noSpecialTextIsInserted() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "Some text"
            val selectionStart = 1
            val selectionEnd = 4
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // "ome"
            it.prependSelectedWordsWith(insertText)

            // Text and selection should be unmodified
            assertEquals(originalText, editor.text.toString())
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionIncludesWordBreaks_startsOfAllWordsArePrepended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "one two three four"
            val selectionStart = 2
            val originalSelectionEnd = 15
            val modifiedSelectionEnd = 18
            editor.setText(originalText)
            editor.setSelection(selectionStart, originalSelectionEnd) // "e two three f"
            it.prependSelectedWordsWith(insertText)

            // text should be inserted at word starts inside selection
            assertEquals("one #two #three #four", editor.text.toString())

            // selection should be expanded accordingly
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(modifiedSelectionEnd, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionIncludesEnd_textIsNotAppended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "Some text"
            val selectionStart = 7
            val selectionEnd = 9
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // "xt"
            it.prependSelectedWordsWith(insertText)

            // Text and selection should be unmodified
            assertEquals(originalText, editor.text.toString())
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionIncludesStartAndStartIsAWord_textIsPrepended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "Some text"
            val selectionStart = 0
            val selectionEnd = 3
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // "Som"
            it.prependSelectedWordsWith(insertText)

            // Text should be inserted at beginning
            assert(editor.text.startsWith(insertText))

            // selection should be expanded accordingly
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionIncludesStartAndStartIsNotAWord_textIsNotPrepended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "  Some text"
            val selectionStart = 0
            val selectionEnd = 1
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // " "
            it.prependSelectedWordsWith(insertText)

            // Text and selection should be unmodified
            assertEquals(originalText, editor.text.toString())
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionBeginsAtWordStart_textIsPrepended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "Some text"
            val selectionStart = 5
            val selectionEnd = 9
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // "text"
            it.prependSelectedWordsWith(insertText)

            // Text is prepended
            assertEquals("Some #text", editor.text.toString())

            // Selection is expanded accordingly
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionEndsAtWordStart_textIsAppended() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            val editor = it.findViewById<EditText>(R.id.composeEditField)
            val insertText = "#"
            val originalText = "Some text"
            val selectionStart = 1
            val selectionEnd = 5
            editor.setText(originalText)
            editor.setSelection(selectionStart, selectionEnd) // "ome "
            it.prependSelectedWordsWith(insertText)

            // Text is prepended
            assertEquals("Some #text", editor.text.toString())

            // Selection is expanded accordingly
            assertEquals(selectionStart, editor.selectionStart)
            assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
        }
    }

    @Test
    fun whenNoLanguageIsGiven_defaultLanguageIsSelected() = runTest {
        rule.launch()
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(Locale.getDefault().language, it.selectedLanguage)
        }
    }

    @Test
    fun languageGivenInComposeOptionsIsRespected() = runTest {
        rule.launch(intent(ComposeOptions(language = "no")))
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("no", it.selectedLanguage)
        }
    }

    @Test
    fun modernLanguageCodeIsUsed() = runTest {
        // https://github.com/tuskyapp/Tusky/issues/2903
        // "ji" was deprecated in favor of "yi"
        rule.launch(intent(ComposeOptions(language = "ji")))
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("yi", it.selectedLanguage)
        }
    }

    @Test
    fun unknownLanguageGivenInComposeOptionsIsRespected() = runTest {
        rule.launch(intent(ComposeOptions(language = "zzz")))
        dispatcher.scheduler.advanceUntilIdle()
        accountManager.getPachliAccountFlow(pachliAccountId).first()
        rule.scenario.onActivity {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("zzz", it.selectedLanguage)
        }
    }

    /** Returns an intent to launch [ComposeActivity] with the given options */
    private fun intent(composeOptions: ComposeOptions? = null) = ComposeActivityIntent(
        ApplicationProvider.getApplicationContext(),
        pachliAccountId,
        composeOptions,
    )

    private fun clickUp(activity: ComposeActivity) {
        val menuItem = RoboMenuItem(android.R.id.home)
        activity.onOptionsItemSelected(menuItem)
    }

    private fun clickBack(activity: ComposeActivity) {
        activity.onBackPressedDispatcher.onBackPressed()
    }

    private fun insertSomeTextInContent(activity: ComposeActivity, text: String? = null) {
        activity.findViewById<EditText>(R.id.composeEditField).setText(text ?: "Some text")
    }

    private fun getInstanceWithCustomConfiguration(
        maximumLegacyTootCharacters: Int? = null,
        configuration: InstanceConfiguration? = null,
    ): InstanceV1 {
        var result = InstanceV1(
            uri = "https://example.token",
            version = "2.6.3",
        )

        maximumLegacyTootCharacters?.let {
            result = result.copy(maxTootChars = it)
        }
        configuration?.let {
            result = result.copy(configuration = it)
        }

        return result
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
