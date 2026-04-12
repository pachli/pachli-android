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
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.core.common.PachliError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source

/** Errors returned by [Uri.saveToDirectory] and [Uri.saveToFile]. */
sealed interface SaveUriError : PachliError {
    /**
     * `contentResolver.openInputStream` returned null, indicating the content
     * provider crashed.
     */
    data object ContentProviderCrash : SaveUriError {
        override val resourceId = R.string.err_draft_error_content_provider_crash
        override val formatArgs = null
        override val cause = null
    }

    /**
     * [IOException] occurred saving the attachment.
     *
     * @property exception [IOException] thrown.
     */
    data class IO(val exception: IOException) : SaveUriError {
        override val resourceId = R.string.err_draft_error_io_fmt
        override val formatArgs = arrayOf(exception.localizedMessage ?: "")
        override val cause = null
    }
}

/**
 * @return True if the file identified by [Uri.path] exists in [directory].
 */
fun Uri.isInDirectory(directory: File): Boolean {
    val filePath = path ?: return true
    return File(filePath).parentFile == directory
}

/**
 * Copies the content of this [Uri] to a file in [directory].
 *
 * The saved file is named [basename]`.{ext}`.
 *
 * If [this.scheme][Uri.scheme] is `https` this is treated as a remote file, the
 * extension is derived from the URI's [lastPathSegment][Uri.getLastPathSegment] and
 * the URI contents are downloaded.
 *
 * Otherwise, this [Uri] is treated as a URI to a file to access through a
 * [ContentProvider][android.content.ContentProvider], and the extension is derived
 * from the URI's MIME type.
 *
 * @param context
 * @param callFactory Implementation of [Call.Factory] (e.g., [okhttp3.OkHttpClient])
 * that can request content from this [Uri].
 * @param directory The destination directory where the content will be saved.
 * @param basename Basename to use for the destination file.
 * @return [Ok] containing a content URI for the destination file if the copy was
 * successful, or [Err] containing a [SaveUriError] if the content provider
 * crashed or an [IOException] occurred.
 */
suspend fun Uri.saveToDirectory(context: Context, callFactory: Call.Factory, directory: File, basename: String): Result<Uri, SaveUriError> = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver

    val fileExtension = if (scheme == "https") {
        lastPathSegment?.substringAfterLast('.', "tmp")
    } else {
        val mimeType = contentResolver.getType(this@saveToDirectory)
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }

    val filename = "$basename.$fileExtension"
    val file = File(directory, filename)

    return@withContext try {
        // saving redrafted media
        if (scheme == "https") {
            val request = Request.Builder().url(toString()).build()
            callFactory.newCall(request).execute().use { response ->
                response.body.source().buffer.use { input ->
                    file.sink().buffer().use { it.writeAll(input) }
                }
            }
            Ok(Unit)
        } else {
            this@saveToDirectory.saveToFile(contentResolver, file)
        }
    } catch (ex: IOException) {
        Err(SaveUriError.IO(ex))
    }.map {
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
    }
}

/**
 * Copies the content of this [Uri] to [file] using the provided [contentResolver].
 *
 * @param contentResolver The [ContentResolver] used to open an input stream for this [Uri].
 * @param file The destination [File] where the content will be written.
 * @return [Ok] if the copy was successful, or [Err] containing a [SaveUriError].
 */
private suspend fun Uri.saveToFile(contentResolver: ContentResolver, file: File): Result<Unit, SaveUriError> = withContext(Dispatchers.IO) {
    try {
        return@withContext contentResolver.openInputStream(this@saveToFile)?.use { inputStream ->
            inputStream.use {
                it.source().buffer().use { source ->
                    file.sink().buffer().use { it.writeAll(source) }
                }
            }
            Ok(Unit)
        } ?: Err(SaveUriError.ContentProviderCrash)
    } catch (ex: IOException) {
        return@withContext Err(SaveUriError.IO(ex))
    }
}
