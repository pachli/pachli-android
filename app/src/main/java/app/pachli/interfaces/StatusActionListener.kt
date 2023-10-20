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

package app.pachli.interfaces

import android.view.View
import app.pachli.viewdata.StatusViewData

interface StatusActionListener : LinkListener {
    fun onReply(position: Int)
    fun onReblog(reblog: Boolean, position: Int)
    fun onFavourite(favourite: Boolean, position: Int)
    fun onBookmark(bookmark: Boolean, position: Int)
    fun onMore(view: View, position: Int)
    fun onViewMedia(position: Int, attachmentIndex: Int, view: View?)
    fun onViewThread(position: Int)

    /**
     * Open reblog author for the status.
     * @param position At which position in the list status is located
     */
    fun onOpenReblog(position: Int)
    fun onExpandedChange(expanded: Boolean, position: Int)
    fun onContentHiddenChange(isShowing: Boolean, position: Int)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     * @param position    The position of the status in the list.
     */
    fun onContentCollapsedChange(isCollapsed: Boolean, position: Int)

    /**
     * called when the reblog count has been clicked
     * @param position The position of the status in the list.
     */
    fun onShowReblogs(position: Int) {}

    /**
     * called when the favourite count has been clicked
     * @param position The position of the status in the list.
     */
    fun onShowFavs(position: Int) {}
    fun onVoteInPoll(position: Int, choices: List<Int>)
    fun onShowEdits(position: Int) {}
    fun clearWarningAction(position: Int)

    fun onTranslate(statusViewData: StatusViewData) {
        TODO()
    }
}
