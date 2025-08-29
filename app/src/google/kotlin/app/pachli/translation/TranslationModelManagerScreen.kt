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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pachli.R
import app.pachli.core.common.extensions.MiB
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.repository.Loadable
import app.pachli.core.designsystem.theme.AppTheme
import app.pachli.core.designsystem.theme.androidAttrColorAccent
import app.pachli.core.designsystem.theme.androidAttrTextColorPrimary
import app.pachli.core.ui.components.PachliListItem
import app.pachli.core.ui.components.attrDimenResource
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.Locale

@Composable
internal fun TranslationModelManagerScreen(
    viewModel: TranslationModelManagerViewModel,
    onDelete: (TranslationModelViewData) -> Unit,
    onDownload: (TranslationModelViewData) -> Unit,
) {
    val (loaded, remote) = viewModel.flowViewData.collectAsStateWithLifecycle(emptyList())
        .value.partition { it.state.get() is Loadable.Loaded }

    TranslationModelManagerContent(
        loaded,
        remote,
        onDelete,
        onDownload,
    )
}

@Composable
private fun TranslationModelManagerContent(
    loaded: List<TranslationModelViewData>,
    remote: List<TranslationModelViewData>,
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
        TranslationModelList(loaded, remote, onDelete, onDownload)
    }
}

@Composable
private fun TranslationModelList(
    loaded: List<TranslationModelViewData>,
    remote: List<TranslationModelViewData>,
    onDelete: (TranslationModelViewData) -> Unit,
    onDownload: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
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
}

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

@Composable
private fun DownloadedTranslationModelListItem(
    viewData: TranslationModelViewData,
    onDelete: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLanguage = viewData.locale.displayLanguage
    val deleteModelLabel = stringResource(R.string.action_delete_remote_model_fmt, displayLanguage)

    if (viewData.remoteModel.language == "en") {
        PachliListItem(
            modifier = modifier.semantics(mergeDescendants = true) { },
            headlineContent = { Text(text = displayLanguage) },
            trailingContent = {
                IconButton(onClick = { }) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle),
                        "",
                    )
                }
            },
        )

        return
    }

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

        headlineContent = { Text(text = displayLanguage) },
        trailingContent = {
            IconButton(onClick = { onDelete(viewData) }) {
                Icon(
                    painterResource(R.drawable.outline_delete_24),
                    deleteModelLabel,
                )
            }
        },
        supportingContent = {
            (viewData.state.get() as? Loadable.Loaded<ModelStats>)?.let { state ->
                if (state.data.sizeOnDisk != 0L) {
                    Text(text = "${formatNumber(state.data.sizeOnDisk)}B")
                }
            }
        },
    )
}

@Composable
private fun DownloadableTranslationModelListItem(
    viewData: TranslationModelViewData,
    onDownload: (TranslationModelViewData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayLanguage = viewData.locale.displayLanguage
    val state = viewData.state.get()

    val contentDescripion = when (state) {
        is Loadable.Loading -> stringResource(R.string.content_description_loading_fmt, displayLanguage)
        null -> stringResource(R.string.content_description_available_fmt, displayLanguage)
        else -> ""
    }

    val downloadModelLabel = stringResource(R.string.action_download_remote_model_fmt, viewData.locale.displayLanguage)

    PachliListItem(
        modifier = modifier.semantics(mergeDescendants = true) {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = downloadModelLabel,
                    action = {
                        onDownload(viewData)
                        true
                    },
                ),
            )
        },
        headlineContent = { Text(text = viewData.locale.displayLanguage) },
        trailingContent = {
            if (state is Loadable.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                IconButton(onClick = { onDownload(viewData) }) {
                    Icon(
                        painterResource(R.drawable.ic_file_download_black_24dp),
                        contentDescripion,
                    )
                }
            }
        },
        supportingContent = viewData.state.getError()?.let {
            {
                Text(
                    text = it.fmt(context),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Preview
@Composable
private fun PreviewTranslationModelManagerContent() {
    val loaded = listOf(
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("en").build(),
            state = Ok(null),
            locale = Locale.forLanguageTag("en"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("de").build(),
            state = Ok(Loadable.Loaded(ModelStats(sizeOnDisk = 30.MiB.toLong()))),
            locale = Locale.forLanguageTag("de"),
        ),
    )

    val remote = listOf(
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("af").build(),
            state = Ok(null),
            locale = Locale.forLanguageTag("af"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("fr").build(),
            state = Ok(Loadable.Loading),
            locale = Locale.forLanguageTag("fr"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("pl").build(),
            state = Err(TranslatorError.ThrowableError(RuntimeException("Some MLKit error message"))),
            locale = Locale.forLanguageTag("pl"),
        ),
        TranslationModelViewData(
            remoteModel = TranslateRemoteModel.Builder("es").build(),
            state = Ok(null),
            locale = Locale.forLanguageTag("es"),
        ),
    )

    AppTheme {
        TranslationModelManagerContent(
            loaded,
            remote,
            { },
            { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
