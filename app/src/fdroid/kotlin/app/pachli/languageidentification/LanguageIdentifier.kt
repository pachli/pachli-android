/*
 * Copyright 2024 Pachli Association
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

package app.pachli.languageidentification

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.plus

@RequiresApi(Build.VERSION_CODES.Q)
class Api29LanguageIdentifier(
    private val externalScope: CoroutineScope,
    val context: Context,
) : LanguageIdentifierBase {
    override suspend fun identifyPossibleLanguages(text: String): List<IdentifiedLanguage> = (externalScope + Dispatchers.IO).async {
        val textClassificationManager = context.getSystemService(TextClassificationManager::class.java)
        val textClassifier = textClassificationManager.textClassifier

        val textRequest = TextLanguage.Request.Builder(text).build()
        val detectedLanguage = textClassifier.detectLanguage(textRequest)

        buildList {
            for (i in 0 until detectedLanguage.localeHypothesisCount) {
                val localeDetected = detectedLanguage.getLocale(i)
                val confidence = detectedLanguage.getConfidenceScore(localeDetected)

                add(
                    IdentifiedLanguage(
                        confidence = confidence,
                        languageTag = localeDetected.toLanguageTag(),
                    ),
                )
            }
        }
    }.await()

    override fun close() { }
}

class Factory(
    private val externalScope: CoroutineScope,
    val context: Context,
) : LanguageIdentifierFactory() {
    override fun newInstance(): LanguageIdentifierBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Api29LanguageIdentifier(externalScope, context)
    } else {
        NopLanguageIdentifier
    }
}

/**
 * LanguageIdentifer that always returns [UNDETERMINED_LANGUAGE_TAG].
 */
object NopLanguageIdentifier : LanguageIdentifierBase {
    override suspend fun identifyPossibleLanguages(text: String) = listOf(IdentifiedLanguage(confidence = 1f, languageTag = UNDETERMINED_LANGUAGE_TAG))
    override fun close() { }
}
