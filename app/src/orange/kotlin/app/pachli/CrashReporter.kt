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

package app.pachli

import android.app.Application
import app.pachli.core.designsystem.R as DR
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

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
