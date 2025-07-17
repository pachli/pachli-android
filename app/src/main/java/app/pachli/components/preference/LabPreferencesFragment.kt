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

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.pachli.R
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.ExportedPreferencesRepository
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.databinding.FragmentLabPreferencesBinding
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preference
import app.pachli.settings.switchPreference
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/** MIME type for exported preference files. */
private const val PREFERENCES_MIME_TYPE = "application/json"

@AndroidEntryPoint
class LabPreferencesFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var contentResolver: ContentResolver

    @Inject
    lateinit var exportedPreferencesRepository: ExportedPreferencesRepository

    private val binding by viewBinding(FragmentLabPreferencesBinding::bind)

    /**
     * Prompts the user for a file to export preferences to, exports the
     * preferences, and shows a success or error snackbar.
     */
    private val exportPreferences = registerForActivityResult(ActivityResultContracts.CreateDocument(PREFERENCES_MIME_TYPE)) {
        it?.let { uri ->
            val filename = uri.resolveName(contentResolver)
            viewLifecycleOwner.lifecycleScope.launch {
                exportedPreferencesRepository.export(uri)
                    .onSuccess {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.export_preferences_ok_fmt, filename),
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                    .onFailure {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.error_export_preferences_fmt, filename, it.fmt(binding.root.context)),
                            Snackbar.LENGTH_INDEFINITE,
                        ).show()
                    }
            }
        }
    }

    /**
     * Prompts the user for a file to import preferences from, imports the
     * preferences, and shows a success or error snackbar.
     */
    private val importPreferences = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                val name = uri.resolveName(contentResolver)
                exportedPreferencesRepository.import(uri)
                    .onFailure {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.error_import_preferences_fmt, name, it.fmt(binding.root.context)),
                            Snackbar.LENGTH_INDEFINITE,
                        ).show()
                    }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Fetch the default view, which contains the list of preferences.
        val defaultView = super.onCreateView(inflater, container, savedInstanceState)

        // Construct the final view by taking the custom layout and appending the default view
        // to the end.
        val finalView = FragmentLabPreferencesBinding.inflate(inflater, container, false).root.apply {
            addView(defaultView)
        }

        finalView.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
        )

        return finalView
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

            switchPreference {
                key = PrefKeys.LAB_RENDER_MARKDOWN
                setTitle(app.pachli.core.preferences.R.string.pref_title_render_markdown)
                isIconSpaceReserved = false
            }

            preference {
                setTitle(app.pachli.core.preferences.R.string.pref_title_export_settings)
                setSummary(app.pachli.core.preferences.R.string.pref_summary_export_settings)
                isIconSpaceReserved = false

                setOnPreferenceClickListener {
                    exportPreferences.launch(getExportFileName())
                    true
                }
            }

            preference {
                setTitle(app.pachli.core.preferences.R.string.pref_title_import_settings)
                setSummary(app.pachli.core.preferences.R.string.pref_summary_import_settings)
                isIconSpaceReserved = false

                setOnPreferenceClickListener {
                    importPreferences.launch(arrayOf(PREFERENCES_MIME_TYPE))
                    true
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.applyDefaultWindowInsets()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_labs)
    }

    companion object {
        fun newInstance() = LabPreferencesFragment()

        /**
         * @return Default filename when exporting preferences. Includes
         * the current date and time for ease of identification and stable
         * sorting.
         */
        fun getExportFileName() = "pachli-preferences-${SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).format(Date())}.json"
    }
}

/**
 * Resolves a `content://` URI to the local filename.
 *
 * @param contentResolver ContentResolver to use to perform the resolution.
 */
private fun Uri.resolveName(contentResolver: ContentResolver): String? {
    return contentResolver.query(this, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null

        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        cursor.getString(name)
    }
}
