/*
 * Copyright (c) 2026 Pachli Association
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

// A non-ASCII "․" is used in test names, as a "." is not a valid character
// in Kotlin identifiers.
@file:Suppress("NonAsciiCharacters")

package app.pachli.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.AccountSource
import app.pachli.core.model.Draft
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.testing.fakes.fakeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

abstract class BaseDraftCompanionTest {
    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected val account = AccountEntity(
        id = 0L,
        domain = "mastodon.example",
        accessToken = "token",
        clientId = "id",
        clientSecret = "secret",
        isActive = true,
    )
}

@RunWith(AndroidJUnit4::class)
class DraftCompanionTest : BaseDraftCompanionTest() {
    @Test
    fun `Draft․createDraft() for Timeline․Home uses account defaults (1)`() {
        // When
        val draft = Draft.createDraft(context, account, Timeline.Home)

        // Then
        assertThat(draft.sensitive).isEqualTo(account.defaultMediaSensitivity)
        assertThat(draft.visibility).isEqualTo(account.defaultPostPrivacy)
        assertThat(draft.language).isEqualTo(account.defaultPostLanguage)
        assertThat(draft.quotePolicy).isEqualTo(account.defaultQuotePolicy)

        assertThat(draft.content).isEqualTo("")
        assertThat(draft.cursorPosition).isEqualTo(0)
    }

    @Test
    fun `Draft․createDraft() for Timeline․Home uses account defaults (2)`() {
        // Given
        val account = this.account.copy(
            defaultMediaSensitivity = true,
            defaultPostPrivacy = Status.Visibility.UNLISTED,
            defaultPostLanguage = "un",
            defaultQuotePolicy = AccountSource.QuotePolicy.FOLLOWERS,
        )

        // When
        val draft = Draft.createDraft(context, account, Timeline.Home)

        // Then
        assertThat(draft.sensitive).isEqualTo(account.defaultMediaSensitivity)
        assertThat(draft.visibility).isEqualTo(account.defaultPostPrivacy)
        assertThat(draft.language).isEqualTo(account.defaultPostLanguage)
        assertThat(draft.quotePolicy).isEqualTo(account.defaultQuotePolicy)

        assertThat(draft.content).isEqualTo("")
        assertThat(draft.cursorPosition).isEqualTo(0)
    }

    @Test
    fun `Draft․createDraft() for Timeline․Conversations sets Visibility․DIRECT`() {
        // When
        val draft = Draft.createDraft(context, account, Timeline.Conversations)

        // Then
        assertThat(draft.visibility).isEqualTo(Status.Visibility.DIRECT)
    }

    @Test
    fun `Draft․createDraft() for Timeline․Hashtags sets content and cursor position`() {
        // When
        val draft = Draft.createDraft(
            context,
            account,
            Timeline.Hashtags(listOf("one")),
        )

        // Then
        assertThat(draft.content).isEqualTo(" #one")
        assertThat(draft.cursorPosition).isEqualTo(0)
    }

    @Test
    fun `Draft․createDraftReply() inherits status content warning`() {
        // Given
        val status = fakeStatus().asModel().copy(spoilerText = "content-warning")

        // When
        val draft = Draft.createDraftReply(account, status)

        // Then
        assertThat(draft.contentWarning).isEqualTo(status.spoilerText)
    }

    @Test
    fun `Draft․createDraftReply() inherits status language`() {
        // Given
        val account = this.account.copy(defaultPostLanguage = "en")
        val status = fakeStatus().asModel().copy(language = "fr")

        // When
        val draft = Draft.createDraftReply(account, status)

        // Then
        assertThat(draft.language).isEqualTo(status.language)
    }

    @Test
    fun `Draft․createDraftReply() mentions everyone, cursor at end of content`() {
        // Given
        val status = fakeStatus().asModel()

        // When
        val draft = Draft.createDraftReply(account, status)

        // Then
        assertThat(draft.content).isEqualTo("@connyduck@mastodon.example ")
        assertThat(draft.cursorPosition).isEqualTo(28)
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class DraftCreateReplySensitivityTest(
    private val testData: TestData,
) : BaseDraftCompanionTest() {
    /**
     * The draft reply's sensitivity should be the stricter of the account's
     * default sensitivity and the sensitivity of the status being replied to.
     */
    @Test
    fun `Draft․createDraftReply() uses stricter sensitive property`() {
        // Given
        val account = this.account.copy(defaultMediaSensitivity = testData.accountMediaSensitivity)
        val status = fakeStatus().asModel().copy(sensitive = testData.statusSensitivity)

        // When
        val draft = Draft.createDraftReply(account, status)

        // Then
        assertThat(draft.sensitive).isEqualTo(testData.want)
    }

    companion object {
        data class TestData(
            val accountMediaSensitivity: Boolean,
            val statusSensitivity: Boolean,
            val want: Boolean,
        )

        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun data() = listOf(
            // Both false, so false.
            TestData(accountMediaSensitivity = false, statusSensitivity = false, want = false),
            // Account default is true, so true.
            TestData(accountMediaSensitivity = true, statusSensitivity = false, want = true),
            // Status being replied to is true, so true.
            TestData(accountMediaSensitivity = false, statusSensitivity = true, want = true),
            // Both true, so true.
            TestData(accountMediaSensitivity = true, statusSensitivity = true, want = true),
        )
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class DraftCreateReplyVisibilityTest(
    private val testData: TestData,
) : BaseDraftCompanionTest() {
    /**
     * The draft reply's visibility should be the stricter of the account's
     * default visibility and the visibility of the status being replied to.
     */
    @Test
    fun `Draft․createDraftReply() uses stricter visibility property`() {
        // Given
        val account = this.account.copy(defaultPostPrivacy = testData.accountVisibility)
        val status = fakeStatus().asModel().copy(visibility = testData.statusVisibility)

        // When
        val draft = Draft.createDraftReply(account, status)

        // Then
        assertThat(draft.visibility).isEqualTo(testData.want)
    }

    companion object {
        data class TestData(
            val accountVisibility: Status.Visibility,
            val statusVisibility: Status.Visibility,
            val want: Status.Visibility,
        )

        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun data() = listOf(
            // Both public, want public.
            TestData(Status.Visibility.PUBLIC, Status.Visibility.PUBLIC, Status.Visibility.PUBLIC),
            // Account is public, status is followers-only, want followers-only.
            TestData(Status.Visibility.PUBLIC, Status.Visibility.PRIVATE, Status.Visibility.PRIVATE),
            // Account default is followers-only, status is public, want followers-only.
            TestData(Status.Visibility.PRIVATE, Status.Visibility.PUBLIC, Status.Visibility.PRIVATE),
        )
    }
}
