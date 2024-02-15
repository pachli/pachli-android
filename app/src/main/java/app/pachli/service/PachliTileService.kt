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

package app.pachli.service

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import app.pachli.components.notifications.pendingIntentFlags
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.MainActivityIntent

/**
 * Small Addition that adds in a QuickSettings tile
 * opens the Compose activity or shows an account selector when multiple accounts are present
 */
@TargetApi(24)
class PachliTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = MainActivityIntent.openCompose(this, ComposeOptions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(getActivityPendingIntent(this, 0, intent))
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun getActivityPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags(false))
        } else {
            PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags(false))
        }
    }
}
