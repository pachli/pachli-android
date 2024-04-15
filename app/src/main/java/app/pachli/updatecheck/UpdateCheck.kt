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

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.designsystem.R as DR
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.extensions.await
import app.pachli.updatecheck.UpdateCheckResult.AT_LATEST
import app.pachli.updatecheck.UpdateCheckResult.DIALOG_SHOWN
import app.pachli.updatecheck.UpdateCheckResult.IGNORED
import app.pachli.updatecheck.UpdateCheckResult.SKIPPED_BECAUSE_NEVER
import app.pachli.updatecheck.UpdateCheckResult.SKIPPED_BECAUSE_TOO_SOON
import java.time.Instant
import java.util.Date
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import timber.log.Timber

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

enum class UpdateCheckResult {
    /** Skipped update check because user configured frequency is "never" */
    SKIPPED_BECAUSE_NEVER,

    /** Skipped update check because it's too soon relative to the last check */
    SKIPPED_BECAUSE_TOO_SOON,

    /** Performed update check, user is at latest available version */
    AT_LATEST,

    /** Performed update check, user is ignoring the remote version */
    IGNORED,

    /** Performed update check, update dialog was shown to the user */
    DIALOG_SHOWN,
}

@Singleton
abstract class UpdateCheckBase(
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : Preference.SummaryProvider<Preference> {
    /** An intent that can be used to start the update process (e.g., open a store listing) */
    abstract val updateIntent: Intent

    /**
     * Fetch the version code of the latest available version of Pachli from whatever
     * remote service the running version was downloaded from.
     *
     * @return The latest version code, or null if it could not be determined
     */
    abstract suspend fun remoteFetchLatestVersionCode(): Int?

    /**
     * Check for available updates, and prompt user to update.
     *
     * Show a dialog prompting the user to update if a newer version of the app is available.
     * The user can start an update, ignore this version, or dismiss all future update
     * notifications.
     *
     * @param context Themed context used to display the "Update" dialog. Must be from an
     *     activity that uses `Theme.AppCompat` or a descendent.
     * @param force If true then the user's preferences for update checking frequency are
     *     ignored and the update check is always performed.
     *
     * @return The result of performing the update check
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateCheckResult {
        val frequency = UpdateNotificationFrequency.from(
            sharedPreferencesRepository.getString(PrefKeys.UPDATE_NOTIFICATION_FREQUENCY, null),
        )

        if (!force && frequency == UpdateNotificationFrequency.NEVER) return SKIPPED_BECAUSE_NEVER

        val now = System.currentTimeMillis()

        if (!force) {
            val lastCheck = sharedPreferencesRepository.getLong(
                PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS,
                0,
            )

            if (now - lastCheck < MINIMUM_DURATION_BETWEEN_CHECKS.inWholeMilliseconds) {
                return SKIPPED_BECAUSE_TOO_SOON
            }
        }

        sharedPreferencesRepository.edit {
            putLong(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS, now)
        }

        val latestVersionCode = remoteFetchLatestVersionCode() ?: BuildConfig.VERSION_CODE

        if (latestVersionCode <= BuildConfig.VERSION_CODE) return AT_LATEST

        if (frequency == UpdateNotificationFrequency.ONCE_PER_VERSION) {
            val ignoredVersion =
                sharedPreferencesRepository.getInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, -1)
            if (latestVersionCode == ignoredVersion && !force) {
                Timber.d("Ignoring update to %d", latestVersionCode)
                return IGNORED
            }
        }

        Timber.d("New version is: %d", latestVersionCode)
        when (showUpdateDialog(context)) {
            AlertDialog.BUTTON_POSITIVE -> {
                context.startActivity(updateIntent)
            }

            AlertDialog.BUTTON_NEUTRAL -> {
                with(sharedPreferencesRepository.edit()) {
                    putInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, latestVersionCode)
                    apply()
                }
            }

            AlertDialog.BUTTON_NEGATIVE -> {
                with(sharedPreferencesRepository.edit()) {
                    putString(
                        PrefKeys.UPDATE_NOTIFICATION_FREQUENCY,
                        app.pachli.updatecheck.UpdateNotificationFrequency.NEVER.name,
                    )
                    apply()
                }
            }
        }

        return DIALOG_SHOWN
    }

    private suspend fun showUpdateDialog(context: Context) = AlertDialog.Builder(context)
        .setTitle(R.string.update_dialog_title)
        .setMessage(R.string.update_dialog_message)
        .setCancelable(true)
        .setIcon(DR.mipmap.ic_launcher)
        .create()
        .await(
            R.string.update_dialog_positive,
            R.string.update_dialog_negative,
            R.string.update_dialog_neutral,
        )

    override fun provideSummary(preference: Preference): CharSequence? {
        val frequency = UpdateNotificationFrequency.from(
            sharedPreferencesRepository.getString(PrefKeys.UPDATE_NOTIFICATION_FREQUENCY, null),
        )

        if (frequency == UpdateNotificationFrequency.NEVER) return null

        val now = Instant.now()
        val lastCheck = sharedPreferencesRepository.getLong(
            PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS,
            now.toEpochMilli(),
        )

        val nextCheck = Instant.ofEpochMilli(lastCheck).plus(MINIMUM_DURATION_BETWEEN_CHECKS.toJavaDuration())

        val dateString = AbsoluteTimeFormatter().format(Date.from(nextCheck))

        return preference.context.getString(R.string.pref_update_next_scheduled_check, dateString)
    }

    companion object {
        /** How much time should elapse between version checks */
        val MINIMUM_DURATION_BETWEEN_CHECKS = 24.hours
    }
}
