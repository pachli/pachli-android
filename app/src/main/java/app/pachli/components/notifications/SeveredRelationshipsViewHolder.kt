/*
 * Copyright 2024 Pachli Association
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

import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.network.model.RelationshipSeveranceEvent
import app.pachli.databinding.ItemSeveredRelationshipsBinding
import app.pachli.util.getRelativeTimeSpanString
import app.pachli.viewdata.NotificationViewData

class SeveredRelationshipsViewHolder(
    private val binding: ItemSeveredRelationshipsBinding,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val event = viewData.relationshipSeveranceEvent!!
        if (payloads.isNullOrEmpty()) {
            binding.notificationTopText.text = HtmlCompat.fromHtml(
                itemView.context.getString(
                    R.string.notification_severed_relationships_format,
                    event.targetName,
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )

            binding.datetime.text = getRelativeTimeSpanString(itemView.context, event.createdAt.toEpochMilli(), System.currentTimeMillis())

            binding.notificationFollowersCount.text = itemView.context.resources.getQuantityString(
                R.plurals.notification_severed_relationships_summary_followers_fmt,
                event.followersCount,
                event.followersCount,
            )

            binding.notificationFollowingCount.text = itemView.context.resources.getQuantityString(
                R.plurals.notification_severed_relationships_summary_following_fmt,
                event.followingCount,
                event.followingCount,
            )

            val resourceId = when (event.type) {
                RelationshipSeveranceEvent.Type.DOMAIN_BLOCK -> R.string.notification_severed_relationships_domain_block_body
                RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK -> R.string.notification_severed_relationships_user_domain_block_body
                RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION -> R.string.notification_severed_relationships_account_suspension_body
                RelationshipSeveranceEvent.Type.UNKNOWN -> R.string.notification_severed_relationships_unknown_body
            }
            binding.notificationCategory.text = itemView.context.getString(resourceId)
        } else {
            if (payloads.any { it == StatusBaseViewHolder.Key.KEY_CREATED }) {
                binding.datetime.text = getRelativeTimeSpanString(itemView.context, event.createdAt.toEpochMilli(), System.currentTimeMillis())
            }
        }
    }
}
