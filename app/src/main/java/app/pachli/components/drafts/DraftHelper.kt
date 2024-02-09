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
import androidx.core.net.toUri
import app.pachli.BuildConfig
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.database.model.DraftAttachment
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Status
import app.pachli.util.copyToFile
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

class DraftHelper @Inject constructor(
    @ApplicationContext val context: Context,
    private val okHttpClient: OkHttpClient,
    private val draftDao: DraftDao,
) {
    suspend fun saveDraft(
        draftId: Int,
        accountId: Long,
        inReplyToId: String?,
        content: String?,
        contentWarning: String?,
        sensitive: Boolean,
        visibility: Status.Visibility,
        mediaUris: List<String>,
        mediaDescriptions: List<String?>,
        mediaFocus: List<Attachment.Focus?>,
        poll: NewPoll?,
        failedToSend: Boolean,
        failedToSendAlert: Boolean,
        scheduledAt: String?,
        language: String?,
        statusId: String?,
    ) = withContext(Dispatchers.IO) {
        val externalFilesDir = context.getExternalFilesDir("Pachli")

        if (externalFilesDir == null || !(externalFilesDir.exists())) {
            Timber.e("Error obtaining directory to save media.")
            throw Exception()
        }

        val draftDirectory = File(externalFilesDir, "Drafts")

        if (!draftDirectory.exists()) {
            draftDirectory.mkdir()
        }

        val uris = mediaUris.map { uriString ->
            uriString.toUri()
        }.mapIndexedNotNull { index, uri ->
            if (uri.isInFolder(draftDirectory)) {
                uri
            } else {
                uri.copyToFolder(draftDirectory, index)
            }
        }

        val types = uris.map { uri ->
            val mimeType = context.contentResolver.getType(uri)
            when (mimeType?.substring(0, mimeType.indexOf('/'))) {
                "video" -> DraftAttachment.Type.VIDEO
                "image" -> DraftAttachment.Type.IMAGE
                "audio" -> DraftAttachment.Type.AUDIO
                else -> throw IllegalStateException("unknown media type")
            }
        }

        val attachments: MutableList<DraftAttachment> = mutableListOf()
        for (i in mediaUris.indices) {
            attachments.add(
                DraftAttachment(
                    uriString = uris[i].toString(),
                    description = mediaDescriptions[i],
                    focus = mediaFocus[i],
                    type = types[i],
                ),
            )
        }

        val draft = DraftEntity(
            id = draftId,
            accountId = accountId,
            inReplyToId = inReplyToId,
            content = content,
            contentWarning = contentWarning,
            sensitive = sensitive,
            visibility = visibility,
            attachments = attachments,
            poll = poll,
            failedToSend = failedToSend,
            failedToSendNew = failedToSendAlert,
            scheduledAt = scheduledAt,
            language = language,
            statusId = statusId,
        )

        draftDao.insertOrReplace(draft)
        Timber.d("saved draft to db")
    }

    suspend fun deleteDraftAndAttachments(draftId: Int) {
        draftDao.find(draftId)?.let { draft ->
            deleteDraftAndAttachments(draft)
        }
    }

    private suspend fun deleteDraftAndAttachments(draft: DraftEntity) {
        deleteAttachments(draft)
        draftDao.delete(draft.id)
    }

    suspend fun deleteAllDraftsAndAttachmentsForAccount(accountId: Long) {
        draftDao.loadDrafts(accountId).forEach { draft ->
            deleteDraftAndAttachments(draft)
        }
    }

    suspend fun deleteAttachments(draft: DraftEntity) = withContext(Dispatchers.IO) {
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uriString)
            }
        }
    }

    private fun Uri.isInFolder(folder: File): Boolean {
        val filePath = path ?: return true
        return File(filePath).parentFile == folder
    }

    private fun Uri.copyToFolder(folder: File, index: Int): Uri? {
        val contentResolver = context.contentResolver
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val fileExtension = if (scheme == "https") {
            lastPathSegment?.substringAfterLast('.', "tmp")
        } else {
            val mimeType = contentResolver.getType(this)
            val map = MimeTypeMap.getSingleton()
            map.getExtensionFromMimeType(mimeType)
        }

        val filename = String.format("Pachli_Draft_Media_%s_%d.%s", timeStamp, index, fileExtension)
        val file = File(folder, filename)

        if (scheme == "https") {
            // saving redrafted media
            try {
                val request = Request.Builder().url(toString()).build()

                val response = okHttpClient.newCall(request).execute()

                val sink = file.sink().buffer()

                response.body?.source()?.use { input ->
                    sink.use { output ->
                        output.writeAll(input)
                    }
                }
            } catch (ex: IOException) {
                Timber.w(ex, "failed to save media")
                return null
            }
        } else {
            this.copyToFile(contentResolver, file)
        }
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
    }
}
