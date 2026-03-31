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

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.pachli.R
import app.pachli.core.designsystem.theme.ThemePreviews
import app.pachli.core.ui.components.PachliAlertDialog
import app.pachli.core.ui.components.PreviewPachliTheme
import kotlinx.parcelize.Parcelize

/**
 * Displays a dialog for the user to confirm deletion of a language model.
 *
 * @param language ISO code of the language to delete.
 * @param displayLanguage Name of the language, in the user's locale.
 */
@Immutable
@Parcelize
data class ConfirmDeleteLanguageDialogState(
    val language: String,
    val displayLanguage: String,
) : Parcelable {
    companion object {
        fun from(viewData: TranslationModelViewData) = ConfirmDeleteLanguageDialogState(
            language = viewData.remoteModel.language,
            displayLanguage = viewData.locale.displayLanguage,
        )
    }
}

/**
 * Displays a dialog for the user to confirm deletion of a language model.
 *
 * @param state
 * @param onDismissRequest Called if the dialog is dismissed.
 * @param onConfirm Called if the user confirms.
 */
@Composable
internal fun ConfirmDeleteLanguageDialog(
    state: () -> ConfirmDeleteLanguageDialogState?,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val s = state()
    val language = s?.language
    val displayLanguage = s?.displayLanguage

    PachliAlertDialog(
        dialogTitle = stringResource(R.string.fragment_confirm_delete_language_dialog_title_fmt, displayLanguage ?: ""),
        dialogText = stringResource(R.string.fragment_confirm_delete_language_dialog_msg),
        modifier = Modifier.testTag("ConfirmDeleteLanguageDialog"),
        isVisible = s != null,
        onDismissRequest = onDismissRequest,
    ) { language?.let { onConfirm(it) } }
}

@VisibleForTesting
@ThemePreviews
@Composable
internal fun PreviewConfirmDeleteLanguageDialog() {
    PreviewPachliTheme {
        ConfirmDeleteLanguageDialog(
            state = {
                ConfirmDeleteLanguageDialogState(
                    language = "fr",
                    displayLanguage = "French",
                )
            },
            onDismissRequest = { },
            onConfirm = { },
        )
    }
}
