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
import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse.AvailabilityStatus.STATUS_READY_TO_DOWNLOAD
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * LanguageIdentifer that uses Google's ML Kit to perform the language identification.
 *
 * If the ML Kit module is not installed on the device it will be installed on
 * first use.
 */
class LanguageIdentifier(
    private val moduleInstallClient: ModuleInstallClient,
    private val googleLanguageIdentifier: LanguageIdentifier,
) : LanguageIdentifierBase {
    override suspend fun identifyPossibleLanguages(text: String): List<IdentifiedLanguage> {
        val modulesAvailable = moduleInstallClient
            .areModulesAvailable(googleLanguageIdentifier)
            .await()
            .areModulesAvailable()

        return if (modulesAvailable) {
            googleLanguageIdentifier.identifyPossibleLanguages(text).await().map {
                IdentifiedLanguage(
                    confidence = it.confidence,
                    languageTag = it.languageTag,
                )
            }
        } else {
            Timber.d("langid module not installed yet")
            listOf(IdentifiedLanguage(confidence = 1f, languageTag = UNDETERMINED_LANGUAGE_TAG))
        }
    }

    override fun close() {
        googleLanguageIdentifier.close()
    }

    /**
     * Factory for LanguageIdentifer based on Google's ML Kit.
     *
     * When the factory is constructed a coroutine to check and install the
     * language module is launched, increasing the chances the module will
     * be installed before it is first used.
     */
    class Factory(
        externalScope: CoroutineScope,
        context: Context,
    ) : LanguageIdentifierFactory() {
        private val moduleInstallClient = ModuleInstall.getClient(context)

        init {
            externalScope.launch {
                LanguageIdentification.getClient().use { langIdClient ->
                    val availablityStatus = moduleInstallClient
                        .areModulesAvailable(langIdClient)
                        .await()
                        .availabilityStatus

                    if (availablityStatus != STATUS_READY_TO_DOWNLOAD) return@launch
                    Timber.d("langid module not installed, requesting download")
                    val moduleInstallRequest = ModuleInstallRequest.newBuilder()
                        .addApi(langIdClient)
                        .build()
                    moduleInstallClient.installModules(moduleInstallRequest)
                        .addOnSuccessListener { Timber.d("langid module installed") }
                        .addOnFailureListener { e -> Timber.d(e, "langid module install failed") }
                }
            }
        }

        override fun newInstance() = LanguageIdentifier(moduleInstallClient, LanguageIdentification.getClient())
    }
}
