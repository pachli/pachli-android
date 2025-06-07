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
import app.pachli.core.network.model.RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION
import app.pachli.core.network.model.RelationshipSeveranceEvent.Type.DOMAIN_BLOCK
import app.pachli.core.network.model.RelationshipSeveranceEvent.Type.UNKNOWN
import app.pachli.core.network.model.RelationshipSeveranceEvent.Type.USER_DOMAIN_BLOCK
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
        val context = itemView.context
        val event = viewData.relationshipSeveranceEvent!!

        if (payloads.isNullOrEmpty()) {
            val topTextHtml = when (event.type) {
                DOMAIN_BLOCK -> context.getString(
                    R.string.notification_severed_relationships_domain_block_fmt,
                    viewData.localDomain,
                    event.targetName,
                )

                USER_DOMAIN_BLOCK -> context.getString(
                    R.string.notification_severed_relationships_user_domain_block_fmt,
                    event.targetName,
                )

                ACCOUNT_SUSPENSION -> context.getString(
                    R.string.notification_severed_relationships_account_suspension_fmt,
                    viewData.localDomain,
                    event.targetName,
                )

                UNKNOWN -> context.getString(
                    R.string.notification_severed_relationships_unknown_fmt,
                    event.targetName,
                )
            }

            binding.notificationTopText.text = HtmlCompat.fromHtml(topTextHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

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
        } else {
            if (payloads.any { it == StatusBaseViewHolder.Key.KEY_CREATED }) {
                binding.datetime.text = getRelativeTimeSpanString(itemView.context, event.createdAt.toEpochMilli(), System.currentTimeMillis())
            }
        }
    }
}
