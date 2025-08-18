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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.FragmentManager
import app.pachli.R
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.model.translation.TranslatedAttachment
import app.pachli.core.model.translation.TranslatedPoll
import app.pachli.core.model.translation.TranslatedPollOption
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TranslationBackend
import app.pachli.languageidentification.LanguageIdentifier
import app.pachli.languageidentification.UNDETERMINED_LANGUAGE_TAG
import app.pachli.settings.PreferenceParent
import app.pachli.settings.enumListPreference
import app.pachli.settings.preference
import app.pachli.settings.preferenceCategory
import app.pachli.settings.switchPreference
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [TranslationService] that uses Google's ML Kit to perform the translation.
 */
@Singleton
class MlKitTranslationService(
    private val context: Context,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val languageIdentifierFactory: LanguageIdentifier.Factory,
    mastodonApi: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : TranslationService {
    /** Regex matching paras delimited by single newlines. */
    private val rxPara = """^.+?$""".toRegex(RegexOption.MULTILINE)

    @get:StringRes
    override val labelResource: Int
        get() = when (sharedPreferencesRepository.translationBackend) {
            TranslationBackend.SERVER_ONLY -> R.string.action_translate
            TranslationBackend.SERVER_FIRST -> R.string.action_translate
            TranslationBackend.LOCAL_ONLY -> R.string.action_translate_mlkit
        }

    private val serverTranslationService = ServerTranslationService(mastodonApi, statusDisplayOptionsRepository)

    private val connectivityManager by lazy { context.getSystemService<ConnectivityManager>()!! }

    private val downloadConditions = DownloadConditions.Builder().build()

    override fun canTranslate(statusViewData: IStatusViewData) = when (sharedPreferencesRepository.translationBackend) {
        TranslationBackend.SERVER_ONLY -> serverTranslationService.canTranslate(statusViewData)
        TranslationBackend.SERVER_FIRST -> true
        TranslationBackend.LOCAL_ONLY -> true
    }

    override fun canTranslateTo(languageTag: String): Result<String, TranslatorError.UnsupportedTargetLanguage> {
        val targetLanguage = TranslateLanguage.fromLanguageTag(languageTag)
        Timber.d("languageTag: $languageTag, targetLanguage: $targetLanguage")
        if (targetLanguage == null) {
            val locale = Locale.forLanguageTag(languageTag)
            return Err(TranslatorError.UnsupportedTargetLanguage(locale.displayLanguage.ifEmpty { languageTag }))
        }
        return Ok(targetLanguage)
    }

    override suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError> {
        val backend = sharedPreferencesRepository.translationBackend

        // If SERVER_ONLY then return whatever the serverTranslationService returned.
        if (backend == TranslationBackend.SERVER_ONLY) {
            return serverTranslationService.translate(statusViewData)
        }

        // If SERVER_FIRST then return a successful result from serverTranslationService.
        if (backend == TranslationBackend.SERVER_FIRST) {
            serverTranslationService.translate(statusViewData).onSuccess { return Ok(it) }
        }

        // Either the backend is LOCAL_ONLY, or SERVER_FIRST failed. Either way, perform
        // the translation locally.

        // MlKit doesn't work well when translating content that contains HTML tags. To
        // work around this convert the status text from HTML to plain text.
        val text = HtmlCompat.fromHtml(statusViewData.actionable.content, FROM_HTML_MODE_LEGACY)
        val spoilerText = statusViewData.actionable.spoilerText

        // Identify the source language. Use all the possible text in the status to
        // maximise the chance the correct language is identified.
        val textToIdentify = buildString {
            if (spoilerText.isNotEmpty()) {
                append(spoilerText)
                append("\n\n")
            }

            if (text.isNotEmpty()) append(text)

            with(statusViewData.actionable) {
                poll?.let {
                    append("\n")
                    it.options.forEach {
                        append("\n")
                        append(it)
                    }
                }

                attachments.forEach {
                    append("\n")
                    it.description?.let {
                        append(it)
                    }
                }
            }
        }

        val identifiedLanguageTag = languageIdentifierFactory.newInstance().identifyPossibleLanguages(textToIdentify)
            .map { it.firstOrNull()?.languageTag }
            .getOrElse { return Err(TranslatorError.LanguageIdentifierError(it)) }

        if (identifiedLanguageTag == null || identifiedLanguageTag == UNDETERMINED_LANGUAGE_TAG) {
            return Err(TranslatorError.NoLanguageDetected)
        }
        val sourceLanguage = TranslateLanguage.fromLanguageTag(identifiedLanguageTag)
        if (sourceLanguage == null) {
            return Err(TranslatorError.UnsupportedSourceLanguage(identifiedLanguageTag))
        }

        Timber.d("Source language: $sourceLanguage")

        val targetLanguageTag = sharedPreferencesRepository.languageExpandDefault
        val targetLanguage = canTranslateTo(targetLanguageTag).getOrElse { return Err(it) }
        Timber.d("Target language: $targetLanguage")

        // Setting a `requireWiFi()` download condition appears to cause the download task
        // to hang until Wi-Fi becomes available, instead of failing fast. So if models needs
        // to be downloaded check for Wi-Fi availability ourself and bail early if it's not present.
        if (sharedPreferencesRepository.translationDownloadRequireWiFi) {
            val haveWiFi = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (!haveWiFi) {
                val modelManager = RemoteModelManager.getInstance()
                if (!modelManager.isModelDownloaded(TranslateRemoteModel.Builder(sourceLanguage).build()).await()) {
                    return Err(TranslatorError.DownloadRequired(Locale.forLanguageTag(sourceLanguage).displayLanguage))
                }
                if (!modelManager.isModelDownloaded(TranslateRemoteModel.Builder(targetLanguage).build()).await()) {
                    return Err(TranslatorError.DownloadRequired(Locale.forLanguageTag(targetLanguage).displayLanguage))
                }
            }
        }

        // Build the client
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        Timber.d("source.text: $text")
        Timber.d("source.spoilerText: $spoilerText")

        val client = Translation.getClient(options)

        val result = runSuspendCatching {
            // Perform the translation
            client.downloadModelIfNeeded(downloadConditions).await()

            val spoilerText = client.translate(spoilerText)

            // Google MlKit doesn't retain paragraphs / newlines in the translated
            // content, so each paragraph in the content has to be translated
            // separately and joined back together.
            val paras = rxPara.findAll(text).map {
                client.translate(it.value).asDeferred()
            }.toList()

            val translatedPollOptions = statusViewData.status.poll?.options?.map {
                client.translate(it.title).asDeferred()
            }

            // Translated description for each attachment. Each item in this list
            // is either null (the attachment had no description), or a list of
            // translated paragraphs that are joined back together
            val translatedAttachmentDescriptions = statusViewData.actionable.attachments.map {
                it.description?.let {
                    rxPara.findAll(it).map {
                        client.translate(it.value).asDeferred()
                    }.toList()
                }
            }

            return@runSuspendCatching TranslatedStatus(
                spoilerText = spoilerText.await(),
                content = paras.awaitAll().joinToString("") { "<p>$it</p>" },
                detectedSourceLanguage = sourceLanguage,
                poll = translatedPollOptions?.let { options ->
                    TranslatedPoll(
                        id = statusViewData.status.poll!!.id,
                        options = options.awaitAll().map {
                            TranslatedPollOption(title = it)
                        },
                    )
                },
                attachments = statusViewData.status.attachments.mapIndexed { i, attachment ->
                    TranslatedAttachment(
                        id = attachment.id,
                        description = translatedAttachmentDescriptions.getOrNull(i)?.awaitAll()?.joinToString("\n\n"),
                    )
                },
                // See attribution requirements at
                // https://cloud.google.com/translate/attribution
                provider = context.getString(R.string.label_translated_mlkit),
            )
        }.mapError { TranslatorError.ThrowableError(it) }
        Timber.d("result: $result")

        client.close()
        return result
    }

    override fun preferenceCategory(parent: PreferenceParent, fragmentManager: FragmentManager) {
        parent.preferenceCategory(app.pachli.core.preferences.R.string.pref_title_translation) {
            enumListPreference<TranslationBackend> {
                // Figure out the default backend, and also reset it back to SERVER_ONLY
                // if the target language is not supported.
                val defaultBackend = if (canTranslateTo(sharedPreferencesRepository.languageExpandDefault) is Err) {
                    sharedPreferencesRepository.translationBackend = TranslationBackend.SERVER_ONLY
                    TranslationBackend.SERVER_ONLY
                } else {
                    TranslationBackend.SERVER_FIRST
                }
                setDefaultValue(defaultBackend)
                key = PrefKeys.TRANSLATION_BACKEND
                setTitle(app.pachli.core.preferences.R.string.pref_translation_backend)
            }

            switchPreference {
                setDefaultValue(true)
                key = PrefKeys.TRANSLATION_DOWNLOAD_REQUIRE_WIFI
                setTitle(R.string.pref_translation_require_wifi)
                isSingleLineTitle = false
            }

            preference {
                setTitle(R.string.pref_translation_manage_languages)
                fragment = TranslationModelManagerFragment::class.qualifiedName
            }

            preference {
                setTitle(R.string.fragment_google_disclaimer_dialog_title)
                setOnPreferenceClickListener {
                    GoogleDisclaimerDialogFragment().show(fragmentManager, "google_disclaimer")
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}
