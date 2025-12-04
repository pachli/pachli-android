/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.drafts

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.pachli.BuildConfig
import app.pachli.components.compose.ComposeActivity
import app.pachli.core.common.PachliError
import app.pachli.core.model.Attachment
import app.pachli.core.model.DraftAttachment
import app.pachli.util.copyToFile
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber

sealed interface DraftError : PachliError {
    data object NoExternalFilesDir : DraftError {
        override val resourceId: Int
            get() = TODO("Not yet implemented")
        override val formatArgs: Array<out Any>? = null
        override val cause: PachliError? = null
    }

    data object MkdirDrafts : DraftError {
        override val resourceId: Int
            get() = TODO("Not yet implemented")
        override val formatArgs: Array<out Any>? = null
        override val cause: PachliError? = null
    }

    data class ThrowableError(val throwable: Throwable) : DraftError {
        override val resourceId: Int
            get() = TODO("Not yet implemented")
        override val formatArgs: Array<out Any>?
            get() = TODO("Not yet implemented")
        override val cause: PachliError? = null
    }
}

class DraftHelper @Inject constructor(
    @ApplicationContext val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun saveAttachments(media: List<ComposeActivity.QueuedMedia>): Result<List<DraftAttachment>, DraftError> {
        if (media.isEmpty()) return Ok(emptyList())

        val externalFilesDir = context.getExternalFilesDir("Pachli")
        if (externalFilesDir == null || !(externalFilesDir.exists())) {
            Timber.e("Error obtaining directory to save media.")
            return Err(DraftError.NoExternalFilesDir)
        }

        val draftDirectory = File(externalFilesDir, "Drafts")

        runCatching { draftDirectory.mkdir() }.getOrElse { return Err(DraftError.MkdirDrafts) }

        val draftAttachments = media.mapIndexed { index, item ->
            val uri = if (item.uri.isInFolder(draftDirectory)) {
                item.uri
            } else {
                item.uri.copyToFolder(draftDirectory, index).getOrElse { return Err(it) }
            }

            val mimeType = context.contentResolver.getType(uri)
            val attachmentType = when (mimeType?.substring(0, mimeType.indexOf('/'))) {
                "video" -> Attachment.Type.VIDEO
                "image" -> Attachment.Type.IMAGE
                "audio" -> Attachment.Type.AUDIO
                else -> throw IllegalStateException("unknown media type")
            }

            DraftAttachment(
                uri = uri,
                description = item.description,
                type = attachmentType,
                focus = item.focus,
            )
        }

        return Ok(draftAttachments)
    }

    private fun Uri.isInFolder(folder: File): Boolean {
        val filePath = path ?: return true
        return File(filePath).parentFile == folder
    }

    private suspend fun Uri.copyToFolder(folder: File, index: Int): Result<Uri, DraftError.ThrowableError> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val fileExtension = if (scheme == "https") {
            lastPathSegment?.substringAfterLast('.', "tmp")
        } else {
            val mimeType = contentResolver.getType(this@copyToFolder)
            val map = MimeTypeMap.getSingleton()
            map.getExtensionFromMimeType(mimeType)
        }

        val filename = "Pachli_Draft_Media_${timeStamp}_$index.$fileExtension"
        val file = File(folder, filename)

        if (scheme == "https") {
            // saving redrafted media
            try {
                val request = Request.Builder().url(toString()).build()
                val response = okHttpClient.newCall(request).execute()

                response.body?.source()?.buffer?.use { input ->
                    file.sink().buffer().use { it.writeAll(input) }
                }
            } catch (ex: IOException) {
                Timber.w(ex, "failed to save media")
                return@withContext Err(DraftError.ThrowableError(ex))
            }
        } else {
            this@copyToFolder.copyToFile(contentResolver, file)
        }
        return@withContext Ok(FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file))
    }
}
