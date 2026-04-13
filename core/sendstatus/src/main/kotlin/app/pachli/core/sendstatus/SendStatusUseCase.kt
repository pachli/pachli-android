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

package app.pachli.core.sendstatus

import android.content.Context
import androidx.core.content.ContextCompat
import app.pachli.core.sendstatus.model.StatusToSend
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SendStatusUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(statusToSend: StatusToSend) {
        val intent = SendStatusService.sendStatusIntent(context, statusToSend)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        /** Tag assigned to notifications about status saved to drafts. */
        // Assigned to notifications in SendStatusService, used in `DraftActivity` to
        // clear notifications, because the user can see the drafts with errors.
        const val TAG_SAVED_TO_DRAFTS = "app.pachli.core.sendstatus.SendStatusService.SAVED_TO_DRAFTS"
    }
}
