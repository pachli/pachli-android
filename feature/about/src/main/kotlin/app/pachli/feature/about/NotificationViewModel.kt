/*
 * Copyright 2024 Pachli Association
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

package app.pachli.feature.about

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.NotificationConfig
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    /** Timestamp for this state. Ensures that each state is different */
    val now: Instant,

    /** @see [app.pachli.core.activity.NotificationConfig.androidNotificationsEnabled] */
    val androidNotificationsEnabled: Boolean,

    /**
     * Formatted string of accounts and whether notifications are enabled for each
     * account.
     */
    val notificationEnabledAccounts: String,

    /** @see [app.pachli.core.activity.NotificationConfig.notificationMethod] */
    val notificationMethod: NotificationConfig.Method,

    /** @see [app.pachli.core.activity.NotificationConfig.unifiedPushAvailable] */
    val unifiedPushAvailable: Boolean,

    /** @see [app.pachli.core.activity.NotificationConfig.anyAccountNeedsMigration] */
    val anyAccountNeedsMigration: Boolean,

    /** Formatted string of accounts and whether the account needs migration */
    val anyAccountNeedsMigrationAccounts: String,

    /** True if all accounts have a UnifiedPush URL */
    val allAccountsHaveUnifiedPushUrl: Boolean,

    /** Formatted string of accounts with/out their UnifiedPush URL */
    val allAccountsUnifiedPushUrlAccounts: String,

    /** True if all accounts have a UnifiedPush notification method */
    val allAccountsHaveUnifiedPushSubscription: Boolean,

    /** Formatted string of accounts and their notification method */
    val allAccountsUnifiedPushSubscriptionAccounts: String,

    /** True if Android exempts ntfy from battery optimisation */
    val ntfyIsExemptFromBatteryOptimisation: Boolean,

    /** True if Android exempts Pachli from battery optimisation */
    val pachliIsExemptFromBatteryOptimisation: Boolean,

    /**
     * Formatted string of accounts, when the last notification fetch was for each
     * account, and the result.
     */
    val lastFetch: String,
) {
    companion object {
        fun from(notificationConfig: NotificationConfig, accountManager: AccountManager, powerManager: PowerManager, now: Instant, packageName: String) = UiState(
            now = now,

            androidNotificationsEnabled = notificationConfig.androidNotificationsEnabled,
            notificationEnabledAccounts = accountManager.accounts.joinToString("\n") {
                "%s %s".format(if (it.notificationsEnabled) "✔" else "✖", it.fullName)
            },
            notificationMethod = notificationConfig.notificationMethod,

            unifiedPushAvailable = notificationConfig.unifiedPushAvailable,
            anyAccountNeedsMigration = notificationConfig.anyAccountNeedsMigration,
            anyAccountNeedsMigrationAccounts = accountManager.accounts.joinToString("\n") {
                // Duplicate of accountNeedsMigration from PushNotificationHelper
                "%s %s".format(if (it.oauthScopes.contains("push")) "✔" else "✖", it.fullName)
            },

            allAccountsHaveUnifiedPushUrl = accountManager.accounts.all { it.unifiedPushUrl.isNotEmpty() },
            allAccountsUnifiedPushUrlAccounts = accountManager.accounts.joinToString("\n") {
                if (it.unifiedPushUrl.isNotEmpty()) {
                    "✔ %s %s".format(it.fullName, it.unifiedPushUrl)
                } else {
                    "✖  %s".format(it.fullName)
                }
            },
            allAccountsHaveUnifiedPushSubscription = notificationConfig.notificationMethodAccount.all { it.value is NotificationConfig.Method.Push },
            allAccountsUnifiedPushSubscriptionAccounts = notificationConfig.notificationMethodAccount.map {
                when (val method = it.value) {
                    NotificationConfig.Method.Pull -> "✖ ${it.key} (Pull)"
                    NotificationConfig.Method.Push -> "✔ ${it.key} (Push)"
                    is NotificationConfig.Method.PushError -> "✖ ${it.key} (Error: ${method.t})"
                    NotificationConfig.Method.Unknown -> "✖ ${it.key} (Unknown)"
                }
            }.joinToString("\n"),

            ntfyIsExemptFromBatteryOptimisation = powerManager.isIgnoringBatteryOptimizations("io.heckel.ntfy"),
            pachliIsExemptFromBatteryOptimisation = powerManager.isIgnoringBatteryOptimizations(packageName),

            lastFetch = notificationConfig
                .lastFetchNewNotifications
                .map { (fullName, outcome) ->
                    val instant = outcome.first
                    val result = outcome.second
                    "%s\n    %s ago @ %s".format(
                        when (result) {
                            is Ok -> "✔ $fullName"
                            is Err -> "✖ $fullName: ${result.error}"
                        },
                        Duration.between(instant, now).asDdHhMmSs(),
                        instantFormatter.format(instant),
                    )
                }.joinToString("\n"),
        )
    }
}

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val application: Application,
    private val accountManager: AccountManager,
    private val powerManager: PowerManager,
    private val usageStatsManager: UsageStatsManager,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        UiState.from(
            NotificationConfig,
            accountManager,
            powerManager,
            Instant.now(),
            application.packageName,
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val pullWorkerFlow: StateFlow<List<WorkInfo>> = WorkManager.getInstance(application)
        .getWorkInfosByTagFlow("pullNotifications").stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _usageEventsFlow = MutableStateFlow<List<UsageEvents.Event>>(emptyList())
    val usageEventsFlow = _usageEventsFlow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val now = Instant.now()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val usageEvents = usageStatsManager.queryEventsForSelf(
                now.minusSeconds(86400 * 3).toEpochMilli(),
                now.toEpochMilli(),
            )
            val events = buildList {
                var event = UsageEvents.Event()
                while (usageEvents.getNextEvent(event)) {
                    if (event.eventType != UsageEvents.Event.STANDBY_BUCKET_CHANGED) continue
                    this.add(event)
                    event = UsageEvents.Event()
                }
            }.sortedByDescending { it.timeStamp }
            _usageEventsFlow.value = events
        }

        _uiState.value = UiState.from(
            NotificationConfig,
            accountManager,
            powerManager,
            now,
            application.packageName,
        )
    }
}
