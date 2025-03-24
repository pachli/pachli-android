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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.PachliError
import app.pachli.core.data.repository.Loadable
import app.pachli.core.network.R
import app.pachli.util.getLocaleList
import app.pachli.util.modernLanguageCode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.file.Path
import java.util.Locale
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.fileSize
import kotlin.io.path.walk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The local state of a translation model. See [TranslationModelViewData.state].
 */
private typealias State = Result<Loadable<ModelStats>?, PachliError>

/**
 * Statistics about each model.
 *
 * @property sizeOnDisk The size of the model's files on disk (bytes).
 */
data class ModelStats(
    val sizeOnDisk: Long,
) {
    companion object {
        /**
         * @return A [ModelStats] initialised for [language] with translation
         * files in a subdirectory of [root].
         */
        suspend fun from(root: Path, language: String) = withContext(Dispatchers.IO) {
            // MlKit seems to have two naming schemes for language model directories. Either,
            // "en_$language" or "$language_en". Both need to be checked and the file sizes
            // summed.
            val sizeOnDisk = listOf(root / "${language}_en", root / "en_$language").sumOf { path ->
                path.walk().map { it.fileSize() }.sum()
            }

            return@withContext ModelStats(
                sizeOnDisk = sizeOnDisk,
            )
        }
    }
}

/**
 * View data for a translation model.
 *
 * @param remoteModel MlKit's [TranslateRemoteModel] for this model.
 * @param state The model's download state. Possible values are:
 * - OK(null) - The model is not downloaded.
 * - Ok(Loadable.Loading) - The model is being downloaded.
 * - Ok(Loadable.Loaded) - The model has been downloaded.
 * - Err(ThrowableError) - An error occurred during the download.
 * @param locale The locale corresponding to the model's language.
 */
data class TranslationModelViewData(
    val remoteModel: TranslateRemoteModel,
    val state: State,
    val locale: Locale,
)

/** Wrapper for any exceptions thrown by MlKit. */
data class MlKitError(val throwable: Throwable) : PachliError {
    override val resourceId: Int = R.string.error_generic_fmt
    override val formatArgs: Array<Any> = arrayOf(throwable.localizedMessage ?: "")
    override val cause: PachliError? = null
}

@HiltViewModel
class TranslationModelManagerViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val remoteModelManager = RemoteModelManager.getInstance()

    /**
     * Map from a locale's modern language code to the information for that
     * [Locale].
     */
    private val localeMap = getLocaleList(emptyList()).associateBy { it.modernLanguageCode }

    /** Map from each [TranslateRemoteModel] to its [State]. */
    private val states = MutableStateFlow(emptyMap<TranslateRemoteModel, State>())

    /** Empty [DownloadConditions], to always download. */
    private val downloadConditions = DownloadConditions.Builder().build()

    /**
     * Comparator for [TranslationModelViewData], grouping into downloaded and not downloaded,
     * sorting by displayLanguage within each group. English is always listed first.
     */
    private val compare: Comparator<TranslationModelViewData> = compareBy({ it.remoteModel.language != "en" }, { it.state.get() !is Loadable.Loaded<*> }, { it.locale.displayLanguage })

    /** Path MlKit downloads models to. */
    // Note: This is not a public part of the MlKit API and may change at any time.
    private val downloadedModelsPath = Path(context.noBackupFilesDir.toString()) / "com.google.mlkit.translate.models"

    /**
     * Flow containing a sorted list (see [compare]) of [TranslationModelViewData] suitable
     * for display.
     */
    val flowViewData = states.onSubscription {
        val models = TranslateLanguage.getAllLanguages().map {
            TranslateRemoteModel.Builder(it).build()
        }

        states.update {
            models.associateWith {
                if (remoteModelManager.isModelDownloaded(it).await() == true) {
                    Ok(Loadable.Loaded(ModelStats.from(downloadedModelsPath, it.language)))
                } else {
                    Ok(null)
                }
            }
        }
    }.mapLatest { state ->
        state.mapNotNull { (remoteModel, state) ->
            localeMap[remoteModel.language]?.let { locale ->
                TranslationModelViewData(
                    remoteModel = remoteModel,
                    state = state,
                    locale = locale,
                )
            }
        }.sortedWith(compare)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /**
     * Downloads [language], with updates appearing in [flowViewData].
     *
     * Always downloads, irrespective of the network type. It is the caller's responsibility
     * to check.
     *
     * @param language Language code to download.
     */
    fun downloadLanguage(language: String) {
        val model = TranslateRemoteModel.Builder(language).build()
        downloadModel(model)
    }

    /**
     * Downloads [model], with updates appearing in [flowViewData].
     */
    private fun downloadModel(model: TranslateRemoteModel) {
        viewModelScope.launch {
            states.update { it + (model to Ok(Loadable.Loading)) }
            runSuspendCatching {
                remoteModelManager.download(model, downloadConditions).await()
            }
                .onSuccess {
                    states.update {
                        it + (model to Ok(Loadable.Loaded(ModelStats.from(downloadedModelsPath, model.language))))
                    }
                }
                .onFailure { e ->
                    Timber.e("Download failed: $e")
                    states.update { it + (model to Err(MlKitError(e))) }
                }
        }
    }

    /**
     * Deletes [language], with updates appearing in [flowViewData].
     *
     * @param language Language code to download.
     */
    fun deleteLanguage(language: String) {
        val model = TranslateRemoteModel.Builder(language).build()
        deleteModel(model)
    }

    /**
     * Deletes [model], with updates appearing in [flowViewData].
     */
    private fun deleteModel(model: TranslateRemoteModel) {
        viewModelScope.launch {
            remoteModelManager.deleteDownloadedModel(model).await()
            states.update { it + (model to Ok(null)) }
        }
    }
}
