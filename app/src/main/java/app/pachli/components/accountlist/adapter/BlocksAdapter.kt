/* Copyright 2017 Andrew Dawson
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

package app.pachli.components.accountlist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import app.pachli.R
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.visible
import app.pachli.core.designsystem.R as DR
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemBlockedUserBinding
import app.pachli.interfaces.AccountActionListener

/** Displays a list of blocked accounts. */
class BlocksAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean,
) : AccountAdapter<BindingHolder<ItemBlockedUserBinding>>(
    accountActionListener = accountActionListener,
    animateAvatar = animateAvatar,
    animateEmojis = animateEmojis,
    showBotOverlay = showBotOverlay,
) {

    override fun createAccountViewHolder(parent: ViewGroup): BindingHolder<ItemBlockedUserBinding> {
        val binding = ItemBlockedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindAccountViewHolder(viewHolder: BindingHolder<ItemBlockedUserBinding>, position: Int) {
        val account = accountList[position]
        val binding = viewHolder.binding
        val context = binding.root.context

        val emojifiedName = account.name.emojify(account.emojis, binding.blockedUserDisplayName, animateEmojis)
        binding.blockedUserDisplayName.text = emojifiedName
        val formattedUsername = context.getString(R.string.post_username_format, account.username)
        binding.blockedUserUsername.text = formattedUsername

        val avatarRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.blockedUserAvatar, avatarRadius, animateAvatar)

        binding.blockedUserBotBadge.visible(showBotOverlay && account.bot)

        binding.blockedUserUnblock.setOnClickListener {
            accountActionListener.onBlock(false, account.id, position)
        }
        binding.root.setOnClickListener {
            accountActionListener.onViewAccount(account.id)
        }
    }
}
