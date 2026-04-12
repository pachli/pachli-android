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

import android.os.Parcelable
import app.pachli.core.model.Attachment
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaToSend(
    val localId: Int,
    // null if media is not yet completely uploaded
    val id: String?,
    val uri: String,
    val description: String?,
    val focus: Attachment.Focus?,
    var processed: Boolean,
) : Parcelable
