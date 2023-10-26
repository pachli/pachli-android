/* Copyright 2022 Tusky Contributors
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

package app.pachli.viewdata

import android.os.Parcelable
import app.pachli.entity.Attachment
import app.pachli.entity.Status
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AttachmentViewData(
    val attachment: Attachment,
    val statusId: String,
    val statusUrl: String,
    val sensitive: Boolean,
    val isRevealed: Boolean,
) : Parcelable {

    @IgnoredOnParcel
    val id = attachment.id

    companion object {
        @JvmStatic
        fun list(status: Status, alwaysShowSensitiveMedia: Boolean = false): List<AttachmentViewData> {
            val actionable = status.actionableStatus
            return actionable.attachments.map { attachment ->
                AttachmentViewData(
                    attachment = attachment,
                    statusId = actionable.id,
                    statusUrl = actionable.url!!,
                    sensitive = actionable.sensitive,
                    isRevealed = alwaysShowSensitiveMedia || !actionable.sensitive,
                )
            }
        }
    }
}
