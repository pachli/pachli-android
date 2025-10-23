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

package app.pachli.core.ui

import android.view.View
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.IStatus
import app.pachli.core.model.Poll
import app.pachli.core.model.Status

interface StatusActionListener<T : IStatusViewData> : LinkListener {
    fun onReply(viewData: T)
    fun onReblog(viewData: T, reblog: Boolean)
    fun onFavourite(viewData: T, favourite: Boolean)
    fun onBookmark(viewData: T, bookmark: Boolean)
    fun onMore(view: View, viewData: T)
    fun onViewAttachment(view: View?, viewData: T, attachmentIndex: Int)
    fun onViewThread(status: Status)

    /**
     * Open reblog author for the status.
     */
    fun onOpenReblog(status: IStatus)
    fun onExpandedChange(viewData: T, expanded: Boolean)

    fun onAttachmentDisplayActionChange(viewData: T, newAction: AttachmentDisplayAction)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     */
    fun onContentCollapsedChange(viewData: T, isCollapsed: Boolean)

    /**
     * called when the reblog count has been clicked
     */
    fun onShowReblogs(statusId: String) {}

    /**
     * called when the favourite count has been clicked
     */
    fun onShowFavs(statusId: String) {}

    /**
     * Called when voting on a poll.
     *
     * @param viewData
     * @param poll The poll the user is voting in.
     * @param choices The indices of the options the user is voting for.
     */
    fun onVoteInPoll(viewData: T, poll: Poll, choices: List<Int>)
    fun onShowEdits(statusId: String) {}

    /** Remove the content filter from the status. */
    fun clearContentFilter(viewData: T)

    /** Edit the filter that matched this status. */
    fun onEditFilterById(pachliAccountId: Long, filterId: String)

    /**
     * View non-attached media referenced by URL.
     *
     * @param pachliAccountId
     * @param username The username that owns the media.
     * @param url The URL of the media.
     */
    fun onViewMedia(pachliAccountId: Long, username: String, url: String)
}
