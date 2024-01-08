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

package app.pachli.network

import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val content: InputStream,
    private val contentLength: Long,
    private val mediaType: MediaType,
    private val uploadListener: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType {
        return mediaType
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploaded: Long = 0
        content.use { content ->
            var read: Int
            while (content.read(buffer).also { read = it } != -1) {
                uploadListener((100 * uploaded / contentLength).toInt())
                uploaded += read.toLong()
                sink.write(buffer, 0, read)
            }
            uploadListener((100 * uploaded / contentLength).toInt())
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}
