/* Copyright 2022 Tusky contributors
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

package app.pachli.adapter

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import app.pachli.util.getPachliDisplayName
import app.pachli.util.modernLanguageCode
import com.google.android.material.color.MaterialColors
import java.util.Locale

/**
 * Display a list of [Locale] in a spinner.
 *
 * At rest the locale is represented by the uppercase 2-3 character language code without
 * any subcategories ("EN", "DE", "ZH") etc.
 *
 * In the menu the locale is presented as "Local name (name)". E.g,. when the current
 * locale is English the German locale is displayed as "German (Deutsch)".
 */
class LocaleAdapter(context: Context, resource: Int, locales: List<Locale>) : ArrayAdapter<Locale>(context, resource, locales) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getView(position, convertView, parent) as TextView).apply {
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorTertiary))
            typeface = Typeface.DEFAULT_BOLD
            text = super.getItem(position)?.modernLanguageCode?.uppercase()
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getDropDownView(position, convertView, parent) as TextView).apply {
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorTertiary))
            text = super.getItem(position)?.getPachliDisplayName(context)
        }
    }
}
