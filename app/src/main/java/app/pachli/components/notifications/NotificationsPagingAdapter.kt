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
import app.pachli.R
import app.pachli.adapter.FollowRequestViewHolder
import app.pachli.adapter.ReportNotificationViewHolder
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowNotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowRequestNotificationViewData
import app.pachli.core.data.model.NotificationViewData.ModerationWarningNotificationViewData
import app.pachli.core.data.model.NotificationViewData.ReportNotificationViewData
import app.pachli.core.data.model.NotificationViewData.SeveredRelationshipsNotificationViewData
import app.pachli.core.data.model.NotificationViewData.SignupNotificationViewData
import app.pachli.core.data.model.NotificationViewData.UnknownNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.FavouriteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.MentionNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.PollNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.FilterAction
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.databinding.ItemFollowBinding
import app.pachli.databinding.ItemFollowRequestBinding
import app.pachli.databinding.ItemModerationWarningBinding
import app.pachli.databinding.ItemNotificationFilteredBinding
import app.pachli.databinding.ItemReportNotificationBinding
import app.pachli.databinding.ItemSeveredRelationshipsBinding
import app.pachli.databinding.ItemStatusBinding
import app.pachli.databinding.ItemStatusNotificationBinding
import app.pachli.databinding.ItemStatusWrapperBinding
import app.pachli.databinding.ItemUnknownNotificationBinding
import app.pachli.interfaces.AccountActionListener
import com.bumptech.glide.RequestManager

/** How to present the notification in the UI */
enum class NotificationViewKind {
    /** View as the original status */
    STATUS,

    /**
     * Hide the status behind a warning message because the content
     * matched a content filter.
     */
    STATUS_FILTERED,

    /**
     * Hide the notification behind a warning message because the
     * account matched an account filter.
     */
    ACCOUNT_FILTERED,

    /** View as the original status, with the interaction type above */
    NOTIFICATION,
    FOLLOW,
    FOLLOW_REQUEST,
    REPORT,

    /** View details of the affected target, number of relationships affected, and the actor */
    SEVERED_RELATIONSHIPS,

    /** View details of the moderation warning. */
    MODERATION_WARNING,

    UNKNOWN,
    ;

    companion object {
        fun from(viewData: NotificationViewData?): NotificationViewKind {
            return when (viewData) {
                is MentionNotificationViewData,
                is PollNotificationViewData,
                -> STATUS

                is FavouriteNotificationViewData,
                is ReblogNotificationViewData,
                is StatusNotificationViewData,
                is UpdateNotificationViewData,
                -> NOTIFICATION

                is FollowNotificationViewData,
                is SignupNotificationViewData,
                -> FOLLOW

                is FollowRequestNotificationViewData -> FOLLOW_REQUEST
                is ReportNotificationViewData -> REPORT
                is SeveredRelationshipsNotificationViewData -> SEVERED_RELATIONSHIPS
                is ModerationWarningNotificationViewData -> MODERATION_WARNING
                is UnknownNotificationViewData -> UNKNOWN
                null -> UNKNOWN
            }
        }
    }
}

interface NotificationActionListener : StatusActionListener<NotificationViewData.WithStatus> {
    fun onViewReport(reportId: String)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     */
    fun onNotificationContentCollapsedChange(
        isCollapsed: Boolean,
        viewData: NotificationViewData.WithStatus,
    )

    /**
     * Called when the user clicks "Show anyway" to see a notification
     * filtered by the account.
     */
    fun clearAccountFilter(viewData: NotificationViewData)

    /**
     * Called when the user clicks "Edit filter" from a notification
     * filtered by the account.
     */
    fun editAccountNotificationFilter()
}

/**
 * @param diffCallback
 * @param notificationActionListener
 * @param accountActionListener
 * @param statusDisplayOptions
 */
class NotificationsPagingAdapter(
    private val glide: RequestManager,
    diffCallback: DiffUtil.ItemCallback<NotificationViewData>,
    private val setStatusContent: SetStatusContent,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener,
    var statusDisplayOptions: StatusDisplayOptions = StatusDisplayOptions(),
) : PagingDataAdapter<NotificationViewData, RecyclerView.ViewHolder>(diffCallback) {

    /** View holders in this adapter must implement this interface. */
    interface ViewHolder<T : NotificationViewData> {
        /** Bind the data from the notification and payloads to the view. */
        fun bind(
            viewData: T,
            payloads: List<List<Any?>>?,
            statusDisplayOptions: StatusDisplayOptions,
        )
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item is NotificationViewData.WithStatus && item.statusViewDataQ.contentFilterAction == FilterAction.WARN) {
            return NotificationViewKind.STATUS_FILTERED.ordinal
        }

        if (item?.accountFilterDecision is AccountFilterDecision.Warn) {
            return NotificationViewKind.ACCOUNT_FILTERED.ordinal
        }

        return NotificationViewKind.from(item).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (NotificationViewKind.entries[viewType]) {
            NotificationViewKind.STATUS -> {
                StatusViewHolder(
                    ItemStatusBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                    notificationActionListener,
                )
            }
            NotificationViewKind.STATUS_FILTERED -> {
                FilterableStatusViewHolder(
                    ItemStatusWrapperBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                    notificationActionListener,
                )
            }
            NotificationViewKind.ACCOUNT_FILTERED -> {
                FilterableNotificationViewHolder(
                    ItemNotificationFilteredBinding.inflate(inflater, parent, false),
                    notificationActionListener,
                )
            }

            NotificationViewKind.NOTIFICATION -> {
                StatusNotificationViewHolder(
                    ItemStatusNotificationBinding.inflate(inflater, parent, false),
                    glide,
                    setStatusContent,
                    notificationActionListener,
                )
            }
            NotificationViewKind.FOLLOW -> {
                FollowViewHolder(
                    ItemFollowBinding.inflate(inflater, parent, false),
                    glide,
                    notificationActionListener,
                )
            }
            NotificationViewKind.FOLLOW_REQUEST -> {
                FollowRequestViewHolder(
                    ItemFollowRequestBinding.inflate(inflater, parent, false),
                    glide,
                    accountActionListener,
                    notificationActionListener,
                    showHeader = true,
                )
            }
            NotificationViewKind.REPORT -> {
                ReportNotificationViewHolder(
                    ItemReportNotificationBinding.inflate(inflater, parent, false),
                    glide,
                    notificationActionListener,
                )
            }
            NotificationViewKind.SEVERED_RELATIONSHIPS -> {
                SeveredRelationshipsViewHolder(
                    ItemSeveredRelationshipsBinding.inflate(inflater, parent, false),
                )
            }
            NotificationViewKind.MODERATION_WARNING -> {
                ModerationWarningViewHolder(
                    ItemModerationWarningBinding.inflate(inflater, parent, false),
                )
            }
            else -> {
                FallbackNotificationViewHolder(
                    ItemUnknownNotificationBinding.inflate(inflater, parent, false),
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
        payloads: List<Any?>,
    ) {
        bindViewHolder(holder, position, payloads as? List<List<Any?>>?)
    }

    private fun bindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<List<Any?>>?) {
        getItem(position)?.let {
            (holder as ViewHolder<NotificationViewData>).bind(it, payloads, statusDisplayOptions)
        }
    }

    /**
     * Notification view holder to use if no other type is appropriate. Should never normally
     * be used, but is useful when migrating code.
     */
    private class FallbackNotificationViewHolder(
        val binding: ItemUnknownNotificationBinding,
    ) : ViewHolder<UnknownNotificationViewData>, RecyclerView.ViewHolder(binding.root) {
        override fun bind(
            viewData: UnknownNotificationViewData,
            payloads: List<List<Any?>>?,
            statusDisplayOptions: StatusDisplayOptions,
        ) {
            binding.text1.text = binding.root.context.getString(R.string.notification_unknown)
        }
    }
}
