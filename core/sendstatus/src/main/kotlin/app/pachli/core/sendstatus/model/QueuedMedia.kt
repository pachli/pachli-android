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

package app.pachli.core.sendstatus.model

import android.net.Uri
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.Attachment
import app.pachli.core.sendstatus.MediaUploaderError
import app.pachli.core.sendstatus.UploadState
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth

/**
 * Media queued for upload.
 *
 * @param account
 * @param localId Pachli identifier for this media, while it's queued.
 * @param uri Local URI for this media on device.
 * @param type Media's [Type].
 * @param mediaSize Media size in bytes, or [app.pachli.core.common.util.MEDIA_SIZE_UNKNOWN]. See [app.pachli.core.common.util.getMediaSize].
 * @param description
 * @param focus
 * @param uploadState
 */
data class QueuedMedia(
    val account: AccountEntity,
    val localId: Int,
    val uri: Uri,
    val type: Type,
    val mediaSize: Long,
    val description: String? = null,
    val focus: Attachment.Focus? = null,
    val uploadState: Result<UploadState, MediaUploaderError>,
) {
    enum class Type {
        IMAGE,
        VIDEO,
        AUDIO,
    }

    /**
     * Server's ID for this attachment. May be null if the media is still
     * being uploaded, or it was uploaded and there was an error that
     * meant it couldn't be processed. Attachments that have an error
     * *after* processing have a non-null `serverId`.
     */
    val serverId: String?
        get() = uploadState.mapBoth(
            { state ->
                when (state) {
                    is UploadState.Uploading -> null
                    is UploadState.Uploaded.Processing -> state.serverId
                    is UploadState.Uploaded.Processed -> state.serverId
                    is UploadState.Uploaded.Published -> state.serverId
                }
            },
            { error ->
                when (error) {
                    is MediaUploaderError.UpdateMediaError -> error.serverId
                    else -> null
                }
            },
        )
}
