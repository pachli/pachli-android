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
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.plus

/** The BCP 47 language tag for "undetermined language". */
const val UNDETERMINED_LANGUAGE_TAG = "und"

/**
 * A language identified by [LanguageIdentifier.identifyPossibleLanguages].
 */
data class IdentifiedLanguage(
    /** Confidence score associated with the identification. */
    val confidence: Float,
    /** BCP 47 language tag for the identified language. */
    val languageTag: String,
)

sealed class LanguageIdentifierError(
    @StringRes override val resourceId: Int = -1,
    override val formatArgs: Array<out String>? = null,
    override val cause: PachliError? = null,
) : PachliError {

    /**
     * Called [LanguageIdentifier.identifyPossibleLanguages] after calling
     * [LanguageIdentifier.close].
     */
    data object UseAfterClose : LanguageIdentifierError()
    data class Unknown(val throwable: Throwable) : LanguageIdentifierError()
}

interface LanguageIdentifier : AutoCloseable {
    /**
     * Identifies the language in [text] and returns a list of possible
     * languages.
     *
     * @return A non-empty list of identified languages in [text]. If no
     * languages were identified a list with an item with the languageTag
     * set to [UNDETERMINED_LANGUAGE_TAG] is used.
     */
    suspend fun identifyPossibleLanguages(text: String): Result<List<IdentifiedLanguage>, LanguageIdentifierError>

    // Language identifiers may consume a lot of resources while in use, so they
    // cannot be treated as singletons that can be injected and can persist as long
    // as an activity remains. They may also require resource cleanup, which is
    // impossible to guarantee using activity lifecycle methods (onDestroy etc).
    //
    // So instead of injecting the language identifier, inject a factory for creating
    // language identifiers. It is the responsibility of the calling code to use the
    // factory to create the language identifier, and then close the language
    // identifier when finished.
    abstract class Factory {
        abstract suspend fun newInstance(): LanguageIdentifier
    }
}

/**
 * [LanguageIdentifier] that uses Android's [TextClassificationManager], available
 * on API 29 and above, to identify the language.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class Api29LanguageIdentifier(
    private val externalScope: CoroutineScope,
    val context: Context,
) : LanguageIdentifier {
    private val textClassificationManager: TextClassificationManager = context.getSystemService(TextClassificationManager::class.java)
    private var textClassifier: TextClassifier? = textClassificationManager.textClassifier

    override suspend fun identifyPossibleLanguages(text: String): Result<List<IdentifiedLanguage>, LanguageIdentifierError> = (externalScope + Dispatchers.IO).async {
        val textRequest = TextLanguage.Request.Builder(text).build()

        textClassifier?.detectLanguage(textRequest)?.let { detectedLanguage ->
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
        }.toResultOr { LanguageIdentifierError.UseAfterClose }
    }.await()

    override fun close() {
        textClassifier = null
    }
}

/**
 * [LanguageIdentifier] that always returns [UNDETERMINED_LANGUAGE_TAG].
 *
 * Use when no other language identifier is available.
 */
object NopLanguageIdentifier : LanguageIdentifier {
    private var closed = false
    override suspend fun identifyPossibleLanguages(text: String): Result<List<IdentifiedLanguage>, LanguageIdentifierError> = if (closed) {
        Err(LanguageIdentifierError.UseAfterClose)
    } else {
        Ok(listOf(IdentifiedLanguage(confidence = 1f, languageTag = UNDETERMINED_LANGUAGE_TAG)))
    }

    override fun close() {
        closed = true
    }
}

/**
 * [LanguageIdentifier.Factory] that creates [Api29LanguageIdentifier] if available,
 * [NopLanguageIdentifier] otherwise.
 */
class DefaultLanguageIdentifierFactory(
    private val externalScope: CoroutineScope,
    val context: Context,
) : LanguageIdentifier.Factory() {
    override suspend fun newInstance(): LanguageIdentifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Api29LanguageIdentifier(externalScope, context)
    } else {
        NopLanguageIdentifier
    }
}
