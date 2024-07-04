/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.compose

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.components.compose.ComposeActivity.QueuedMedia
import app.pachli.components.compose.MediaUploaderError.PrepareMediaError
import app.pachli.core.common.PachliError
import app.pachli.core.common.string.randomAlphanumericString
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.InstanceInfo
import app.pachli.core.network.model.MediaUploadApi
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.util.MEDIA_SIZE_UNKNOWN
import app.pachli.util.asRequestBody
import app.pachli.util.getImageSquarePixels
import app.pachli.util.getMediaSize
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber

/**
 * Media that has been fully uploaded to the server and may still be being
 * processed.
 *
 * @property mediaId Server-side identifier for this media item
 * @property processed True if the server has finished processing this media item
 */
data class UploadedMedia(
    val mediaId: String,
    val processed: Boolean,
)

/**
 * Media that has been prepared for uploading.
 *
 * @param type file's general type (image, video, etc)
 * @param uri content URI for the prepared media file
 * @param size size of the media file, in bytes
 */
data class PreparedMedia(val type: QueuedMedia.Type, val uri: Uri, val size: Long)

/* Errors that can be returned when uploading media. */
sealed interface MediaUploaderError : PachliError {
    /** Errors that can occur while preparing media for upload. */
    sealed interface PrepareMediaError : MediaUploaderError {
        /**
         * Content resolver returned an empty URI for the file.
         *
         * @property uri URI returned by the content resolver.
         */
        data class ContentResolverMissingPathError(val uri: Uri) : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_content_resolver_missing_path_fmt
            override val formatArgs = arrayOf(uri)
            override val cause = null
        }

        /**
         * Content resolver returned an unsupported URI scheme.
         *
         * @property uri URI returned by the content provider.
         */
        data class ContentResolverUnsupportedSchemeError(val uri: Uri) : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_content_resolver_unsupported_scheme_fmt
            override val formatArgs = arrayOf(uri)
            override val cause = null
        }

        /**
         * [IOException] while operating on the file
         *
         * @property exception Thrown exception.
         */
        data class IoError(val exception: IOException) : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_io_fmt
            override val formatArgs = arrayOf(exception.localizedMessage)
            override val cause = null
        }

        /**
         * File's size exceeds servers limits.
         *
         * @property fileSizeBytes size of the file being uploaded
         * @property allowedSizeBytes maximum size of file server accepts
         */
        data class FileIsTooLargeError(val fileSizeBytes: Long, val allowedSizeBytes: Long) : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_file_is_too_large_fmt
            override val formatArgs = arrayOf(formatNumber(fileSizeBytes), formatNumber(allowedSizeBytes))
            override val cause = null
        }

        /** File's size could not be determined. */
        data object UnknownFileSizeError : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_unknown_file_size
            override val formatArgs = null
            override val cause = null
        }

        /**
         * File's MIME type is not supported by server.
         *
         * @property mimeType File's MIME type.
         */
        data class UnsupportedMimeTypeError(val mimeType: String) : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_unsupported_mime_type_fmt
            override val formatArgs = arrayOf(mimeType)
            override val cause = null
        }

        /** File's MIME type is not known. */
        data object UnknownMimeTypeError : PrepareMediaError {
            override val resourceId = R.string.error_prepare_media_unknown_mime_type
            override val formatArgs = null
            override val cause = null
        }
    }

    /** [ApiError] wrapper. */
    @JvmInline
    value class UploadMediaError(private val error: ApiError) : MediaUploaderError, PachliError by error

    /** Server did return media with ID [uploadId]. */
    data class UploadIdNotFoundError(val uploadId: Int) : MediaUploaderError {
        override val resourceId = R.string.error_media_uploader_upload_not_found_fmt
        override val formatArgs = arrayOf(uploadId.toString())
        override val cause = null
    }

    /** Catch-all for arbitrary throwables */
    data class ThrowableError(private val throwable: Throwable) : MediaUploaderError {
        override val resourceId = R.string.error_media_uploader_throwable_fmt
        override val formatArgs = arrayOf(throwable.localizedMessage)
        override val cause = null
    }
}

/** Events that happen over the life of a media upload. */
sealed interface UploadEvent {
    /**
     * Upload has made progress.
     *
     * @property percentage What percent of the file has been uploaded.
     */
    data class ProgressEvent(val percentage: Int) : UploadEvent

    /**
     * Upload has finished.
     *
     * @property media The uploaded media
     */
    data class FinishedEvent(val media: UploadedMedia) : UploadEvent
}

data class UploadData(
    val flow: Flow<Result<UploadEvent, MediaUploaderError>>,
    val scope: CoroutineScope,
)

fun createNewImageFile(context: Context, suffix: String = ".jpg"): File {
    // Create an image file name
    val randomId = randomAlphanumericString(12)
    val imageFileName = "Pachli_${randomId}_"
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        imageFileName,
        suffix,
        storageDir,
    )
}

@Singleton
class MediaUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaUploadApi: MediaUploadApi,
) {

    private val uploads = mutableMapOf<Int, UploadData>()

    private var mostRecentId: Int = 0

    fun getNewLocalMediaId(): Int {
        return mostRecentId++
    }

    suspend fun getMediaUploadState(localId: Int): Result<UploadEvent.FinishedEvent, MediaUploaderError> {
        return uploads[localId]?.flow
            ?.filterIsInstance<Ok<UploadEvent.FinishedEvent>>()
            ?.first()
            ?: Err(MediaUploaderError.UploadIdNotFoundError(localId))
    }

    /**
     * Uploads media.
     * @param media the media to upload
     * @param instanceInfo info about the current media to make sure the media gets resized correctly
     * @return A Flow emitting upload events.
     * The Flow is hot, in order to cancel upload or clear resources call [cancelUploadScope].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun uploadMedia(media: QueuedMedia, instanceInfo: InstanceInfo): Flow<Result<UploadEvent, MediaUploaderError>> {
        val uploadScope = CoroutineScope(Dispatchers.IO)
        val uploadFlow: Flow<Result<UploadEvent, MediaUploaderError>> = flow {
            if (shouldResizeMedia(media, instanceInfo)) {
                emit(downsize(media, instanceInfo))
            } else {
                emit(media)
            }
        }
            .flatMapLatest { upload(it) }
            .shareIn(uploadScope, SharingStarted.Lazily, 1)

        uploads[media.localId] = UploadData(uploadFlow, uploadScope)
        return uploadFlow
    }

    /**
     * Cancels the CoroutineScope of a media upload.
     * Call this when to abort the upload or to clean up resources after upload info is no longer needed
     */
    fun cancelUploadScope(vararg localMediaIds: Int) {
        localMediaIds.forEach { localId ->
            uploads.remove(localId)?.scope?.cancel()
        }
    }

    /**
     * Prepares media for the upload queue by copying it from the [ContentResolver] to
     * a temporary location.
     *
     * @param inUri [ContentResolver] URI for the file to prepare
     * @param instanceInfo server's configuration, for maximum file size limits
     */
    fun prepareMedia(inUri: Uri, instanceInfo: InstanceInfo): Result<PreparedMedia, PrepareMediaError> {
        var mediaSize = MEDIA_SIZE_UNKNOWN
        var uri = inUri
        val mimeType: String?

        try {
            when (inUri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    mimeType = contentResolver.getType(uri)

                    val suffix = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType ?: "tmp")

                    contentResolver.openInputStream(inUri)?.source()?.buffer().use { input ->
                        if (input == null) {
                            Timber.w("Media input is null")
                            uri = inUri
                            return@use
                        }
                        val file = File.createTempFile("randomTemp1", suffix, context.cacheDir)
                        file.absoluteFile.sink().buffer().use { it.writeAll(input) }
                        uri = FileProvider.getUriForFile(
                            context,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            file,
                        )
                        mediaSize = getMediaSize(contentResolver, uri)
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path ?: return Err(PrepareMediaError.ContentResolverMissingPathError(uri))
                    val inputFile = File(path)
                    val suffix = inputFile.name.substringAfterLast('.', "tmp")
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix)
                    val file = File.createTempFile("randomTemp1", ".$suffix", context.cacheDir)

                    inputFile.source().buffer().use { input ->
                        file.absoluteFile.sink().buffer().use { it.writeAll(input) }
                    }
                    uri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        file,
                    )
                    mediaSize = getMediaSize(contentResolver, uri)
                }
                else -> {
                    Timber.w("Unknown uri scheme %s", uri)
                    return Err(PrepareMediaError.ContentResolverUnsupportedSchemeError(uri))
                }
            }
        } catch (e: IOException) {
            Timber.w(e)
            return Err(PrepareMediaError.IoError(e))
        }

        if (mediaSize == MEDIA_SIZE_UNKNOWN) {
            Timber.w("Could not determine file size of upload")
            return Err(PrepareMediaError.UnknownFileSizeError)
        }

        mimeType ?: return Err(PrepareMediaError.UnknownMimeTypeError)

        return when (mimeType.substring(0, mimeType.indexOf('/'))) {
            "video" -> {
                if (mediaSize > instanceInfo.videoSizeLimit) {
                    Err(PrepareMediaError.FileIsTooLargeError(mediaSize, instanceInfo.videoSizeLimit))
                } else {
                    Ok(PreparedMedia(QueuedMedia.Type.VIDEO, uri, mediaSize))
                }
            }
            "image" -> {
                Ok(PreparedMedia(QueuedMedia.Type.IMAGE, uri, mediaSize))
            }
            "audio" -> {
                if (mediaSize > instanceInfo.videoSizeLimit) {
                    Err(PrepareMediaError.FileIsTooLargeError(mediaSize, instanceInfo.videoSizeLimit))
                } else {
                    Ok(PreparedMedia(QueuedMedia.Type.AUDIO, uri, mediaSize))
                }
            }
            else -> {
                Err(PrepareMediaError.UnsupportedMimeTypeError(mimeType))
            }
        }
    }

    private val contentResolver = context.contentResolver

    private suspend fun upload(media: QueuedMedia): Flow<Result<UploadEvent, MediaUploaderError.UploadMediaError>> {
        return callbackFlow {
            var mimeType = contentResolver.getType(media.uri)

            // Android's MIME type suggestions from file extensions is broken for at least
            // .m4a files. See https://github.com/tuskyapp/Tusky/issues/3189 for details.
            // Sniff the content of the file to determine the actual type.
            mimeType?.let {
                if (it.startsWith("audio/", ignoreCase = true) ||
                    it.startsWith("video/", ignoreCase = true)
                ) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, media.uri)
                    mimeType = retriever.extractMetadata(METADATA_KEY_MIMETYPE)
                }
            }
            val map = MimeTypeMap.getSingleton()
            val fileExtension = map.getExtensionFromMimeType(mimeType)
            val filename = "%s_%d_%s.%s".format(
                context.getString(R.string.app_name),
                System.currentTimeMillis(),
                randomAlphanumericString(10),
                fileExtension,
            )

            if (mimeType == null) mimeType = "multipart/form-data"

            var lastProgress = -1
            val fileBody = media.uri.asRequestBody(
                contentResolver,
                requireNotNull(mimeType!!.toMediaTypeOrNull()) { "Invalid Content Type" },
                media.mediaSize,
            ) { percentage ->
                if (percentage != lastProgress) {
                    trySend(Ok(UploadEvent.ProgressEvent(percentage)))
                }
                lastProgress = percentage
            }

            val body = MultipartBody.Part.createFormData("file", filename, fileBody)

            val description = if (media.description != null) {
                MultipartBody.Part.createFormData("description", media.description)
            } else {
                null
            }

            val focus = media.focus?.let {
                MultipartBody.Part.createFormData("focus", "${it.x},${it.y}")
            }

            val uploadResult = mediaUploadApi.uploadMedia(body, description, focus)
                .mapEither(
                    { UploadEvent.FinishedEvent(UploadedMedia(it.body.id, it.code == 200)) },
                    { MediaUploaderError.UploadMediaError(it) },
                )
            send(uploadResult)

            awaitClose()
        }
    }

    private fun downsize(media: QueuedMedia, instanceInfo: InstanceInfo): QueuedMedia {
        val file = createNewImageFile(context)
        downsizeImage(media.uri, instanceInfo.imageSizeLimit, contentResolver, file)
        return media.copy(uri = file.toUri(), mediaSize = file.length())
    }

    private fun shouldResizeMedia(media: QueuedMedia, instanceInfo: InstanceInfo): Boolean {
        return media.type == QueuedMedia.Type.IMAGE &&
            (media.mediaSize > instanceInfo.imageSizeLimit || getImageSquarePixels(context.contentResolver, media.uri) > instanceInfo.imageMatrixLimit)
    }
}
