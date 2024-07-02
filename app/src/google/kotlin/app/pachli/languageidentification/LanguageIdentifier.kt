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
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier as GoogleLanguageIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [LanguageIdentifier] that uses Google's ML Kit to perform the language
 * identification.
 */
class MlKitLanguageIdentifier private constructor() : LanguageIdentifier {
    private var client: GoogleLanguageIdentifier? = null

    init {
        client = LanguageIdentification.getClient()
    }

    override suspend fun identifyPossibleLanguages(text: String): Result<List<IdentifiedLanguage>, LanguageIdentifierError> {
        return client?.let { client ->
            // May throw an MlKitException, so catch and map error
            runSuspendCatching {
                client.identifyPossibleLanguages(text).await().map {
                    IdentifiedLanguage(
                        confidence = it.confidence,
                        languageTag = it.languageTag,
                    )
                }
            }.mapError { LanguageIdentifierError.Unknown(it) }
        } ?: Err(LanguageIdentifierError.UseAfterClose)
    }

    override fun close() {
        client?.close()
        client = null
    }

    /**
     * Factory for LanguageIdentifer based on Google's ML Kit.
     *
     * When the factory is constructed a [com.google.android.gms.tasks.Task]
     * to check and install the language module is launched, increasing the
     * chances the module will be installed before it is first used.
     */
    class Factory(
        private val externalScope: CoroutineScope,
        private val context: Context,
    ) : LanguageIdentifier.Factory() {
        private val moduleInstallClient = ModuleInstall.getClient(context)

        init {
            LanguageIdentification.getClient().use { langIdClient ->
                val moduleInstallRequest = ModuleInstallRequest.newBuilder()
                    .addApi(langIdClient)
                    .build()

                moduleInstallClient.installModules(moduleInstallRequest)
            }
        }

        /**
         * Returns a [MlKitLanguageIdentifier] if the relevant modules are
         * installed, defers to [DefaultLanguageIdentifierFactory] if not.
         */
        override suspend fun newInstance(): LanguageIdentifier {
            LanguageIdentification.getClient().use { langIdClient ->
                val modulesAreAvailable = moduleInstallClient
                    .areModulesAvailable(langIdClient)
                    .await()
                    .areModulesAvailable()

                return if (modulesAreAvailable) {
                    Timber.d("mlkit langid module available")
                    MlKitLanguageIdentifier()
                } else {
                    Timber.d("mlkit langid module *not* available")
                    DefaultLanguageIdentifierFactory(externalScope, context)
                        .newInstance()
                }
            }
        }
    }
}
