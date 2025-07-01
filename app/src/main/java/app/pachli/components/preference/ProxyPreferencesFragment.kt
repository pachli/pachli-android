/* Copyright 2018 Conny Duck

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

package app.pachli.components.preference

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.pachli.R
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.ProxyConfiguration
import app.pachli.core.preferences.ProxyConfiguration.Companion.MAX_PROXY_PORT
import app.pachli.core.preferences.ProxyConfiguration.Companion.MIN_PROXY_PORT
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preferenceCategory
import app.pachli.settings.switchPreference
import app.pachli.settings.validatedEditTextPreference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

class ProxyPreferencesFragment : PreferenceFragmentCompat() {
    private var pendingRestart = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            switchPreference {
                setTitle(R.string.pref_title_http_proxy_enable)
                isIconSpaceReserved = false
                key = PrefKeys.HTTP_PROXY_ENABLED
                setDefaultValue(false)
            }

            preferenceCategory { category ->
                category.dependency = PrefKeys.HTTP_PROXY_ENABLED
                category.isIconSpaceReserved = false

                validatedEditTextPreference(null, ProxyConfiguration::isValidHostname) {
                    setTitle(R.string.pref_title_http_proxy_server)
                    key = PrefKeys.HTTP_PROXY_SERVER
                    isIconSpaceReserved = false
                    setSummaryProvider { text }
                }

                val portErrorMessage = getString(
                    R.string.pref_title_http_proxy_port_message,
                    MIN_PROXY_PORT,
                    MAX_PROXY_PORT,
                )

                validatedEditTextPreference(portErrorMessage, ProxyConfiguration::isValidProxyPort) {
                    setTitle(R.string.pref_title_http_proxy_port)
                    key = PrefKeys.HTTP_PROXY_PORT
                    isIconSpaceReserved = false
                    setSummaryProvider { text }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_http_proxy_settings)
    }

    override fun onPause() {
        super.onPause()
        if (pendingRestart) {
            pendingRestart = false
            exitProcess(0)
        }
    }

    @Singleton
    class SummaryProvider @Inject constructor(
        private val sharedPreferencesRepository: SharedPreferencesRepository,
    ) : Preference.SummaryProvider<Preference> {
        override fun provideSummary(preference: Preference): CharSequence {
            if (!sharedPreferencesRepository.httpProxyEnabled) {
                return preference.context.getString(R.string.pref_summary_http_proxy_disabled)
            }

            val missing = preference.context.getString(R.string.pref_summary_http_proxy_missing)

            val server = sharedPreferencesRepository.httpProxyServer ?: missing
            val port = sharedPreferencesRepository.httpProxyPort

            if (port < MIN_PROXY_PORT || port > MAX_PROXY_PORT) {
                val invalid = preference.context.getString(R.string.pref_summary_http_proxy_invalid)
                return "$server:$invalid"
            }

            return "$server:$port"
        }
    }

    companion object {
        fun newInstance(): ProxyPreferencesFragment {
            return ProxyPreferencesFragment()
        }
    }
}
