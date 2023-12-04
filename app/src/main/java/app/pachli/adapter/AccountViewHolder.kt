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

import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.network.model.TimelineAccount
import app.pachli.databinding.ItemAccountBinding
import app.pachli.interfaces.AccountActionListener
import app.pachli.interfaces.LinkListener
import app.pachli.util.emojify
import app.pachli.util.loadAvatar
import app.pachli.util.visible

class AccountViewHolder(
    private val binding: ItemAccountBinding,
) : RecyclerView.ViewHolder(binding.root) {
    private lateinit var accountId: String

    fun setupWithAccount(
        account: TimelineAccount,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
        showBotOverlay: Boolean,
    ) {
        accountId = account.id

        binding.accountUsername.text = binding.accountUsername.context.getString(
            R.string.post_username_format,
            account.username,
        )

        val emojifiedName = account.name.emojify(
            account.emojis,
            binding.accountDisplayName,
            animateEmojis,
        )
        binding.accountDisplayName.text = emojifiedName

        val avatarRadius = binding.accountAvatar.context.resources
            .getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.accountAvatar, avatarRadius, animateAvatar)

        binding.accountBotBadge.visible(showBotOverlay && account.bot)
    }

    fun setupActionListener(listener: AccountActionListener) {
        itemView.setOnClickListener { listener.onViewAccount(accountId) }
    }

    fun setupLinkListener(listener: LinkListener) {
        itemView.setOnClickListener {
            listener.onViewAccount(
                accountId,
            )
        }
    }
}
