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

package app.pachli.components.notifications

import android.content.Intent
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.AccountWarning
import app.pachli.databinding.ItemModerationWarningBinding
import app.pachli.viewdata.NotificationViewData

class ModerationWarningViewHolder(
    private val binding: ItemModerationWarningBinding,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    var viewData: NotificationViewData? = null

    init {
        binding.root.setOnClickListener {
            viewData?.let {
                val intent = Intent(Intent.ACTION_VIEW, "https://${it.localDomain}/disputes/strikes/${it.accountWarning!!.id}".toUri())
                binding.root.context.startActivity(intent)
            }
        }
    }

    override fun bind(viewData: NotificationViewData, payloads: List<List<Any?>>?, statusDisplayOptions: StatusDisplayOptions) {
        this.viewData = viewData
        val context = itemView.context
        val warning = viewData.accountWarning!!

        val stringRes = when (warning.action) {
            AccountWarning.Action.NONE -> R.string.notification_moderation_warning_body_none_fmt
            AccountWarning.Action.DISABLE -> R.string.notification_moderation_warning_body_disable_fmt
            AccountWarning.Action.MARK_STATUSES_AS_SENSITIVE -> R.string.notification_moderation_warning_body_mark_statuses_as_sensitive_fmt
            AccountWarning.Action.DELETE_STATUSES -> R.string.notification_moderation_warning_body_delete_statuses_fmt
            AccountWarning.Action.SILENCE -> R.string.notification_moderation_warning_body_silence_fmt
            AccountWarning.Action.SUSPEND -> R.string.notification_moderation_warning_body_suspend_fmt
            AccountWarning.Action.UNKNOWN -> R.string.notification_moderation_warning_body_unknown_fmt
        }
        binding.notificationBody.text = context.getString(stringRes, warning.text)
    }
}
