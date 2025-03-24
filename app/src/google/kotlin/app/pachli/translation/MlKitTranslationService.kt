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

import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.model.translation.TranslatedAttachment
import app.pachli.core.model.translation.TranslatedPoll
import app.pachli.core.model.translation.TranslatedPollOption
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.languageidentification.LanguageIdentifier
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [TranslationService] that uses Google's ML Kit to perform the translation.
 */
class MlKitTranslationService(private val languageIdentifierFactory: LanguageIdentifier.Factory) : TranslationService {
    override fun canTranslate(statusViewData: IStatusViewData) = true

    override suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError> {
        // Identify the source language
        // Use all the possible text in the status to maximise the chance the correct
        // language is identified.
        val textToIdentify = buildString {
            with(statusViewData.status) {
                if (spoilerText.isNotEmpty()) {
                    append(spoilerText)
                    append("\n\n")
                }

                if (content.isNotEmpty()) {
                    append(content)
                    append("\n\n")
                }

                poll?.let {
                    it.options.forEach {
                        append(it)
                        append("\n")
                    }
                    append("\n")
                }

                attachments.forEach {
                    it.description?.let {
                        append(it)
                        append("\n")
                    }
                }
            }
        }

        // TODO
        // - Check for errors
        // - Check for no detected language
        // - Check source language is not handled
        val detectedLanguages = languageIdentifierFactory.newInstance().identifyPossibleLanguages(textToIdentify).get()
        Timber.d("detected: $detectedLanguages")
        val languageTag = detectedLanguages!!.first()
        val sourceLanguage = TranslateLanguage.fromLanguageTag(languageTag.languageTag)!!

        Timber.d("sourceLanguage: $sourceLanguage")
        // Build the client
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()

        Timber.d("options: $options")
        // TODO: Preference to only download on Wi-Fi
        val conditions = DownloadConditions.Builder().build()

        // Perform the translation
        val client = Translation.getClient(options)

        client.downloadModelIfNeeded(conditions).await()

        Timber.d("source: ${statusViewData.content}")

        val result = runSuspendCatching {
            return@runSuspendCatching TranslatedStatus(
                spoilerText = client.translate(statusViewData.spoilerText).await(),
                content = client.translate(statusViewData.content.toString()).await(),
                detectedSourceLanguage = sourceLanguage,
                poll = statusViewData.status.poll?.let { poll ->
                    TranslatedPoll(
                        id = poll.id,
                        options = poll.options.map {
                            TranslatedPollOption(
                                title = client.translate(it.title).await(),
                            )
                        },
                    )
                },
                attachments = statusViewData.status.attachments.map { attachment ->
                    TranslatedAttachment(
                        id = attachment.id,
                        description = attachment.description?.let { client.translate(it).await() },
                    )
                },
                provider = "On-device",
            )
        }.mapError { TranslatorError.ThrowableError(it) }
        Timber.d("result: $result")

        client.close()

        return result
    }
}
