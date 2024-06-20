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
import java.io.IOException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.FileNotFoundException
import okio.buffer
import okio.sink
import okio.source

fun Uri.copyToFile(contentResolver: ContentResolver, file: File): Boolean {
    return try {
        val inputStream = contentResolver.openInputStream(this) ?: return false
        inputStream.source().buffer().use { source ->
            file.sink().buffer().use { it.writeAll(source) }
        }
        true
    } catch (e: IOException) {
        false
    }
}

// Aligned with okio.Segment.SIZE (internal) for better performance
private const val DEFAULT_CHUNK_SIZE = 8192L

fun interface UploadCallback {
    fun onProgressUpdate(percentage: Int)
}

fun Uri.asRequestBody(
    contentResolver: ContentResolver,
    contentType: MediaType? = null,
    contentLength: Long = -1L,
    uploadListener: UploadCallback? = null,
): RequestBody {
    return object : RequestBody() {
        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = contentLength

        override fun writeTo(sink: BufferedSink) {
            val buffer = Buffer()
            var uploaded: Long = 0
            val inputStream = contentResolver.openInputStream(this@asRequestBody)
                ?: throw FileNotFoundException("Unavailable ContentProvider")

            inputStream.source().use { source ->
                while (true) {
                    val read = source.read(buffer, DEFAULT_CHUNK_SIZE)
                    if (read == -1L) {
                        break
                    }
                    sink.write(buffer, read)
                    uploaded += read
                    uploadListener?.let { if (contentLength > 0L) it.onProgressUpdate((100L * uploaded / contentLength).toInt()) }
                }
                uploadListener?.onProgressUpdate(100)
            }
        }
    }
}
