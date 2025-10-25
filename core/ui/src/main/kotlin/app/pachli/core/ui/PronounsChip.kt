/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import com.google.android.material.chip.Chip

/**
 * A [Chip] for showing an account's pronouns.
 *
 * The Chip hides itself if [setText] is passed a null or empty value.
 *
 * Otherwise the chip shows itself, formats the text as HTML, and
 * displays. A click handler is set to show a Toast with the text,
 * in case the text overflows the available space.
 */
open class PronounsChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = app.pachli.core.designsystem.R.attr.pronounsChipStyle,
) : Chip(context, attrs, defStyleAttr) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        if (text.isNullOrBlank()) {
            hide()
        } else {
            val formatted = HtmlCompat.fromHtml(text.toString().trim(), FROM_HTML_MODE_LEGACY)
            super.setText(formatted, type)
            setOnClickListener { Toast.makeText(context, formatted, Toast.LENGTH_LONG).show() }
            show()
        }
    }
}
