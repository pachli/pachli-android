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

package app.pachli.core.activity

import com.github.michaelbull.result.Result
import java.time.Instant

/**
 * Singleton to record information about how notifications are configured
 * and fetched as the app runs.
 */
object NotificationConfig {
    /** Method used to fetch Mastodon notifications */
    sealed interface Method {
        /** Notifications are pushed using UnifiedPush */
        data object Push : Method

        /** Notifications are periodically pulled */
        data object Pull : Method

        /** Notification method is not known */
        data object Unknown : Method

        /** Notifications should be pushed, there was an error configuring UnifiedPush */
        data class PushError(val t: Throwable) : Method
    }

    /** True if notification channels are enabled */
    var androidNotificationsEnabled = false

    /**
     * True if UnifiedPush is available
     *
     * @see [app.pachli.components.notifications.isUnifiedPushAvailable]
     */
    var unifiedPushAvailable = false

    /**
     * True if any account is missing the `push` OAuth scope.
     *
     * @see [app.pachli.components.notifications.anyAccountNeedsMigration]
     */
    var anyAccountNeedsMigration = false

    /** The current global method for fetching notifications */
    var notificationMethod: Method = Method.Unknown

    /**
     * The current per-account method for fetching notifications for that account.
     *
     * The map key is [app.pachli.core.database.model.AccountEntity.fullName]
     */
    var notificationMethodAccount = mutableMapOf<String, Method>()

    /**
     * Per-account details of the last time notifications were fetched for
     * the account.
     *
     * The map key is [app.pachli.core.database.model.AccountEntity.fullName].
     *
     * The value [Pair] is a timestamp of the fetch, and either a successful result,
     * or a description of what failed.
     */
    var lastFetchNewNotifications = mutableMapOf<String, Pair<Instant, Result<Unit, String>>>()
}
