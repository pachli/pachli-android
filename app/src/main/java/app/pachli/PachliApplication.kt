/*
 * Copyright 2020 Tusky Contributors
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
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pachli.components.notifications.createWorkerNotificationChannel
import app.pachli.core.activity.initCrashReporter
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.NEW_INSTALL_SCHEMA_VERSION
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SCHEMA_VERSION
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.util.LocaleManager
import app.pachli.util.setAppNightMode
import app.pachli.worker.PruneCacheWorker
import app.pachli.worker.PruneCachedMediaWorker
import app.pachli.worker.PruneLogEntryEntityWorker
import dagger.hilt.android.HiltAndroidApp
import de.c1710.filemojicompat_defaults.DefaultEmojiPackList
import de.c1710.filemojicompat_ui.helpers.EmojiPackHelper
import de.c1710.filemojicompat_ui.helpers.EmojiPreference
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.conscrypt.Conscrypt
import timber.log.Timber

@HiltAndroidApp
class PachliApplication : Application() {
    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        initCrashReporter(this)
    }

    override fun onCreate() {
        // Uncomment me to get StrictMode violation logs
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
//                    .detectDiskReads()
//                    .detectDiskWrites()
//                    .detectNetwork()
//                    .detectUnbufferedIo()
//                    .penaltyLog()
//                    .build())
//        }
        super.onCreate()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        // Migrate shared preference keys and defaults from version to version.
        val oldVersion = sharedPreferencesRepository.getInt(PrefKeys.SCHEMA_VERSION, NEW_INSTALL_SCHEMA_VERSION)
        if (oldVersion != SCHEMA_VERSION) {
            upgradeSharedPreferences(oldVersion, SCHEMA_VERSION)
        }

        // In this case, we want to have the emoji preferences merged with the other ones
        // Copied from PreferenceManager.getDefaultSharedPreferenceName
        EmojiPreference.sharedPreferenceName = packageName + "_preferences"
        EmojiPackHelper.init(this, DefaultEmojiPackList.get(this), allowPackImports = false)

        // init night mode
        val theme = AppTheme.from(sharedPreferencesRepository)
        setAppNightMode(theme)

        localeManager.setLocale()

        createWorkerNotificationChannel(this)

        val workManager = WorkManager.getInstance(this)

        // Prune the database every ~ 12 hours when the device is idle.
        val pruneCacheWorker = PeriodicWorkRequestBuilder<PruneCacheWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PruneCacheWorker.PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            pruneCacheWorker,
        )

        // Delete old logs every ~ 12 hours when the device is idle.
        val pruneLogEntryEntityWorker = PeriodicWorkRequestBuilder<PruneLogEntryEntityWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PruneLogEntryEntityWorker.PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            pruneLogEntryEntityWorker,
        )

        // Delete old cached media every ~ 12 hours when the device is idle
        val pruneCachedMediaWorker = PeriodicWorkRequestBuilder<PruneCachedMediaWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PruneCachedMediaWorker.PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            pruneCachedMediaWorker,
        )
    }

    private fun upgradeSharedPreferences(oldVersion: Int, newVersion: Int) {
        Timber.d("Upgrading shared preferences: %d -> %d", oldVersion, newVersion)
        val editor = sharedPreferencesRepository.edit()

        // General usage is:
        //
        // if (oldVersion < ...) {
        //     ... use editor modify the preferences ...
        // }

        editor.putInt(PrefKeys.SCHEMA_VERSION, newVersion)
        editor.apply()
    }
}
