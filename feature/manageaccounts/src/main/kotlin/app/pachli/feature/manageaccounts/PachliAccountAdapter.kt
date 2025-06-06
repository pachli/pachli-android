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

package app.pachli.feature.manageaccounts

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.domain.notifications.AccountNotificationMethod
import app.pachli.core.domain.notifications.NotificationConfig
import app.pachli.core.domain.notifications.hasPushScope
import app.pachli.core.domain.notifications.notificationMethod
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.asDdHhMmSs
import app.pachli.core.ui.extensions.instantFormatter
import app.pachli.core.ui.loadAvatar
import app.pachli.feature.manageaccounts.PachliAccountViewHolder.ChangePayload
import app.pachli.feature.manageaccounts.databinding.ItemPachliAccountBinding
import com.bumptech.glide.RequestManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import java.time.Duration
import java.time.Instant

/**
 * Adapter for [PachliAccount].
 *
 * Accounts are shown with:
 *
 * - Avatar
 * - Display name
 * - Account name
 * - Notification fetch details
 *
 * The active account is highlighted.
 *
 * Buttons are provided to allow the user to switch to the account or log out of
 * the account.
 *
 * @param glide [RequestManager] used to load avatars and other images.
 * @param animateAvatars See [PachliAccountAdapter.animateAvatars].
 * @param animateEmojis See [PachliAccountAdapter.animateEmojis]
 * @param showBotOverlay See [PachliAccountAdapter.showBotOverlay]
 * @param accept Handler to call when the user acts on the UI.
 */
internal class PachliAccountAdapter(
    private val glide: RequestManager,
    animateAvatars: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean,
    private val accept: onUiAction,
) : ListAdapter<PachliAccount, PachliAccountViewHolder>(PachliAccountDiffer) {
    /**
     * True if avatars should be animated.
     *
     * Setting a different value will update the list.
     */
    var animateAvatars = animateAvatars
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, currentList.size, ChangePayload.AnimateAvatars(value))
        }

    /**
     * True if emojis should be animated.
     *
     * Setting a different value will update the list.
     */
    var animateEmojis = animateEmojis
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, currentList.size, ChangePayload.AnimateEmojis(value))
        }

    /**
     * True if bot accounts should show a badge.
     *
     * Setting a different value will update the list.
     */
    var showBotOverlay = showBotOverlay
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, currentList.size, ChangePayload.ShowBotOverlay(value))
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PachliAccountViewHolder {
        return PachliAccountViewHolder(
            ItemPachliAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            glide,
            accept,
        )
    }

    override fun onBindViewHolder(holder: PachliAccountViewHolder, position: Int, payloads: List<Any?>) {
        getItem(position)?.let {
            holder.bind(it, animateEmojis, animateAvatars, showBotOverlay, payloads)
        }
    }

    override fun onBindViewHolder(holder: PachliAccountViewHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(it, animateEmojis, animateAvatars, showBotOverlay)
        }
    }
}

internal class PachliAccountViewHolder(
    internal val binding: ItemPachliAccountBinding,
    private val glide: RequestManager,
    accept: onUiAction,
) : RecyclerView.ViewHolder(binding.root) {
    private val context = binding.root.context

    private val avatarRadius by unsafeLazy {
        context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.avatar_radius_48dp)
    }

    /** The account being displayed. */
    internal lateinit var account: PachliAccount

    /** Payloads for partial notification of item changes. */
    internal sealed interface ChangePayload {
        /** The [animateAvatars] state of the UI has changed. */
        data class AnimateAvatars(val animateAvatars: Boolean) : ChangePayload

        /** The [animateEmojis] state of the UI has changed. */
        data class AnimateEmojis(val animateEmojis: Boolean) : ChangePayload

        /** The [showBotOverlay] state of the UI has changed. */
        data class ShowBotOverlay(val showBotOverlay: Boolean) : ChangePayload
    }

    init {
        with(binding) {
            deleteAccount.setOnClickListener { accept(UiAction.Logout(account)) }
            switchAccount.setOnClickListener { accept(UiAction.Switch(account)) }
        }
    }

    fun bind(
        account: PachliAccount,
        animateEmojis: Boolean,
        animateAvatars: Boolean,
        showBotOverlay: Boolean,
        payloads: List<Any?>? = null,
    ) = with(binding) {
        this@PachliAccountViewHolder.account = account

        if (payloads == null || payloads.isEmpty()) {
            bindAll(account, animateEmojis, animateAvatars, showBotOverlay)
            return@with
        }

        payloads.forEach { payload ->
            when (payload) {
                is ChangePayload.AnimateAvatars -> bindAvatar(account, animateAvatars)
                is ChangePayload.AnimateEmojis -> bindAnimateEmojis(account, animateEmojis)
                is ChangePayload.ShowBotOverlay -> bindShowBotOverlay(account, showBotOverlay)
            }
        }
    }

    private fun bindAll(account: PachliAccount, animateEmojis: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean) = with(binding) {
        root.isActivated = account.entity.isActive
        suggestionReason.visible(account.entity.isActive)
        switchAccount.isEnabled = !account.entity.isActive

        // Set the content description to the account's fullname, and indicate if it's the
        // active account.
        //
        // The display name is not included, it's more to read out, and the user probably
        // doesn't care.
        //
        // The notification information is not included -- it's a lot to read out, and if
        // remote debugging then screenshots are more useful.
        root.contentDescription = if (account.entity.isActive) {
            context.getString(
                R.string.pachli_account_content_description_fmt,
                account.entity.fullName,
            )
        } else {
            account.entity.fullName
        }

        bindAvatar(account, animateAvatars)
        bindShowBotOverlay(account, showBotOverlay)
        bindAnimateEmojis(account, animateEmojis)

        notificationMethod.text = context.getString(account.entity.notificationMethod.stringRes)
        notificationMethodExtra.text = account.entity.notificationMethodExtra(context)

        val lastFetch = NotificationConfig.lastFetchNewNotifications[account.entity.fullName]
        if (lastFetch == null) {
            lastFetchTime.hide()
            lastFetchError.hide()
        } else {
            val now = Instant.now()
            val instant = lastFetch.first
            val result = lastFetch.second

            val (resTimestamp, error) = when (result) {
                is Ok -> Pair(app.pachli.core.ui.R.string.pref_notification_fetch_ok_timestamp_fmt, null)
                is Err -> Pair(app.pachli.core.ui.R.string.pref_notification_fetch_err_timestamp_fmt, result.error)
            }

            lastFetchTime.text = context.getString(
                resTimestamp,
                Duration.between(instant, now).asDdHhMmSs(),
                instantFormatter.format(instant),
            )

            lastFetchTime.show()
            if (error != null) {
                lastFetchError.text = error
                lastFetchError.show()
            } else {
                lastFetchError.hide()
            }
        }
    }

    /** Loads the account's avatar, respecting [animateAvatars]. */
    private fun bindAvatar(account: PachliAccount, animateAvatars: Boolean) = with(binding) {
        loadAvatar(glide, account.entity.profilePictureUrl, avatar, avatarRadius, animateAvatars)
    }

    /**
     * Display's the bot overlay on the avatar image (if appropriate), respecting
     * [showBotOverlay].
     */
    private fun bindShowBotOverlay(account: PachliAccount, showBotOverlay: Boolean) = with(binding) {
        avatarBadge.isVisible = account.entity.isBot && showBotOverlay
    }

    /** Displays the account's name/username respecting [animateEmojis]. */
    private fun bindAnimateEmojis(account: PachliAccount, animateEmojis: Boolean) = with(binding) {
        displayName.text = account.entity.displayName.unicodeWrap().emojify(
            glide,
            account.emojis,
            displayName,
            animateEmojis,
        )
        username.text = account.entity.fullName
    }
}

private object PachliAccountDiffer : DiffUtil.ItemCallback<PachliAccount>() {
    override fun areItemsTheSame(oldItem: PachliAccount, newItem: PachliAccount) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: PachliAccount, newItem: PachliAccount) = oldItem == newItem
}

/** String resource for the account's notification method. */
@get:StringRes
private val AccountNotificationMethod.stringRes: Int
    get() = when (this) {
        AccountNotificationMethod.PUSH -> app.pachli.core.ui.R.string.pref_notification_method_push
        AccountNotificationMethod.PULL -> app.pachli.core.ui.R.string.pref_notification_method_pull
    }

/**
 * String to show as the "extra" for the notification method.
 *
 * If the notification method is [PUSH][AccountNotificationMethod.PUSH] this should be the
 * URL notifications are delivered to.
 *
 * Otherwise this should explain why the method is [PULL][AccountNotificationMethod.PULL]
 * (either the error when registering, or the lack of the `push` oauth scope).
 */
private fun AccountEntity.notificationMethodExtra(context: Context): String {
    return when (notificationMethod) {
        AccountNotificationMethod.PUSH -> unifiedPushUrl
        AccountNotificationMethod.PULL -> if (hasPushScope) {
            context.getString(app.pachli.core.ui.R.string.pref_notification_fetch_server_rejected, domain)
        } else {
            context.getString(app.pachli.core.ui.R.string.pref_notification_fetch_needs_push)
        }
    }
}
