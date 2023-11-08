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

package app.pachli.updatecheck

import android.content.Intent
import android.net.Uri
import app.pachli.BuildConfig
import app.pachli.core.preferences.SharedPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheck @Inject constructor(
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val fdroidService: FdroidService
) : UpdateCheckBase(sharedPreferencesRepository) {
    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")
    }

    override suspend fun remoteFetchLatestVersionCode(): Int? {
        return fdroidService.getPackage(BuildConfig.APPLICATION_ID).getOrNull()?.suggestedVersionCode
    }
}
