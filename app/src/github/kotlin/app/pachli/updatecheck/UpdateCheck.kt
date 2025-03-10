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
import androidx.core.net.toUri
import app.pachli.BuildConfig
import app.pachli.core.preferences.SharedPreferencesRepository
import com.github.michaelbull.result.get
import javax.inject.Inject

class UpdateCheck @Inject constructor(
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val gitHubService: GitHubService,
) : UpdateCheckBase(sharedPreferencesRepository) {
    private val versionCodeExtractor = """(\d+)\.apk""".toRegex()

    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = when (BuildConfig.FLAVOR_color) {
            "orange" -> "https://www.github.com/pachli/pachli-android-current/releases/latest".toUri()
            else -> "https://www.github.com/pachli/pachli-android/releases/latest".toUri()
        }
    }

    override suspend fun remoteFetchLatestVersionCode(): Int? {
        val release = when (BuildConfig.FLAVOR_color) {
            "orange" -> gitHubService.getLatestRelease("pachli", "pachli-android-current").get()?.body
            else -> gitHubService.getLatestRelease("pachli", "pachli-android").get()?.body
        } ?: return null

        for (asset in release.assets) {
            if (asset.contentType != "application/vnd.android.package-archive") continue
            return versionCodeExtractor.find(asset.name)?.groups?.get(1)?.value?.toIntOrNull() ?: continue
        }

        return null
    }
}
