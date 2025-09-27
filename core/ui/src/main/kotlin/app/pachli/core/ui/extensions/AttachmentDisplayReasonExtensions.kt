/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.ui.extensions

import android.content.Context
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.ui.R

/** @return UX string explaining why an attachment has been hidden. */
fun AttachmentDisplayReason.getFormattedDescription(context: Context) = when (this) {
    is AttachmentDisplayReason.BlurFilter -> {
        val resource = when (filters.size) {
            1 -> R.string.attachment_matches_filter_one_fmt
            2 -> R.string.attachment_matches_filter_two_fmt
            else -> R.string.attachment_matches_filter_other_fmt
        }
        context.getString(resource, *filters.map { it.title }.toTypedArray())
    }
    is AttachmentDisplayReason.Sensitive -> context.getText(R.string.post_sensitive_media_title)
    is AttachmentDisplayReason.UserAction -> context.getText(R.string.attachment_hidden_user_action)
}
