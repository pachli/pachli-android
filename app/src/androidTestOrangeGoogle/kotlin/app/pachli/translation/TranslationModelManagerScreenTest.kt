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

package app.pachli.translation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import app.pachli.core.common.extensions.MiB
import app.pachli.core.data.repository.Loadable
import app.pachli.core.ui.components.DIALOG_BUTTON_NEGATIVE
import app.pachli.core.ui.components.DIALOG_BUTTON_POSITIVE
import com.github.michaelbull.result.Ok
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

fun withRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

@OptIn(ExperimentalTestApi::class)
class TranslationModelManagerScreenTest {
    @get:Rule
    private val composeTestRule = createComposeRule()

    /**
     * Holds matchers for different nodes of interest on the screen used
     * in the different tests.
     */
    private val screen = object {
        /** Matches the "Delete langauge?" confirmation dialog. */
        val deleteLanguageDialog = hasTestTag("ConfirmDeleteLanguageDialog")

        /** Matches the "Download language?" confirmation dialog. */
        val downloadLanguageDialog = hasTestTag("ConfirmDownloadLanguageDialog")

        /** Matches the button to delete German. */
        val deleteGermanButton = hasContentDescription("Delete German")

        /** Matches the button to download Afrikaans. */
        val downloadAfrikaansButton = withRole(Role.Button).and(hasAnyAncestor(hasText("Afrikaans")))

        /** Matches the positive button on the currently displayed dialog. */
        val positiveButton = hasTestTag(DIALOG_BUTTON_POSITIVE)

        /** Matches the negative button on the current displayed dialog. */
        val negativeButton = hasTestTag(DIALOG_BUTTON_NEGATIVE)
    }

    private val defaultTranslationModels = persistentListOf(
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("en").build(),
            translationModelDownloadState = Ok(Loadable.Loaded(ModelStats(sizeOnDisk = 30.MiB.toLong()))),
            locale = Locale.forLanguageTag("en"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("de").build(),
            translationModelDownloadState = Ok(Loadable.Loaded(ModelStats(sizeOnDisk = 30.MiB.toLong()))),
            locale = Locale.forLanguageTag("de"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("af").build(),
            translationModelDownloadState = Ok(null),
            locale = Locale.forLanguageTag("af"),
        ),
    )

    /** Deleting a downloaded language should show a dialog to confirm. */
    @Test
    fun clickDeleteShowsDialog() = runComposeUiTest {
        // Given: List of languages, with Germany downloaded.
        setContent {
            TranslationModelManagerScreen(
                translationModels = mutableStateOf(defaultTranslationModels),
                onDelete = {},
                onDownload = {},
                canDownload = { true },
            )
        }

        // Then: Dialog to confirm deletion is not present.
        onNode(screen.deleteLanguageDialog).assertDoesNotExist()

        // When: Clicking button to delete "German"
        onNode(screen.deleteGermanButton).performClick()

        // Then: Dialog to delete language should appear, and have the correct
        // title.
        waitUntilExactlyOneExists(screen.deleteLanguageDialog)
        onNode(screen.deleteLanguageDialog).onChildren().filterToOne(isHeading())
            .assertTextEquals("Delete German?")

        // TODO: Using "Espresso.pressBack()" here doesn't work because of
        // https://github.com/composablehorizons/compose-unstyled/issues/204.
        // Espresso.pressBack()

        // When: Clicking cancel button.
        onNode(screen.negativeButton).performClick()

        // Then: Dialog to confirm deletion should disappear.
        waitUntilDoesNotExist(screen.deleteLanguageDialog)
    }

    /**
     * Deleting a downloaded language should show a dialog to confirm, clicking
     * "Ok" on the dialog should start the download.
     */
    @Test
    fun confirmingDeleteCallsOnDelete() = runComposeUiTest {
        // Given: List of languages, with Germany downloaded.
        var onDeleteCalled = false

        setContent {
            TranslationModelManagerScreen(
                translationModels = mutableStateOf(defaultTranslationModels),
                onDelete = { onDeleteCalled = true },
                onDownload = {},
                canDownload = { true },
            )
        }
        assertThat(onDeleteCalled).isFalse()

        // When: Clicking button to delete German, and clicking "Ok".
        onNode(screen.deleteGermanButton).performClick()
        onNode(screen.positiveButton).performClick()

        // Then: Dialog will disappear, Deletion will start.
        waitUntilDoesNotExist(screen.deleteLanguageDialog)
        assertThat(onDeleteCalled).isTrue()
    }

    /**
     * Downloading a language when downloads are allowed should not show
     * a dialog to confirm, the download should start.
     */
    @Test
    fun clickDownload_OnWifi_Downloads() = runComposeUiTest {
        // Given: list of languages, with Afrikaans ("af") downloadable,
        // and the user's configuration allows downloading without prompting.
        var onDownloadCalled = false

        setContent {
            TranslationModelManagerScreen(
                translationModels = mutableStateOf(defaultTranslationModels),
                onDelete = { },
                onDownload = { onDownloadCalled = true },
                canDownload = { true },
            )
        }
        assertThat(onDownloadCalled).isFalse()
        onNode(screen.downloadLanguageDialog).assertDoesNotExist()

        // When: Clicking button to download Afrikaans.
        onNode(screen.downloadAfrikaansButton).performClick()

        // Then: Dialog should not appear, download will start.
        onNode(screen.downloadLanguageDialog).assertDoesNotExist()
        assertThat(onDownloadCalled).isTrue()
    }

    /**
     * Downloading a language when downloads are not allowed should show
     * a dialog to confirm. Cancelling the dialog should not download the
     * language.
     */
    @Test
    fun clickDownload_OnMobile_ShowsDialog() = runComposeUiTest {
        // Given: list of languages, with Afrikaans ("af") downloadable,
        // and the user's configuration allows downloading without prompting.
        var onDownloadCalled = false

        setContent {
            TranslationModelManagerScreen(
                translationModels = mutableStateOf(defaultTranslationModels),
                onDelete = { },
                onDownload = { onDownloadCalled = true },
                canDownload = { false },
            )
        }
        assertThat(onDownloadCalled).isFalse()
        onNode(screen.downloadLanguageDialog).assertDoesNotExist()

        // When: Clicking button to download Afrikaans.
        onNode(screen.downloadAfrikaansButton).performClick()

        // Then: Dialog to confirm download should appear and have the
        // correct title.
        waitUntilExactlyOneExists(screen.downloadLanguageDialog)
        onNode(screen.downloadLanguageDialog).onChildren().filterToOne(isHeading())
            .assertTextEquals("Download Afrikaans with mobile data?")

        // When: Clicking Cancel.
        onNode(screen.negativeButton).performClick()

        // Then: Dialog should disappear, download will not start.
        waitUntilDoesNotExist(screen.downloadLanguageDialog)
        assertThat(onDownloadCalled).isFalse()
    }

    /**
     * Downloading a language when downloads are not allowed should show
     * a dialog to confirm. Clicking "Ok" should start the download.
     */
    @Test
    fun clickDownload_OnMobile_ShowsDialog_OkDownloads() = runComposeUiTest {
        // Given: list of languages, with Afrikaans ("af") downloadable,
        // and the user's configuration allows downloading without prompting.
        var onDownloadCalled = false

        setContent {
            TranslationModelManagerScreen(
                translationModels = mutableStateOf(defaultTranslationModels),
                onDelete = { },
                onDownload = { onDownloadCalled = true },
                canDownload = { false },
            )
        }
        assertThat(onDownloadCalled).isFalse()
        onNode(screen.downloadLanguageDialog).assertDoesNotExist()

        // When: Clicking button to download Afrikaans.
        onNode(screen.downloadAfrikaansButton).performClick()

        // Then: Dialog to confirm download should appear.
        waitUntilExactlyOneExists(screen.downloadLanguageDialog)

        // When: Clicking OK.
        onNode(screen.positiveButton).performClick()

        // Then: Dialog should disappear, download will start.
        waitUntilDoesNotExist(screen.downloadLanguageDialog)
        assertThat(onDownloadCalled).isTrue()
    }
}
