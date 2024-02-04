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

package app.pachli.core.activity

import android.app.Application
import android.content.Context
import android.util.Log
import app.pachli.core.designsystem.R as DR
import com.google.auto.service.AutoService
import java.time.Instant
import org.acra.ACRA
import org.acra.builder.ReportBuilder
import org.acra.collector.Collector
import org.acra.config.CoreConfiguration
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.CrashReportData
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import timber.log.Timber

/**
 * Initialise ACRA.
 */
fun initCrashReporter(app: Application) {
    app.initAcra {
        buildConfigClass = BuildConfig::class.java
        reportFormat = StringFormat.KEY_VALUE_LIST

        dialog {
            title = app.getString(R.string.acra_dialog_title, app.getString(R.string.app_name))
            text = app.getString(R.string.acra_dialog_text)
            resIcon = DR.mipmap.ic_launcher
            resTheme = android.R.style.Theme_Material_Light_Dialog_Alert
        }

        mailSender {
            mailTo = BuildConfig.SUPPORT_EMAIL
            reportAsFile = false
            body = app.getString(R.string.acra_email_body)
        }
    }
}

/**
 * Trigger a report without a crash.
 */
fun triggerCrashReport() {
    ACRA.errorReporter.handleException(null, false)
}

/**
 * [Timber.Tree] that logs in to ring buffer, keeping the most recent 1,000 log
 * entries.
 */
object TreeRing : Timber.DebugTree() {
    /**
     * Store the components of a log line without doing any formatting or other
     * work at logging time.
     */
    data class LogEntry(
        val instant: Instant,
        val priority: Int,
        val tag: String?,
        val message: String,
        val t: Throwable?,
    )

    val buffer = RingBuffer<LogEntry>(1000)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        buffer.add(LogEntry(Instant.now(), priority, tag, message, t))
    }
}

/**
 * Acra collector that appends the contents of the [TreeRing] log to the Acra
 * report.
 */
@AutoService(Collector::class)
class TreeRingCollector : Collector {
    /** Map log priority values to characters to use when displaying the log */
    private val priority = mapOf(
        Log.VERBOSE to 'V',
        Log.DEBUG to 'D',
        Log.INFO to 'I',
        Log.WARN to 'W',
        Log.ERROR to 'E',
        Log.ASSERT to 'A',
    )

    override fun collect(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
        crashReportData: CrashReportData,
    ) {
        crashReportData.put(
            "TreeRing",
            TreeRing.buffer.toList().joinToString("\n") {
                "%s %c/%s: %s%s".format(
                    it.instant.toString(),
                    priority[it.priority] ?: '?',
                    it.tag,
                    it.message,
                    it.t?.let { t -> " $t" } ?: "",
                )
            },
        )
    }
}
