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
import androidx.core.content.edit
import app.pachli.BuildConfig
import app.pachli.settings.PrefKeys
import app.pachli.util.SharedPreferencesRepository
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

enum class UpdateNotificationFrequency {
    /** Never prompt the user to update */
    NEVER,

    /** Prompt the user to update once per version */
    ONCE_PER_VERSION,

    /** Always prompt the user to update */
    ALWAYS,

    ;

    companion object {
        fun from(s: String?): UpdateNotificationFrequency {
            s ?: return ALWAYS

            return try {
                valueOf(s.uppercase())
            } catch (_: IllegalArgumentException) {
                ALWAYS
            }
        }
    }
}

@Singleton
abstract class UpdateCheckBase(private val sharedPreferencesRepository: SharedPreferencesRepository) {
    /** An intent that can be used to start the update process (e.g., open a store listing) */
    abstract val updateIntent: Intent

    /**
     * @return The newest available versionCode (which may be the current version code if there is
     *    no newer version, or if [MINIMUM_DURATION_BETWEEN_CHECKS] has not elapsed since the last
     *    check.
     */
    suspend fun getLatestVersionCode(): Int {
        val now = System.currentTimeMillis()
        val lastCheck = sharedPreferencesRepository.getLong(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS, 0)

        if (now - lastCheck < MINIMUM_DURATION_BETWEEN_CHECKS.inWholeMilliseconds) {
            return BuildConfig.VERSION_CODE
        }

        sharedPreferencesRepository.edit {
            putLong(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS, now)
        }

        return remoteFetchLatestVersionCode() ?: BuildConfig.VERSION_CODE
    }

    /**
     * Fetch the version code of the latest available version of Pachli from whatever
     * remote service the running version was downloaded from.
     *
     * @return The latest version code, or null if it could not be determined
     */
    abstract suspend fun remoteFetchLatestVersionCode(): Int?

    companion object {
        /** How much time should elapse between version checks */
        private val MINIMUM_DURATION_BETWEEN_CHECKS = 24.hours
    }
}
