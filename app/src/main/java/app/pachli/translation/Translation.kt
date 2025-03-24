/*
 * Copyright 2025 Pachli Association
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

import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import app.pachli.R
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.model.Status
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.TranslationBackend
import app.pachli.settings.PreferenceParent
import app.pachli.settings.enumListPreference
import app.pachli.settings.preferenceCategory
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither

/**
 * Errors that can occur when translating a status.
 */
sealed interface TranslatorError : PachliError {
    data object ServerDoesNotTranslate : TranslatorError {
        override val resourceId = R.string.translator_error_server_does_not_translate
        override val formatArgs = null
        override val cause = null
    }

    @JvmInline
    value class ApiError(val error: app.pachli.core.network.retrofit.apiresult.ApiError) : TranslatorError, PachliError by error

    /** An error occurred trying to detect the source language. */
    @JvmInline
    value class LanguageIdentifierError(val error: app.pachli.languageidentification.LanguageIdentifierError) : TranslatorError, PachliError by error

    /** Language detection succeeded, but no source language could be determined. */
    data object NoLanguageDetected : TranslatorError {
        override val resourceId = R.string.translator_error_no_language_detected
        override val formatArgs = null
        override val cause = null
    }

    /** Language detected succeeded, but the source language is not supported. */
    data class UnsupportedSourceLanguage(val languageTag: String) : TranslatorError {
        override val resourceId = R.string.translator_error_unsupported_source_language_fmt
        override val formatArgs = arrayOf(languageTag)
        override val cause = null
    }

    /** The target language is not supported. */
    data class UnsupportedTargetLanguage(val language: String) : TranslatorError {
        override val resourceId = R.string.translator_error_unsupported_target_language_fmt
        override val formatArgs = arrayOf(language)
        override val cause = null
    }

    data class DownloadRequired(val language: String) : TranslatorError {
        override val resourceId = R.string.translator_error_download_required_fmt
        override val formatArgs = arrayOf(language)
        override val cause = null
    }

    data class ThrowableError(val throwable: Throwable) : TranslatorError {
        override val resourceId: Int = app.pachli.core.network.R.string.error_generic_fmt
        override val formatArgs: Array<Any> = arrayOf(throwable.localizedMessage ?: "")
        override val cause: PachliError? = null
    }
}

/**
 * Interface translation services must implement to be recognised.
 */
interface TranslationService {
    /**
     * String resource to use as the menu label when offering to translate using this
     * service.
     */
    @get:StringRes
    val labelResource: Int

    /**
     * @return True if this [TranslationService] can translate [statusViewData].
     * Does not indicate if the translation will be successful, only that the
     * translation option should be visible in the UI.
     *
     * @param serverCanTranslate True if the server supports translation. Not all
     * implementations of [TranslationService] will use this.
     * @param statusViewData The [IStatusViewData] that will be translated.
     */
    fun canTranslate(statusViewData: IStatusViewData): Boolean

    /**
     * @return An [Ok] result if the translation service can translate (some)
     * languages to [languageTag]. Otherwise [TranslatorError.UnsupportedTargetLanguage].
     */
    fun canTranslateTo(languageTag: String): Result<String, TranslatorError.UnsupportedTargetLanguage>

    /**
     * Translates some/all of the content of [statusViewData]. The precise content
     * translated depends on the implementation.
     */
    suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError>

    /**
     * Populates [parent] with one or more preferences used by this [TranslationService].
     */
    fun preferenceCategory(parent: PreferenceParent, fragmentManager: FragmentManager)
}

/**
 * [TranslationService] that translates using the translation API provided by
 * the user's server.
 *
 * @param mastodonApi API to use to perform the translation
 * @param statusDisplayOptionsRepository Tracks whether the user's server supports
 * translation.
 */
class ServerTranslationService(
    private val mastodonApi: MastodonApi,
    private val statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : TranslationService {
    @get:StringRes
    override val labelResource = R.string.action_translate

    /**
     * @return True if the server can translate and the status' visibility is
     * not [Status.Visibility.DIRECT].
     */
    override fun canTranslate(statusViewData: IStatusViewData): Boolean {
        return statusDisplayOptionsRepository.flow.value.canTranslate && statusViewData.actionable.visibility != Status.Visibility.DIRECT
    }

    override fun canTranslateTo(languageTag: String) = Ok(languageTag)

    override suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError> {
        if (!statusDisplayOptionsRepository.flow.value.canTranslate) {
            return Err(TranslatorError.ServerDoesNotTranslate)
        }

        return mastodonApi.translate(statusViewData.actionableId).mapEither(
            { it.body.toModel() },
            { TranslatorError.ApiError(it) },
        )
    }

    override fun preferenceCategory(parent: PreferenceParent, fragmentManager: FragmentManager) {
        parent.preferenceCategory(app.pachli.core.preferences.R.string.pref_title_translation) {
            enumListPreference<TranslationBackend> {
                setDefaultValue(TranslationBackend.SERVER_ONLY)
                key = PrefKeys.TRANSLATION_BACKEND
                setTitle(app.pachli.core.preferences.R.string.pref_translation_backend)
            }
        }
    }
}
