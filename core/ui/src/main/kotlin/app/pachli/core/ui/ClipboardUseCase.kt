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

package app.pachli.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Copies plain text to the clipboard as the primary clip.
 */
class ClipboardUseCase @Inject constructor(
    @ApplicationContext val context: Context,
) {
    private val clipboard: ClipboardManager by lazy {
        ContextCompat.getSystemService(
            context,
            ClipboardManager::class.java,
        ) as ClipboardManager
    }

    /**
     * Copies [text] to the clipboard as the primary clip, with optional
     * [label]. If necessary displays a toast showing [message] to confirm
     * copy is complete.
     *
     * @param text Text to copy.
     * @param message Optional message to show after completion.
     * @param label Optional user-visible label to associate with the copied
     * text, see [ClipData.newPlainText].
     */
    fun copyTextTo(
        text: CharSequence,
        @StringRes message: Int = R.string.item_copied,
        label: CharSequence = "",
    ) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        notify(message)
    }

    private fun notify(@StringRes message: Int) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        }
    }
}
