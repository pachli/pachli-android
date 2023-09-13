/* Copyright 2017 Andrew Dawson
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */
package app.pachli.viewdata

import android.os.Build
import android.text.Spanned
import app.pachli.entity.Filter
import app.pachli.entity.Status
import app.pachli.util.parseAsMastodonHtml
import app.pachli.util.replaceCrashingCharacters
import app.pachli.util.shouldTrimStatus

/**
 * Data required to display a status.
 */
data class StatusViewData(
    var status: Status,
    /**
     * If the status includes a non-empty content warning ([spoilerText]), specifies whether
     * just the content warning is showing (false), or the whole status content is showing (true).
     *
     * Ignored if there is no content warning.
     */
    val isExpanded: Boolean,
    /**
     * If the status contains attached media, specifies whether whether the media is shown
     * (true), or not (false).
     */
    val isShowingContent: Boolean,

    /**
     * Specifies whether the content of this status is currently limited in visibility to the first
     * 500 characters or not.
     *
     * @return Whether the status is collapsed or fully expanded.
     */
    val isCollapsed: Boolean,

    /**
     * Specifies whether this status should be shown with the "detailed" layout, meaning it is
     * the status that has a focus when viewing a thread.
     */
    val isDetailed: Boolean = false,

    /** Whether this status should be filtered, and if so, how */
    var filterAction: Filter.Action = Filter.Action.NONE,
) {
    val id: String
        get() = status.id

    /**
     * Specifies whether the content of this status is long enough to be automatically
     * collapsed or if it should show all content regardless.
     *
     * @return Whether the status is collapsible or never collapsed.
     */
    val isCollapsible: Boolean

    val content: Spanned

    /** The content warning, may be the empty string */
    val spoilerText: String
    val username: String

    val actionable: Status
        get() = status.actionableStatus

    val actionableId: String
        get() = status.actionableStatus.id

    val rebloggedAvatar: String?
        get() = if (status.reblog != null) {
            status.account.avatar
        } else {
            null
        }

    val rebloggingStatus: Status?
        get() = if (status.reblog != null) status else null

    init {
        if (Build.VERSION.SDK_INT == 23) {
            // https://github.com/tuskyapp/Tusky/issues/563
            this.content = replaceCrashingCharacters(status.actionableStatus.content.parseAsMastodonHtml())
            this.spoilerText =
                replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
            this.username =
                replaceCrashingCharacters(status.actionableStatus.account.username).toString()
        } else {
            this.content = status.actionableStatus.content.parseAsMastodonHtml()
            this.spoilerText = status.actionableStatus.spoilerText
            this.username = status.actionableStatus.account.username
        }
        this.isCollapsible = shouldTrimStatus(this.content)
    }

    /** Helper for Java */
    fun copyWithCollapsed(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
}
