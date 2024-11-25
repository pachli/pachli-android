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

package app.pachli.components.preference

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.pachli.R
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.databinding.FragmentLabPreferencesWarningBinding
import app.pachli.settings.enumListPreference
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.switchPreference

class LabPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val defaultView = super.onCreateView(inflater, container, savedInstanceState)

        // Insert a warning message as the first child in the layout.
        val warningBinding = FragmentLabPreferencesWarningBinding.inflate(inflater, null, false)
        (defaultView as? ViewGroup)?.addView(warningBinding.root, 0)

        return defaultView
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()

        makePreferenceScreen {
            switchPreference {
                key = PrefKeys.LAB_REVERSE_TIMELINE
                setTitle(R.string.pref_labs_reverse_home_timeline_title)
                setSummaryProvider {
                    if ((it as SwitchPreferenceCompat).isChecked) {
                        context.getString(R.string.pref_labs_reverse_home_timeline_on_summary)
                    } else {
                        context.getString(R.string.pref_labs_reverse_home_timeline_off_summary)
                    }
                }
                isIconSpaceReserved = false
            }

            enumListPreference<TabTapBehaviour> {
                setDefaultValue(TabTapBehaviour.JUMP_TO_NEXT_PAGE)
                setTitle(app.pachli.core.preferences.R.string.pref_title_tab_tap)
                key = PrefKeys.TAB_TAP_BEHAVIOUR
                isIconSpaceReserved = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_labs)
    }

    companion object {
        fun newInstance() = LabPreferencesFragment()
    }
}
