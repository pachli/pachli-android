/* Copyright 2019 Mélanie Chauvel (ariasuni)
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

package app.pachli.util

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceDataStore
import app.pachli.R
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.getNonNullString
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : PreferenceDataStore() {

    fun setLocale() {
        val language = sharedPreferencesRepository.getNonNullString(PrefKeys.LANGUAGE, DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (language != HANDLED_BY_SYSTEM) {
                // app is being opened on Android 13+ for the first time
                // hand over the old setting to the system and save a dummy value in Shared Preferences
                applyLanguageToApp(language)

                sharedPreferencesRepository.edit()
                    .putString(PrefKeys.LANGUAGE, HANDLED_BY_SYSTEM)
                    .apply()
            }
        } else {
            // on Android < 13 we have to apply the language at every app start
            applyLanguageToApp(language)
        }
    }

    override fun putString(key: String?, value: String?) {
        // if we are on Android < 13 we have to save the selected language so we can apply it at appstart
        // on Android 13+ the system handles it for us
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            sharedPreferencesRepository.edit()
                .putString(PrefKeys.LANGUAGE, value)
                .apply()
        }
        applyLanguageToApp(value)
    }

    override fun getString(key: String?, defValue: String?): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val selectedLanguage = AppCompatDelegate.getApplicationLocales()

            if (selectedLanguage.isEmpty) {
                DEFAULT
            } else {
                // Android lets users select all variants of languages we support in the system settings,
                // so we need to find the closest match
                // it should not happen that we find no match, but returning null is fine (picker will show default)

                val availableLanguages = context.resources.getStringArray(R.array.language_values)

                return availableLanguages.find { it == selectedLanguage[0]!!.toLanguageTag() }
                    ?: availableLanguages.find { language ->
                        language.startsWith(selectedLanguage[0]!!.language)
                    }
            }
        } else {
            sharedPreferencesRepository.getNonNullString(PrefKeys.LANGUAGE, DEFAULT)
        }
    }

    private fun applyLanguageToApp(language: String?) {
        val localeList = if (language == DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }

        AppCompatDelegate.setApplicationLocales(localeList)
    }

    companion object {
        private const val DEFAULT = "default"
        private const val HANDLED_BY_SYSTEM = "handled_by_system"
    }
}
