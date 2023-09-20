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

package app.pachli.util

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val DEFAULT_BLOCKSIZE = 16384

fun Uri.copyToFile(
    contentResolver: ContentResolver,
    file: File,
): Boolean {
    try {
        contentResolver.openInputStream(this).use { from ->
            from ?: return false

            FileOutputStream(file).use { to ->
                val chunk = ByteArray(DEFAULT_BLOCKSIZE)
                while (true) {
                    val bytes = from.read(chunk, 0, chunk.size)
                    if (bytes < 0) break
                    to.write(chunk, 0, bytes)
                }
            }
        }
    } catch (e: FileNotFoundException) {
        return false
    }

    return true
}
