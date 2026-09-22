/*
 * Copyright (c) 2026 Pachli Association
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

import android.os.Bundle
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.model.collection.CollectionDisplayAction
import app.pachli.core.model.collection.CollectionDisplayReason
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate
import app.pachli.interfaces.AccountActionListener
import app.pachli.util.ListStatusAccessibilityDelegate

fun interface NotificationProvider<T : NotificationViewData> {
    fun getNotification(pos: Int): T?
}

/**
 * Delegate for actions on different notification types.
 *
 * Notifications with a status delegate to a [ListStatusAccessibilityDelegate].
 *
 * Other notification types are handled here.
 */
class NotificationAccessibilityDelegate<T : NotificationViewData>(
    pachliAccountId: Long,
    private val recyclerView: RecyclerView,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener,
    openUrl: OpenUrlUseCase,
    private val notificationProvider: NotificationProvider<T>,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    /** Delegate for actions on notifications with a status. */
    private val statusAccessibilityDelegate = ListStatusAccessibilityDelegate(
        pachliAccountId = pachliAccountId,
        recyclerView = recyclerView,
        statusActionListener = notificationActionListener,
        openUrl = openUrl,
    ) { pos -> notificationProvider.getNotification(pos) as? NotificationViewData.WithStatus }

    override fun getItemDelegate(): AccessibilityDelegateCompat = itemDelegate

    private val itemDelegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host)

            if (viewHolder is FilterableNotificationViewHolder) {
                info.addAction(showNotificationAnywayAction)
                info.addAction(editNotificationFilterAction)
                return
            }

            val pos = recyclerView.getChildAdapterPosition(host)
            val notification = notificationProvider.getNotification(pos) ?: return

            when (notification) {
                is NotificationViewData.WithStatus -> statusAccessibilityDelegate.itemDelegate.onInitializeAccessibilityNodeInfo(host, info)

                is NotificationViewData.FollowNotificationViewData -> {
                    info.addAction(openProfileAction)
                }

                is NotificationViewData.FollowRequestNotificationViewData -> {
                    info.addAction(acceptFollowRequestAction)
                    info.addAction(rejectFollowRequestAction)
                }

                is NotificationViewData.ModerationWarningNotificationViewData -> {
                    info.addAction(viewModerationWarningAction)
                }

                is NotificationViewData.ReportNotificationViewData -> {
                    info.addAction(openReportAction)
                    info.addAction(openProfileAction)
                    info.addAction(openReporteeProfileAction)
                }

                is NotificationViewData.SeveredRelationshipsNotificationViewData -> {
                    // No actions.
                }

                is NotificationViewData.SignupNotificationViewData -> {
                    info.addAction(openProfileAction)
                }

                is NotificationViewData.WithCollection.CollectionAddNotificationViewData -> {
                    val collectionCardViewData = notification.collectionCardViewData
                    if (collectionCardViewData.displayAction is CollectionDisplayAction.Hide) {
                        info.addAction(showCollectionAction)
                    } else {
                        info.addAction(viewCollectionAction)
                        collectionCardViewData.hashtag?.let {
                            info.addAction(viewCollectionHashtagAction)
                        }
                        info.addAction(hideCollectionAction)
                        if (collectionCardViewData.isMember) {
                            info.addAction(revokeCollectionPermissionAction)
                        }
                    }
                }

                is NotificationViewData.WithCollection.CollectionUpdateNotificationViewData -> {
                    val collectionCardViewData = notification.collectionCardViewData
                    if (collectionCardViewData.displayAction is CollectionDisplayAction.Hide) {
                        info.addAction(showCollectionAction)
                    } else {
                        info.addAction(viewCollectionAction)
                        collectionCardViewData.hashtag?.let {
                            info.addAction(viewCollectionHashtagAction)
                        }
                        info.addAction(hideCollectionAction)
                        if (collectionCardViewData.isMember) {
                            info.addAction(revokeCollectionPermissionAction)
                        }
                    }
                }

                is NotificationViewData.UnknownNotificationViewData -> {
                    // No actions.
                }
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val pos = recyclerView.getChildAdapterPosition(host)
            val notification = notificationProvider.getNotification(pos) ?: return false

            when (action) {
                showNotificationAnywayAction.id -> {
                    interrupt()
                    notificationActionListener.clearAccountFilter(notification)
                }

                editNotificationFilterAction.id -> {
                    interrupt()
                    notificationActionListener.editAccountNotificationFilter()
                }

                openProfileAction.id -> {
                    interrupt()
                    notificationActionListener.onViewAccount(notification.account.accountId)
                }

                acceptFollowRequestAction.id -> {
                    interrupt()
                    accountActionListener.onRespondToFollowRequest(
                        true,
                        notification.account.accountId,
                        pos,
                    )
                }

                rejectFollowRequestAction.id -> {
                    interrupt()
                    accountActionListener.onRespondToFollowRequest(
                        false,
                        notification.account.accountId,
                        pos,
                    )
                }

                viewModerationWarningAction.id -> {
                    interrupt()
                    host.performClick()
                }

                openReportAction.id -> {
                    interrupt()
                    (notification as? NotificationViewData.ReportNotificationViewData)?.let {
                        notificationActionListener.onViewReport(notification.report.reportId)
                    }
                }

                openReporteeProfileAction.id -> (notification as? NotificationViewData.ReportNotificationViewData)?.let {
                    interrupt()
                    notificationActionListener.onViewAccount(
                        notification.report.targetAccount.accountId,
                    )
                }

                showCollectionAction.id -> (notification as? NotificationViewData.WithCollection)?.let {
                    interrupt()
                    notificationActionListener.onCollectionDisplayActionChange(
                        notification.collectionCardViewData,
                        CollectionDisplayAction.Show(
                            notification.collectionCardViewData.displayAction as? CollectionDisplayAction.Hide,
                        ),
                    )
                }

                hideCollectionAction.id -> (notification as? NotificationViewData.WithCollection)?.let {
                    interrupt()
                    val displayAction = notification.collectionCardViewData.displayAction

                    notificationActionListener.onCollectionDisplayActionChange(
                        notification.collectionCardViewData,
                        CollectionDisplayAction.Hide(
                            (displayAction as? CollectionDisplayAction.Show)?.originalAction?.reason ?: CollectionDisplayReason.UserAction,
                        ),
                    )
                }

                viewCollectionAction.id -> (notification as? NotificationViewData.WithCollection)?.let {
                    interrupt()
                    notificationActionListener.onViewCollection(
                        notification.collectionCardViewData,
                    )
                }

                viewCollectionHashtagAction.id -> (notification as? NotificationViewData.WithCollection)?.let {
                    notification.collectionCardViewData.hashtag?.name?.let {
                        interrupt()
                        notificationActionListener.onViewTag(it)
                    }
                }

                revokeCollectionPermissionAction.id -> (notification as? NotificationViewData.WithCollection)?.let {
                    interrupt()
                    notificationActionListener.onRevokeUserFromCollection(notification.collectionCardViewData)
                }

                else -> return if (notification is NotificationViewData.WithStatus) {
                    statusAccessibilityDelegate.itemDelegate.performAccessibilityAction(host, action, args)
                } else {
                    super.performAccessibilityAction(host, action, args)
                }
            }

            return true
        }
    }

    private val showNotificationAnywayAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_show_notification_anyway,
        context.getString(app.pachli.core.ui.R.string.status_filtered_show_anyway),
    )

    private val editNotificationFilterAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_edit_notification_filter,
        context.getString(app.pachli.core.ui.R.string.filter_edit_title),
    )

    private val openProfileAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_profile,
        context.getString(app.pachli.core.ui.R.string.action_view_profile),
    )

    private val acceptFollowRequestAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_follow_request_accept,
        context.getString(app.pachli.core.ui.R.string.action_follow_request_accept),
    )

    private val rejectFollowRequestAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_follow_request_reject,
        context.getString(app.pachli.core.ui.R.string.action_follow_request_reject),
    )

    private val viewModerationWarningAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_moderation_warning_view,
        context.getString(app.pachli.core.ui.R.string.action_moderation_warning_view),
    )

    private val openReporteeProfileAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_reportee_profile,
        context.getString(app.pachli.core.ui.R.string.action_open_reportee_profile),
    )

    private val openReportAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_report,
        context.getString(app.pachli.core.ui.R.string.action_open_report),
    )

    /** Show a hidden collection. */
    private val showCollectionAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collection_show,
        context.getString(app.pachli.core.ui.R.string.action_collection_show),
    )

    /** Hide a visible collection. */
    private val hideCollectionAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collection_hide,
        context.getString(app.pachli.core.ui.R.string.action_collection_hide),
    )

    /** View a collection's members. */
    private val viewCollectionAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collection_view,
        context.getString(app.pachli.core.ui.R.string.action_collection_view),
    )

    /** Revoke permission for the user's account in a collection. */
    private val revokeCollectionPermissionAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collection_revoke,
        context.getString(app.pachli.core.ui.R.string.action_collection_revoke),
    )

    /* View the hashtag associated with a collection. */
    private val viewCollectionHashtagAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collection_view_tag,
        context.getString(app.pachli.core.ui.R.string.action_collection_view_tag),
    )
}
