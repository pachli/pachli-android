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

package app.pachli.core.common.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * The application's version name.
 *
 * In the application module this is available as `BuildConfig.VERSION_NAME`, but it
 * is not available in library modules. Fetch the information from the packagemanager.
 */
fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: PackageManager.NameNotFoundException) {
    "unknown"
}
