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
import com.google.android.play.core.appupdate.AppUpdateManager
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine

class UpdateCheck @Inject constructor(
    sharedPreferencesRepository: SharedPreferencesRepository,
    private val appUpdateManager: AppUpdateManager,
) : UpdateCheckBase(sharedPreferencesRepository) {
    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}".toUri()
        setPackage("com.android.vending")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun remoteFetchLatestVersionCode(): Int? {
        return suspendCancellableCoroutine { cont ->
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                cont.resume(info.availableVersionCode()) {}
            }
        }
    }
}
