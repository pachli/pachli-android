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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.PachliError
import app.pachli.core.data.repository.Loadable
import app.pachli.core.network.R
import app.pachli.core.preferences.SharedPreferencesRepository
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
import kotlinx.collections.immutable.toImmutableList
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
 * The local download state of a translation model.
 *
 * Possible values are:
 * - `OK(null)` - The model is not downloaded.
 * - `Ok(Loadable.Loading)` - The model is being downloaded.
 * - `Ok(Loadable.Loaded)` - The model has been downloaded.
 * - `Err(ThrowableError)` - An error occurred during the download.
 *
 * See [TranslationModelViewData.translationModelDownloadState].
 */
private typealias TranslationModelDownloadState = Result<Loadable<ModelStats>?, PachliError>

/**
 * Statistics about each model.
 *
 * @property sizeOnDisk The size of the model's files on disk (bytes).
 */
@Immutable
data class ModelStats(val sizeOnDisk: Long) {
    companion object {
        /**
         * @return A [ModelStats] initialised for [languageTags] with translation
         * files in a subdirectory of [root].
         */
        suspend fun from(root: Path, vararg languageTags: String?) = withContext(Dispatchers.IO) {
            // MlKit seems to have two naming schemes for language model directories. Either,
            // "en_$language" or "$language_en". Both need to be checked and the file sizes
            // summed.
            //
            // See the comment at the calling location for why multiple language tags
            // are passed in.
            val sizeOnDisk = languageTags.filterNotNull().distinct()
                .flatMap { listOf(root / "${it}_en", root / "en_$it") }
                .sumOf { path -> path.walk().sumOf { it.fileSize() } }

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
 * @param translationModelDownloadState The model's download state.
 * @param locale The locale corresponding to the model's language.
 */
@Immutable
data class TranslationModelViewData(
    val remoteModel: TranslateRemoteModel,
    val translationModelDownloadState: TranslationModelDownloadState,
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
    private val connectivityManager: ConnectivityManager,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    private val remoteModelManager = RemoteModelManager.getInstance()

    /**
     * Map from a locale's modern language code to the information for that
     * [Locale].
     */
    private val localeMap = getLocaleList(emptyList()).associateBy { it.modernLanguageCode }

    /** Map from each [TranslateRemoteModel] to its [TranslationModelDownloadState]. */
    private val states = MutableStateFlow(emptyMap<TranslateRemoteModel, TranslationModelDownloadState>())

    /** Empty [DownloadConditions], to always download. */
    private val downloadConditions = DownloadConditions.Builder().build()

    /**
     * Comparator for [TranslationModelViewData], grouping into downloaded and not downloaded,
     * sorting by displayLanguage within each group. English is always listed first.
     */
    private val compare: Comparator<TranslationModelViewData> = compareBy({ it.remoteModel.language != "en" }, { it.translationModelDownloadState.get() !is Loadable.Loaded<*> }, { it.locale.displayLanguage })

    /** Path MlKit downloads models to. */
    // Note: This is not a public part of the MlKit API and may change at any time.
    private val downloadedModelsPath = Path(context.noBackupFilesDir.toString()) / "com.google.mlkit.translate.models"

    /**
     * Flow containing a sorted list (see [compare]) of [TranslationModelViewData] suitable
     * for display.
     */
    val translationModelViewData = states.onSubscription {
        val models = TranslateLanguage.getAllLanguages().map {
            TranslateRemoteModel.Builder(it).build()
        }

        states.update {
            models.associateWith {
                if (remoteModelManager.isModelDownloaded(it).await() == true) {
                    // Pass both the model's language and the locale's language when looking
                    // up stats, as MlKit is inconsistent about which one it uses.
                    //
                    // For example, Hebrew, which has:
                    //
                    // - model tag = "he"
                    // - locale tag = "iw"
                    //
                    // uses the locale tag ("iw") in the directory name. But Indonesian, which
                    // has
                    //
                    // - model tag = "id"
                    // - locale tag = "in"
                    //
                    // uses the model tag ("id") in the directory name.
                    Timber.d("mod: ${it.language} ${localeMap[it.language]?.language}")
                    val language = localeMap[it.language]?.language ?: it.language
                    Ok(Loadable.Loaded(ModelStats.from(downloadedModelsPath, it.language, language)))
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
                    translationModelDownloadState = state,
                    locale = locale,
                )
            }
        }.sortedWith(compare).toImmutableList()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /**
     * @return True if languages can be downloaded based on the user's download
     * preference and current connectivity.
     */
    fun canDownloadNow(): Boolean {
        if (!sharedPreferencesRepository.translationDownloadRequireWiFi) return true

        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let { capabilities ->
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        return false
    }

    /**
     * Downloads [language], with updates appearing in [translationModelViewData].
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
     * Downloads [model], with updates appearing in [translationModelViewData].
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
     * Deletes [language], with updates appearing in [translationModelViewData].
     *
     * @param language Language code to download.
     */
    fun deleteLanguage(language: String) {
        val model = TranslateRemoteModel.Builder(language).build()
        deleteModel(model)
    }

    /**
     * Deletes [model], with updates appearing in [translationModelViewData].
     */
    private fun deleteModel(model: TranslateRemoteModel) {
        viewModelScope.launch {
            remoteModelManager.deleteDownloadedModel(model).await()
            states.update { it + (model to Ok(null)) }
        }
    }
}
