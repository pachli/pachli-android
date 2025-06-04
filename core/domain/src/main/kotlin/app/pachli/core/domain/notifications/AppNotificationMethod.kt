/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.domain.notifications

/** The overall app notification method. */
enum class AppNotificationMethod {
    /** All accounts are configured to use UnifiedPush, and are registered with the distributor. */
    ALL_PUSH,

    /**
     * Some accounts are configured to use UnifiedPush, and are registered with the distributor.
     * For other accounts either registration failed, or their server does not support push, and
     * notifications are pulled.
     */
    MIXED,

    /** All accounts are configured to pull notifications. */
    ALL_PULL,
}
