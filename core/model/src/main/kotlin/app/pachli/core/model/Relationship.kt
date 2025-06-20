/*
 * Copyright 2017 Andrew Dawson
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

package app.pachli.core.model

data class Relationship(
    val id: String,
    val following: Boolean,
    val followedBy: Boolean,
    /** True if this account is blocked. */
    val blocking: Boolean,
    /** True if this account is muted. */
    val muting: Boolean,
    val mutingNotifications: Boolean,
    val requested: Boolean,
    val showingReblogs: Boolean,
    /**
     * Pleroma extension, same as 'notifying' on Mastodon.
     */
    val subscribing: Boolean? = null,
    val blockingDomain: Boolean,
    // nullable for backward compatibility / feature detection
    val note: String?,
    // since 3.3.0rc
    val notifying: Boolean?,
)
