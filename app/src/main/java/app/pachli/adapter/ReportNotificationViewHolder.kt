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
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.notifications.NotificationActionListener
import app.pachli.components.notifications.NotificationsPagingAdapter
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.TimelineAccount
import app.pachli.core.ui.getRelativeTimeSpanString
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.updateEmojiTargets
import app.pachli.databinding.ItemReportNotificationBinding
import app.pachli.viewdata.NotificationViewData
import com.bumptech.glide.RequestManager

class ReportNotificationViewHolder(
    private val binding: ItemReportNotificationBinding,
    private val glide: RequestManager,
    private val notificationActionListener: NotificationActionListener,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<List<Any?>>?,
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
        binding.notificationTopText.updateEmojiTargets(glide) {
            val reporterName = reporter.name.unicodeWrap().emojify(glide, reporter.emojis, animateEmojis)
            val reporteeName = report.targetAccount.displayName.unicodeWrap().emojify(glide, report.targetAccount.emojis, animateEmojis)

            // Context.getString() returns a String and doesn't support Spannable.
            // Convert the placeholders to the format used by TextUtils.expandTemplate which does.
            val topText =
                view.context.getString(R.string.notification_header_report_format, "^1", "^2")
            view.text = TextUtils.expandTemplate(topText, reporterName, reporteeName)
        }

        val icon = AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_flag_24dp)

        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        binding.notificationSummary.text = itemView.context.resources.getQuantityString(
            R.plurals.notification_summary_report_format,
            report.statusIds?.size ?: 0,
            getRelativeTimeSpanString(itemView.context, report.createdAt.toEpochMilli(), System.currentTimeMillis()),
            report.statusIds?.size ?: 0,
        )
        binding.notificationCategory.text = itemView.context.getString(
            R.string.title_report_category_fmt,
            getTranslatedCategory(itemView.context, report.category),
        )

        if (report.comment.isNotBlank()) {
            binding.titleReportComment.show()
            binding.reportComment.text = report.comment
            binding.reportComment.show()
        } else {
            binding.titleReportComment.hide()
            binding.reportComment.hide()
        }

        // Fancy avatar inset
        val padding = dpToPx(12f, binding.notificationReporteeAvatar.context.resources.displayMetrics).toInt()
        binding.notificationReporteeAvatar.setPaddingRelative(0, 0, padding, padding)

        loadAvatar(
            glide,
            report.targetAccount.avatar,
            binding.notificationReporteeAvatar,
            itemView.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp),
            animateAvatar,
        )
        loadAvatar(
            glide,
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
