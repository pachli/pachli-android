/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.components.preference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Wraps suspending functions in [AccountManager] and calls them in [viewModelScope].
 */
@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val accountManager: AccountManager,
) : ViewModel() {
    fun setNotificationsEnabled(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsEnabled(pachliAccountId, value) }
    }

    fun setNotificationsMentioned(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsMentioned(pachliAccountId, value) }
    }

    fun setNotificationsFollowed(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsFollowed(pachliAccountId, value) }
    }

    fun setNotificationsFollowRequested(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsFollowRequested(pachliAccountId, value) }
    }

    fun setNotificationsReblogged(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsReblogged(pachliAccountId, value) }
    }

    fun setNotificationsFavorited(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsFavorited(pachliAccountId, value) }
    }

    fun setNotificationsPolls(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsPolls(pachliAccountId, value) }
    }

    fun setNotificationsSubscriptions(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsSubscriptions(pachliAccountId, value) }
    }

    fun setNotificationsSignUps(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsSignUps(pachliAccountId, value) }
    }

    fun setNotificationsUpdates(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsUpdates(pachliAccountId, value) }
    }

    fun setNotificationsReports(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsReports(pachliAccountId, value) }
    }

    fun setNotificationsSeveredRelationships(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsSeveredRelationships(pachliAccountId, value) }
    }

    fun setNotificationsModerationWarnings(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationsModerationWarnings(pachliAccountId, value) }
    }

    fun setNotificationSound(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationSound(pachliAccountId, value) }
    }

    fun setNotificationVibration(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationVibration(pachliAccountId, value) }
    }

    fun setNotificationLight(pachliAccountId: Long, value: Boolean) {
        viewModelScope.launch { accountManager.setNotificationLight(pachliAccountId, value) }
    }
}
