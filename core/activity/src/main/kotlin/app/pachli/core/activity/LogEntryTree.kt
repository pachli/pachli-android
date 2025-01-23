/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.activity

import android.database.sqlite.SQLiteException
import android.util.Log
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.model.LogEntryEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * [Timber.Tree] that writes logs to the [LogEntryDao].
 *
 * Only logs that are of level [Log.WARN] or higher, or are tagged with one
 * [loggableTags] are logged, everything else is ignored.
 */
@Singleton
class LogEntryTree @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val logEntryDao: LogEntryDao,
) : Timber.DebugTree() {
    /** Logs with a tag in this set will be logged */
    private val loggableTags = setOf(
        "Noti",
        "NotificationFetcher",
        "NotificationHelperKt",
        "NotificationWorker",
        "PushNotificationHelperKt",
        "UnifiedPushBroadcastReceiver",
    )

    /** Logs with this priority or higher will be logged */
    private val minPriority = Log.WARN

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return (priority >= minPriority) || (tag in loggableTags)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        externalScope.launch {
            try {
                logEntryDao.insert(
                    LogEntryEntity(
                        instant = Instant.now(),
                        priority = priority,
                        tag = tag,
                        message = message,
                        t = t,
                    ),
                )
            } catch (e: SQLiteException) {
                // Might trigger a "cannot start a transaction within a transaction"
                // exception here if the log is being written inside another
                // transaction. Nothing to do except swallow the exception and
                // continue.
            }
        }
    }
}
