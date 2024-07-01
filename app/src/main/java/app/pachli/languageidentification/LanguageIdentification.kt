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

import java.io.Closeable

/** The BCP 47 language tag for "undetermined language". */
const val UNDETERMINED_LANGUAGE_TAG = "und"

/**
 * A language identified by [LanguageIdentifierBase.identifyPossibleLanguages].
 */
data class IdentifiedLanguage(
    /** Confidence score associated with the identification. */
    val confidence: Float,
    /** BCP 47 language tag for the identified language. */
    val languageTag: String,
)

interface LanguageIdentifierBase :
    Closeable,
    AutoCloseable {
    /**
     * Identifies the language in [text] and returns a list of possible
     * languages.
     *
     * @return A non-empty list of identified languages in [text]. If no
     * languages were identified a list with an item with the languageTag
     * set to [UNDETERMINED_LANGUAGE_TAG] is used.
     */
    suspend fun identifyPossibleLanguages(text: String): List<IdentifiedLanguage>
}

// Language identifiers may consume a lot of resources while in use, so they
// cannot be treated as singletons that can be injected and can persist as long
// as an activity remains. They may also require resource cleanup, which is
// impossible to guarantee using activity lifecycle methods (onDestroy etc).
//
// So instead of injecting the language identifier, inject a factory for creating
// language identifiers. It is the responsibility of the calling code to use the
// factory to create the language identifier, and then close the language
// identifier when finished.
abstract class LanguageIdentifierFactory {
    abstract fun newInstance(): LanguageIdentifier
}
