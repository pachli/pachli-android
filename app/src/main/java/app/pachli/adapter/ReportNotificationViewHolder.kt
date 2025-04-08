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

package app.pachli.adapter

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.notifications.NotificationActionListener
import app.pachli.components.notifications.NotificationsPagingAdapter
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.TimelineAccount
import app.pachli.databinding.ItemReportNotificationBinding
import app.pachli.util.getRelativeTimeSpanString
import app.pachli.viewdata.NotificationViewData
import at.connyduck.sparkbutton.helpers.Utils

class ReportNotificationViewHolder(
    private val binding: ItemReportNotificationBinding,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Skip updates with payloads. That indicates a timestamp update, and
        // this view does not have timestamps.
        if (!payloads.isNullOrEmpty()) return

        setupWithReport(
            viewData.account,
            viewData.report!!,
            statusDisplayOptions.animateAvatars,
            statusDisplayOptions.animateEmojis,
        )
        setupActionListener(
            notificationActionListener,
            viewData.report.targetAccount.serverId,
            viewData.account.id,
            viewData.report.reportId,
        )
    }

    private fun setupWithReport(
        reporter: TimelineAccount,
        report: NotificationReportEntity,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
    ) {
        val reporterName = reporter.name.unicodeWrap().emojify(
            reporter.emojis,
            binding.root,
            animateEmojis,
        )
        val reporteeName = report.targetAccount.toTimelineAccount().name.unicodeWrap().emojify(
            report.targetAccount.emojis,
            itemView,
            animateEmojis,
        )
        val icon = AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_flag_24dp)

        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        binding.notificationTopText.text = itemView.context.getString(
            R.string.notification_header_report_format,
            reporterName,
            reporteeName,
        )
        binding.notificationSummary.text = itemView.context.getString(
            R.string.notification_summary_report_format,
            getRelativeTimeSpanString(itemView.context, report.createdAt.toEpochMilli(), System.currentTimeMillis()),
            report.statusIds?.size ?: 0,
        )
        binding.notificationCategory.text = getTranslatedCategory(itemView.context, report.category)

        // Fancy avatar inset
        val padding = Utils.dpToPx(binding.notificationReporteeAvatar.context, 12)
        binding.notificationReporteeAvatar.setPaddingRelative(0, 0, padding, padding)

        loadAvatar(
            report.targetAccount.avatar,
            binding.notificationReporteeAvatar,
            itemView.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp),
            animateAvatar,
        )
        loadAvatar(
            reporter.avatar,
            binding.notificationReporterAvatar,
            itemView.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_24dp),
            animateAvatar,
        )
    }

    private fun setupActionListener(
        listener: NotificationActionListener,
        reporteeId: String,
        reporterId: String,
        reportId: String,
    ) {
        binding.notificationReporteeAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewAccount(reporteeId)
            }
        }
        binding.notificationReporterAvatar.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewAccount(reporterId)
            }
        }

        itemView.setOnClickListener { listener.onViewReport(reportId) }
    }

    private fun getTranslatedCategory(context: Context, category: NotificationReportEntity.Category) = when (category) {
        NotificationReportEntity.Category.SPAM -> context.getString(R.string.report_category_spam)
        NotificationReportEntity.Category.VIOLATION -> context.getString(R.string.report_category_violation)
        NotificationReportEntity.Category.OTHER -> context.getString(R.string.report_category_other)
    }
}
