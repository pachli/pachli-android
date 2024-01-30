/*
 * Copyright 2023 Pachli Association
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

package app.pachli.view

import android.content.DialogInterface.BUTTON_POSITIVE
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.R
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import app.pachli.core.designsystem.EmbeddedFontFamily

/**
 * Dialog fragment for choosing a font family. Displays the list of font families with each
 * entry in its font.
 */
class FontFamilyDialogFragment : ListPreferenceDialogFragmentCompat() {
    private var clickedDialogEntryIndex = 0
    private lateinit var entries: Array<CharSequence>
    private lateinit var entryValues: Array<CharSequence>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = preference as ListPreference
            check(!(preference.entries == null || preference.entryValues == null)) {
                "ListPreference requires an entries array and an entryValues array."
            }
            clickedDialogEntryIndex = preference.findIndexOfValue(preference.value)
            entries = preference.entries
            entryValues = preference.entryValues
        } else {
            clickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0)
            entries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES) as Array<CharSequence>
            entryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES) as Array<CharSequence>
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        val context = requireContext()

        // Use the same layout AlertDialog uses, as android.R.layout.simple_list_item_single_choice
        // puts the radio button at the end of the line, but the default dialog style puts it at
        // the start.
        val a = context.obtainStyledAttributes(
            null,
            R.styleable.AlertDialog,
            R.attr.alertDialogStyle,
            0,
        )
        val layout = a.getResourceId(R.styleable.AlertDialog_singleChoiceItemLayout, 0)
        a.recycle()

        val adapter = object : ArrayAdapter<CharSequence>(context, layout, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)

                val fontFamily = EmbeddedFontFamily.from(entryValues[position].toString())
                if (fontFamily == EmbeddedFontFamily.DEFAULT) {
                    (view as TextView).typeface = Typeface.DEFAULT
                } else {
                    (view as TextView).setTextAppearance(fontFamily.style)
                }
                return view
            }
        }

        builder.setSingleChoiceItems(adapter, clickedDialogEntryIndex) { dialog, which ->
            clickedDialogEntryIndex = which
            this@FontFamilyDialogFragment.onClick(dialog, BUTTON_POSITIVE)
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
        const val TXN_TAG = "app.pachli.view.FontFamilyDialogFragment"
        const val SAVE_STATE_INDEX = "FontFamilyDialogFragment.index"
        const val SAVE_STATE_ENTRIES = "FontFamilyDialogFragment.entries"
        const val SAVE_STATE_ENTRY_VALUES = "FontFamilyDialogFragment.entryValues"

        fun newInstance(key: String): FontFamilyDialogFragment {
            val fragment = FontFamilyDialogFragment()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}
