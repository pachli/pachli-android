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

package app.pachli.components.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.adapter.FollowRequestViewHolder
import app.pachli.adapter.ReportNotificationViewHolder
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Status
import app.pachli.databinding.ItemFollowBinding
import app.pachli.databinding.ItemFollowRequestBinding
import app.pachli.databinding.ItemReportNotificationBinding
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusNotificationBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.databinding.SimpleListItem1Binding
import app.pachli.interfaces.AccountActionListener
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.StatusDisplayOptions
import app.pachli.viewdata.NotificationViewData

/** How to present the notification in the UI */
enum class NotificationViewKind {
    /** View as the original status */
    STATUS,
    STATUS_FILTERED,

    /** View as the original status, with the interaction type above */
    NOTIFICATION,
    FOLLOW,
    FOLLOW_REQUEST,
    REPORT,
    UNKNOWN,
    ;

    companion object {
        fun from(kind: Notification.Type?): NotificationViewKind {
            return when (kind) {
                Notification.Type.MENTION,
                Notification.Type.POLL,
                Notification.Type.UNKNOWN,
                -> STATUS
                Notification.Type.FAVOURITE,
                Notification.Type.REBLOG,
                Notification.Type.STATUS,
                Notification.Type.UPDATE,
                -> NOTIFICATION
                Notification.Type.FOLLOW,
                Notification.Type.SIGN_UP,
                -> FOLLOW
                Notification.Type.FOLLOW_REQUEST -> FOLLOW_REQUEST
                Notification.Type.REPORT -> REPORT
                null -> UNKNOWN
            }
        }
    }
}

interface NotificationActionListener {
    fun onViewAccount(id: String)
    fun onViewThreadForStatus(status: Status)
    fun onViewReport(reportId: String)

    /**
     * Called when the status has a content warning and the visibility of the content behind
     * the warning is being changed.
     *
     * @param expanded the desired state of the content behind the content warning
     * @param position the adapter position of the view
     *
     */
    fun onExpandedChange(expanded: Boolean, position: Int)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     * @param position    The position of the status in the list.
     */
    fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int)
}

class NotificationsPagingAdapter(
    diffCallback: DiffUtil.ItemCallback<NotificationViewData>,
    /** ID of the the account that notifications are being displayed for */
    private val accountId: String,
    private val statusActionListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener,
    var statusDisplayOptions: StatusDisplayOptions,
) : PagingDataAdapter<NotificationViewData, RecyclerView.ViewHolder>(diffCallback) {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    /** View holders in this adapter must implement this interface */
    interface ViewHolder {
        /** Bind the data from the notification and payloads to the view */
        fun bind(
            viewData: NotificationViewData,
            payloads: List<*>?,
            statusDisplayOptions: StatusDisplayOptions,
        )
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item?.statusViewData?.filterAction == Filter.Action.WARN) {
            return NotificationViewKind.STATUS_FILTERED.ordinal
        }

        return NotificationViewKind.from(getItem(position)?.type).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (NotificationViewKind.entries[viewType]) {
            NotificationViewKind.STATUS -> {
                StatusViewHolder(
                    ItemStatusBinding.inflate(inflater, parent, false),
                    statusActionListener,
                    accountId,
                )
            }
            NotificationViewKind.STATUS_FILTERED -> {
                FilterableStatusViewHolder(
                    ItemStatusWrapperBinding.inflate(inflater, parent, false),
                    statusActionListener,
                    accountId,
                )
            }
            NotificationViewKind.NOTIFICATION -> {
                StatusNotificationViewHolder(
                    ItemStatusNotificationBinding.inflate(inflater, parent, false),
                    statusActionListener,
                    notificationActionListener,
                    absoluteTimeFormatter,
                )
            }
            NotificationViewKind.FOLLOW -> {
                FollowViewHolder(
                    ItemFollowBinding.inflate(inflater, parent, false),
                    notificationActionListener,
                    statusActionListener,
                )
            }
            NotificationViewKind.FOLLOW_REQUEST -> {
                FollowRequestViewHolder(
                    ItemFollowRequestBinding.inflate(inflater, parent, false),
                    accountActionListener,
                    statusActionListener,
                    showHeader = true,
                )
            }
            NotificationViewKind.REPORT -> {
                ReportNotificationViewHolder(
                    ItemReportNotificationBinding.inflate(inflater, parent, false),
                    notificationActionListener,
                )
            }
            else -> {
                FallbackNotificationViewHolder(
                    SimpleListItem1Binding.inflate(inflater, parent, false),
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(holder, position, null)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bindViewHolder(holder, position, payloads)
    }

    private fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?,
    ) {
        getItem(position)?.let { (holder as ViewHolder).bind(it, payloads, statusDisplayOptions) }
    }

    /**
     * Notification view holder to use if no other type is appropriate. Should never normally
     * be used, but is useful when migrating code.
     */
    private class FallbackNotificationViewHolder(
        val binding: SimpleListItem1Binding,
    ) : ViewHolder, RecyclerView.ViewHolder(binding.root) {
        override fun bind(
            viewData: NotificationViewData,
            payloads: List<*>?,
            statusDisplayOptions: StatusDisplayOptions,
        ) {
            binding.text1.text = viewData.statusViewData?.content
        }
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "NotificationsPagingAdapter"
    }
}
