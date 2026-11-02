/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.network.extensions

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.FileNotFoundException
import okio.Source
import okio.source

// Aligned with okio.Segment.SIZE (internal) for better performance
private const val DEFAULT_CHUNK_SIZE = 8192L

fun interface UploadCallback {
    fun onProgressUpdate(percentage: Int)
}

suspend fun Uri.asRequestBody(
    contentResolver: ContentResolver,
    contentType: MediaType? = null,
    contentLength: Long = -1L,
    uploadListener: UploadCallback? = null,
): RequestBody = withContext(Dispatchers.IO) {
    return@withContext object : RequestBody() {
        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = contentLength

        override fun writeTo(sink: BufferedSink) {
            val inputStream = contentResolver.openInputStream(this@asRequestBody)
                ?: throw FileNotFoundException("Unavailable ContentProvider")

            inputStream.source().use { source ->
                if (uploadListener == null) {
                    sink.writeAll(source)
                    return@use
                }

                if (contentLength == -1L) {
                    sink.writeAll(source)
                    uploadListener.onProgressUpdate(100)
                    return@use
                }

                sink.writeAll(source) { totalWritten ->
                    uploadListener.onProgressUpdate((100L * totalWritten / contentLength).toInt())
                }
                uploadListener.onProgressUpdate(100)
            }
        }
    }
}

/**
 * Write all bytes from [source] to [this], periodically calling [listener] with
 * a count of the total bytes written.
 */
fun BufferedSink.writeAll(source: Source, listener: (Long) -> Unit): Long {
    var totalBytesWritten = 0L
    while (true) {
        val readCount = source.read(buffer, DEFAULT_CHUNK_SIZE)
        if (readCount == -1L) break
        totalBytesWritten += readCount
        emitCompleteSegments()
        listener(totalBytesWritten)
    }
    return totalBytesWritten
}
