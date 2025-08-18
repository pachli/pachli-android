/*
 * Copyright (c) 2025 Pachli Association
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

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import app.pachli.core.preferences.PreferenceEnum
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.preferences.TranslationBackend
import app.pachli.core.preferences.TranslationBackend.LOCAL_ONLY
import app.pachli.core.preferences.TranslationBackend.SERVER_FIRST
import app.pachli.core.preferences.TranslationBackend.SERVER_ONLY
import app.pachli.core.ui.BuildConfig
import app.pachli.core.ui.EnumListPreference
import app.pachli.core.ui.R
import app.pachli.translation.TranslationService
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TranslationBackendDialogFragment : ListPreferenceDialogFragmentCompat() {
    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var translationService: TranslationService

    private var clickedDialogEntryIndex = 0

    private val entries by lazy { (preference as EnumListPreference<TranslationBackend>).entries }
    private val entryValues by lazy { (preference as EnumListPreference<TranslationBackend>).entryValues }

    /**
     * Map each [TranslationBackend] to the [Result] with the informational labels that should be shown
     * for that backend..
     */
    private val backendLabels by lazy {
        TranslationBackend.entries.associateWith { backendLabel(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = preference as EnumListPreference<TranslationBackend>
            check(!(preference.entries == null || preference.entryValues == null)) {
                "ListPreference requires an entries array and an entryValues array."
            }
            clickedDialogEntryIndex = preference.findIndexOfValue(preference.value)
        } else {
            clickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0)
        }
    }

    /**
     * Determines whether the user should be able to select a specific backend, and the
     * informational label to display for that backend.
     *
     * A backend might not be selectable for several reasons:
     *
     * - The user might not have installed a variant with MlKit support.
     * - MlKit might not support the user's target language.
     *
     * If the backend *is* selectable the [Result] is an [Ok&lt;String>][Ok] containing
     * the label.
     *
     * If the backend *is not* selectable the [Result] is an [Err&lt;String][Err]
     * containing the error label.
     */
    private fun backendLabel(backend: TranslationBackend): Result<String, String> {
        // SERVER_ONLY is always supported.
        if (backend == SERVER_ONLY) return Ok(getString(app.pachli.core.preferences.R.string.pref_translation_backend_option_server_only_help))

        // Not a Google build? Every other backend is an error, with a label
        // explaining why.
        if (BuildConfig.FLAVOR_store != "google" && backend != SERVER_ONLY) {
            return Err(getString(app.pachli.core.preferences.R.string.pref_translation_backend_requires_play_label))
        }

        // Google builds might not support translating to the user's language.
        // Figure out the user's language and query the translationService to determine
        // if the language is OK.
        val targetLanguageTag = sharedPreferencesRepository.languageExpandDefault

        return translationService.canTranslateTo(targetLanguageTag).mapBoth(
            {
                when (backend) {
                    SERVER_ONLY -> Ok(getString(app.pachli.core.preferences.R.string.pref_translation_backend_option_server_only_help))
                    SERVER_FIRST -> Ok(getString(app.pachli.core.preferences.R.string.pref_translation_backend_option_server_first_help))
                    LOCAL_ONLY -> Ok(getString(app.pachli.core.preferences.R.string.pref_translation_backend_option_local_only_help))
                }
            },
            {
                when (backend) {
                    SERVER_ONLY -> Ok(getString(app.pachli.core.preferences.R.string.pref_translation_backend_option_server_only_help))
                    SERVER_FIRST -> Err(it.fmt(requireContext()))
                    LOCAL_ONLY -> Err(it.fmt(requireContext()))
                }
            },
        )
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        val context = requireContext()

        val adapter = object : ArrayAdapter<CharSequence>(context, R.layout.two_line_list_item_checked, android.R.id.text1, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text1 = view.findViewById<CheckedTextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)

                text1.isChecked = position == clickedDialogEntryIndex

                val backend = PreferenceEnum.Companion.from<TranslationBackend>(entryValues[position]) ?: SERVER_ONLY

                backendLabels[backend]!!
                    .onSuccess {
                        text1.isEnabled = true
                        text2.isEnabled = true
                        text2.text = it
                    }
                    .onFailure {
                        text1.isEnabled = false
                        text2.isEnabled = false
                        text2.text = it
                    }

                return view
            }
        }

        builder.setSingleChoiceItems(adapter, clickedDialogEntryIndex) { dialog, which ->
            if (which != 0 && BuildConfig.FLAVOR_store != "google") return@setSingleChoiceItems
            clickedDialogEntryIndex = which

            this@TranslationBackendDialogFragment.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
            dialog.dismiss()
        }

        // The typical interaction for list-based dialogs is to have click-on-an-item dismiss the
        // dialog instead of the user having to press 'Ok'.
        builder.setPositiveButton(null, null)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult && clickedDialogEntryIndex >= 0) {
            val value = entryValues[clickedDialogEntryIndex].toString()
            val preference = preference as ListPreference
            if (preference.callChangeListener(value)) {
                preference.value = value
            }
        }
    }

    companion object {
        const val TXN_TAG = "app.pachli.view.TranslationBackendDialogFragment"
        const val SAVE_STATE_INDEX = "TranslationBackendDialogFragment.index"

        fun newInstance(key: String): TranslationBackendDialogFragment {
            return TranslationBackendDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_KEY, key)
                }
            }
        }
    }
}
