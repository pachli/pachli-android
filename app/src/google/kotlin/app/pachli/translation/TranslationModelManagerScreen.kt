/*
 * Copyright (c) 2025 Pachli Association
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

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pachli.R
import app.pachli.core.common.extensions.MiB
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.repository.Loadable
import app.pachli.core.designsystem.theme.ThemePreviews
import app.pachli.core.designsystem.theme.androidAttrColorAccent
import app.pachli.core.designsystem.theme.androidAttrTextColorPrimary
import app.pachli.core.designsystem.theme.pachliColors
import app.pachli.core.ui.components.PachliListItem
import app.pachli.core.ui.components.PreviewPachliTheme
import app.pachli.core.ui.components.attrDimenResource
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.ThumbVisibility
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Shows downloaded and available-for-download translation language
 * packs, allows the user to download new packs and delete existing
 * ones.
 *
 * Manages dialogs to confirm model deletion and download.
 *
 * @param translationModels All translation models.
 * @param onDelete Called when the user wants to delete a translation model.
 * @param onDownload Called when the user wants to download a translation model.
 * @param canDownload True if the user's settings allow for model download
 * without prompting.
 */
@Composable
internal fun TranslationModelManagerScreen(
    translationModels: () -> ImmutableList<TranslationModelViewData>,
    onDelete: (String) -> Unit,
    onDownload: (String) -> Unit,
    canDownload: () -> Boolean,
) {
    var confirmDeleteLanguageDialogState by rememberSaveable { mutableStateOf<ConfirmDeleteLanguageDialogState?>(null) }
    var confirmDownloadLanguageDialogState by rememberSaveable { mutableStateOf<ConfirmDownloadLanguageDialogState?>(null) }

    Box {
        TranslationModelManagerContent(
            translationModels = translationModels,
            onDownload = { viewData ->
                if (canDownload()) {
                    onDownload(viewData.remoteModel.language)
                } else {
                    confirmDownloadLanguageDialogState = ConfirmDownloadLanguageDialogState.from(viewData)
                }
            },
            onDelete = { viewData ->
                confirmDeleteLanguageDialogState = ConfirmDeleteLanguageDialogState.from(viewData)
            },
        )

        ConfirmDeleteLanguageDialog(
            state = { confirmDeleteLanguageDialogState },
            onDismissRequest = { confirmDeleteLanguageDialogState = null },
            onConfirm = { language ->
                confirmDeleteLanguageDialogState = null
                onDelete(language)
            },
        )

        ConfirmDownloadLanguageDialog(
            state = { confirmDownloadLanguageDialogState },
            onDismissRequest = { confirmDownloadLanguageDialogState = null },
            onConfirm = { language ->
                confirmDownloadLanguageDialogState = null
                onDownload(language)
            },
        )
    }
}

/**
 * Shows downloaded and available-for-download translation language
 * packs, allows the user to download new packs and delete existing
 * ones.
 *
 * @param translationModels All translation models.
 * @param onDelete Called when the user wants to delete a translation model.
 * @param onDownload Called when the user wants to download a translation model.
 * @param modifier
 */
@Composable
private fun TranslationModelManagerContent(
    translationModels: () -> ImmutableList<TranslationModelViewData>,
    onDelete: (TranslationModelViewData) -> Unit,
    onDownload: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(
                start = attrDimenResource(android.R.attr.listPreferredItemPaddingStart),
                end = attrDimenResource(android.R.attr.listPreferredItemPaddingEnd),
            ),
    ) {
        Text(
            text = stringResource(R.string.translation_model_manager_fragment_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.androidAttrTextColorPrimary,
        )
        TranslationModelList(translationModels, onDelete, onDownload, Modifier.fillMaxSize())
    }
}

/**
 * Shows a scrollable list of [translationModels], separated into sections of
 * models that have been downloaded (and can be deleted) and models that can
 * be downloaded.
 *
 * @param translationModels All translation models.
 * @param onDelete Called when the user wants to delete a translation model.
 * @param onDownload Called when the user wants to download a translation model.
 * @param modifier
 */
@Composable
private fun TranslationModelList(
    translationModels: () -> ImmutableList<TranslationModelViewData>,
    onDelete: (TranslationModelViewData) -> Unit,
    onDownload: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val state = rememberScrollAreaState(lazyListState)

    // Bail if there are no items to display (e.g., on first open, or after the
    // device is rotated). If you don't do this the list appears with 0 elements,
    // position restoration runs, there's no position to restore to, and the list
    // is populated with elements some time later.
    val items = translationModels()
    if (items.isEmpty()) return

    val (loaded, remote) = items.partition { it.translationModelDownloadState.get() is Loadable.Loaded }

    ScrollArea(modifier = modifier, state = state) {
        LazyColumn(state = lazyListState) {
            item { ModelHeadingListItem(stringResource(R.string.translation_model_manager_fragment_downloaded_heading)) }
            items(items = loaded, key = { it.remoteModel.language }, contentType = { "Downloaded" }) {
                DownloadedTranslationModelListItem(it, onDelete, Modifier.animateItem())
            }
            item { ModelHeadingListItem(stringResource(R.string.translation_model_manager_fragment_remote_heading)) }
            items(items = remote, key = { it.remoteModel.language }, contentType = { "Downloadable" }) {
                DownloadableTranslationModelListItem(it, onDownload, Modifier.animateItem())
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }

        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(4.dp),
        ) {
            Thumb(
                Modifier.background(Color.LightGray),
                thumbVisibility = ThumbVisibility.HideWhileIdle(
                    enter = fadeIn(),
                    exit = fadeOut(),
                    hideDelay = 2500.milliseconds,
                ),
            )
        }
    }
}

/**
 * Displays a heading in the list of translation models.
 *
 * @param text Text to display in the heading.
 * @param modifier
 */
@Composable
private fun ModelHeadingListItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    PachliListItem(
        modifier = modifier.semantics { heading() },
        headlineContent = {
            Text(
                text,
                color = MaterialTheme.colorScheme.androidAttrColorAccent,
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}

/**
 * Displays a single downloaded [TranslationModelViewData].
 *
 * - English is always shown, and cannot be deleted (it is required by MlKit).
 * - Downloaded models are shown with a "delete" icon. Tapping the icon calls
 * [onDelete].
 *
 * @param viewData
 * @param onDelete Call when the user wants to delete a translation model.
 * @param modifier
 */
@Composable
private fun DownloadedTranslationModelListItem(
    viewData: TranslationModelViewData,
    onDelete: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLanguage = viewData.locale.displayLanguage

    // "English", always present, can't delete.
    if (viewData.remoteModel.language == "en") {
        PachliListItem(
            modifier = modifier.semantics(mergeDescendants = true) { },
            headlineContent = { Text(text = displayLanguage) },
            trailingContent = {
                IconButton(onClick = { }) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle),
                        contentDescription = "",
                    )
                }
            },
        )

        return
    }

    val deleteModelLabel = stringResource(R.string.action_delete_remote_model_fmt, displayLanguage)
    val languageContentDescription = stringResource(R.string.content_description_loaded_fmt, displayLanguage)

    // Downloaded model.
    PachliListItem(
        modifier = modifier.semantics(mergeDescendants = true) {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = deleteModelLabel,
                    action = {
                        onDelete(viewData)
                        true
                    },
                ),
            )
        },
        headlineContent = {
            Text(
                text = displayLanguage,
                modifier = Modifier.semantics { contentDescription = languageContentDescription },
            )
        },
        supportingContent = {
            (viewData.translationModelDownloadState.get() as? Loadable.Loaded<ModelStats>)?.let { state ->
                if (state.data.sizeOnDisk != 0L) {
                    Text(text = "${formatNumber(state.data.sizeOnDisk)}B")
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { onDelete(viewData) }) {
                Icon(
                    painterResource(app.pachli.core.ui.R.drawable.outline_delete_24),
                    deleteModelLabel,
                    tint = pachliColors.colorControlNormal,
                )
            }
        },
    )
}

/**
 * Displays a single downloadable [TranslationModelViewData].
 *
 * - Models are shown with a "download" icon. Tapping the icon calls
 * [onDownload].
 *
 * Download-in-progress models show a progress spinner and hide the action icon.
 *
 * Download errors are shown below the language.
 *
 * @param viewData
 * @param onDownload Call when the user wants to download a translation model.
 * @param modifier
 */
@Composable
private fun DownloadableTranslationModelListItem(
    viewData: TranslationModelViewData,
    onDownload: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayLanguage = viewData.locale.displayLanguage
    val downloadState = viewData.translationModelDownloadState.get()

    val languageContentDescription = when (downloadState) {
        is Loadable.Loading -> stringResource(R.string.content_description_loading_fmt, displayLanguage)
        null -> stringResource(R.string.content_description_available_fmt, displayLanguage)
        else -> ""
    }

    val downloadModelLabel = stringResource(R.string.action_download_remote_model_fmt, viewData.locale.displayLanguage)

    PachliListItem(
        modifier = modifier.semantics(mergeDescendants = true) {
            if (downloadState !is Loadable.Loading) {
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = downloadModelLabel,
                        action = {
                            onDownload(viewData)
                            true
                        },
                    ),
                )
            }
        },
        headlineContent = {
            Text(
                text = viewData.locale.displayLanguage,
                modifier = Modifier.semantics { contentDescription = languageContentDescription },
            )
        },
        supportingContent = viewData.translationModelDownloadState.getError()?.let {
            {
                Text(
                    text = it.fmt(context),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = {
            if (downloadState is Loadable.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                )
            } else {
                IconButton(onClick = { onDownload(viewData) }) {
                    Icon(
                        painterResource(R.drawable.ic_file_download_black_24dp),
                        null,
                        tint = pachliColors.colorControlNormal,
                    )
                }
            }
        },
    )
}

@VisibleForTesting
@ThemePreviews
@Composable
internal fun PreviewTranslationModelManagerContent() {
    val languages = {
        persistentListOf(
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
            TranslationModelViewData(
                remoteModel = TranslateRemoteModel.Builder("fr").build(),
                translationModelDownloadState = Ok(Loadable.Loading),
                locale = Locale.forLanguageTag("fr"),
            ),
            TranslationModelViewData(
                remoteModel = TranslateRemoteModel.Builder("pl").build(),
                translationModelDownloadState = Err(TranslatorError.ThrowableError(RuntimeException("Some MLKit error message"))),
                locale = Locale.forLanguageTag("pl"),
            ),
            TranslationModelViewData(
                remoteModel = TranslateRemoteModel.Builder("es").build(),
                translationModelDownloadState = Ok(null),
                locale = Locale.forLanguageTag("es"),
            ),
        )
    }

    PreviewPachliTheme {
        TranslationModelManagerContent(
            translationModels = languages,
            onDelete = { },
            onDownload = { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
