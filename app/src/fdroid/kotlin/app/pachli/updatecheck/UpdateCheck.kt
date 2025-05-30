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
import javax.inject.Singleton

@Singleton
class UpdateCheck @Inject constructor(
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val fdroidService: FdroidService,
) : UpdateCheckBase(sharedPreferencesRepository) {
    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "market://details?id=${BuildConfig.APPLICATION_ID}".toUri()
    }

    override suspend fun remoteFetchLatestVersionCode(): Int? {
        val fdroidPackage = fdroidService.getPackage(BuildConfig.APPLICATION_ID).get()?.body ?: return null

        // `packages` is a list of all packages that have been built and are available.
        //
        // The package with `suggestedVersionCode` might not have been built yet (see
        // https://github.com/pachli/pachli-android/issues/684).
        //
        // Check that `suggestedVersionCode` appears in the list of packages. If it
        // does, use that.
        val suggestedVersionCode = fdroidPackage.suggestedVersionCode
        if (fdroidPackage.packages.any { it.versionCode == suggestedVersionCode }) return suggestedVersionCode

        // Otherwise, use the highest version code in `packages`.
        return fdroidPackage.packages.maxByOrNull { it.versionCode }?.versionCode
    }
}
