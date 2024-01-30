/* Copyright 2023 Tusky Contributors
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
import androidx.core.view.ViewCompat
import app.pachli.R
import app.pachli.core.activity.emojify
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.visible
import app.pachli.core.designsystem.R as DR
import app.pachli.databinding.ItemMutedUserBinding
import app.pachli.interfaces.AccountActionListener
import app.pachli.util.BindingHolder

/** Displays a list of muted accounts with mute/unmute account button and mute/unmute notifications switch */
class MutesAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean,
) : AccountAdapter<BindingHolder<ItemMutedUserBinding>>(
    accountActionListener = accountActionListener,
    animateAvatar = animateAvatar,
    animateEmojis = animateEmojis,
    showBotOverlay = showBotOverlay,
) {

    private val mutingNotificationsMap = HashMap<String, Boolean>()

    override fun createAccountViewHolder(parent: ViewGroup): BindingHolder<ItemMutedUserBinding> {
        val binding = ItemMutedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindAccountViewHolder(viewHolder: BindingHolder<ItemMutedUserBinding>, position: Int) {
        val account = accountList[position]
        val binding = viewHolder.binding
        val context = binding.root.context

        val mutingNotifications = mutingNotificationsMap[account.id]

        val emojifiedName = account.name.emojify(account.emojis, binding.mutedUserDisplayName, animateEmojis)
        binding.mutedUserDisplayName.text = emojifiedName

        val formattedUsername = context.getString(R.string.post_username_format, account.username)
        binding.mutedUserUsername.text = formattedUsername

        val avatarRadius = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.mutedUserAvatar, avatarRadius, animateAvatar)

        binding.mutedUserBotBadge.visible(showBotOverlay && account.bot)

        val unmuteString = context.getString(R.string.action_unmute_desc, formattedUsername)
        binding.mutedUserUnmute.contentDescription = unmuteString
        ViewCompat.setTooltipText(binding.mutedUserUnmute, unmuteString)

        binding.mutedUserMuteNotifications.setOnCheckedChangeListener(null)

        binding.mutedUserMuteNotifications.isChecked = if (mutingNotifications == null) {
            binding.mutedUserMuteNotifications.isEnabled = false
            true
        } else {
            binding.mutedUserMuteNotifications.isEnabled = true
            mutingNotifications
        }

        binding.mutedUserUnmute.setOnClickListener {
            accountActionListener.onMute(
                false,
                account.id,
                viewHolder.bindingAdapterPosition,
                false,
            )
        }
        binding.mutedUserMuteNotifications.setOnCheckedChangeListener { _, isChecked ->
            accountActionListener.onMute(
                true,
                account.id,
                viewHolder.bindingAdapterPosition,
                isChecked,
            )
        }
        binding.root.setOnClickListener { accountActionListener.onViewAccount(account.id) }
    }

    fun updateMutingNotifications(id: String, mutingNotifications: Boolean, position: Int) {
        mutingNotificationsMap[id] = mutingNotifications
        notifyItemChanged(position)
    }

    fun updateMutingNotificationsMap(newMutingNotificationsMap: HashMap<String, Boolean>) {
        mutingNotificationsMap.putAll(newMutingNotificationsMap)
        notifyDataSetChanged()
    }
}
