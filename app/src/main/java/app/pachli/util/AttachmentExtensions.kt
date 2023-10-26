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

import androidx.annotation.DrawableRes
import app.pachli.R
import app.pachli.core.network.model.Attachment

/** @return a drawable resource for an icon to indicate the attachment type */
@DrawableRes
fun Attachment.iconResource() = when (this.type) {
    Attachment.Type.IMAGE -> R.drawable.ic_photo_24dp
    Attachment.Type.GIFV, Attachment.Type.VIDEO -> R.drawable.ic_videocam_24dp
    Attachment.Type.AUDIO -> R.drawable.ic_music_box_24dp
    Attachment.Type.UNKNOWN -> R.drawable.ic_attach_file_24dp
}
