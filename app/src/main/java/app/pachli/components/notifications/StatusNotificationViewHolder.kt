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

package app.pachli.components.notifications

import android.graphics.Typeface
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.common.util.SmartLengthInputFilter
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Emoji
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.loadAvatar
import app.pachli.databinding.ItemStatusNotificationBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.getRelativeTimeSpanString
import app.pachli.viewdata.NotificationViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.RequestManager
import java.util.Date

/**
 * View holder for a status with an activity to be notified about (posted, boosted,
 * favourited, or edited, per [NotificationViewKind.from]).
 *
 * Shows a line with the activity, and who initiated the activity. Clicking this should
 * go to the profile page for the initiator.
 *
 * Displays the original status below that. Clicking this should go to the original
 * status in context.
 */
internal class StatusNotificationViewHolder(
    private val binding: ItemStatusNotificationBinding,
    private val glide: RequestManager,
    private val setStatusContent: SetStatusContent,
    private val statusActionListener: StatusActionListener<NotificationViewData>,
    private val notificationActionListener: NotificationActionListener,
    private val absoluteTimeFormatter: AbsoluteTimeFormatter,
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius48dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_48dp,
    )
    private val avatarRadius36dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_36dp,
    )
    private val avatarRadius24dp = itemView.context.resources.getDimensionPixelSize(
        DR.dimen.avatar_radius_24dp,
    )

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val statusViewData = viewData.statusViewData
        if (payloads.isNullOrEmpty()) {
            // Hide null statuses. Shouldn't happen according to the spec, but some servers
            // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
            if (statusViewData == null) {
                showNotificationContent(false)
            } else {
                showNotificationContent(true)
                val account = statusViewData.actionable.account
                val createdAt = statusViewData.actionable.createdAt
                setDisplayName(account.name, account.emojis, statusDisplayOptions.animateEmojis)
                setUsername(account.username)
                setCreatedAt(createdAt, statusDisplayOptions.useAbsoluteTime)
                if (viewData.type == NotificationEntity.Type.STATUS ||
                    viewData.type == NotificationEntity.Type.UPDATE
                ) {
                    setAvatar(
                        account.avatar,
                        account.bot,
                        statusDisplayOptions.animateAvatars,
                        statusDisplayOptions.showBotOverlay,
                    )
                } else {
                    setAvatars(
                        account.avatar,
                        viewData.account.avatar,
                        statusDisplayOptions.animateAvatars,
                    )
                }

                binding.notificationContainer.setOnClickListener {
                    notificationActionListener.onViewThreadForStatus(viewData.status)
                }
                binding.notificationContent.setOnClickListener {
                    notificationActionListener.onViewThreadForStatus(viewData.status)
                }
                binding.notificationTopText.setOnClickListener {
                    notificationActionListener.onViewAccount(viewData.account.id)
                }
            }
            setMessage(viewData, statusActionListener, statusDisplayOptions)
        } else {
            for (item in payloads) {
                if (StatusBaseViewHolder.Key.KEY_CREATED == item && statusViewData != null) {
                    setCreatedAt(
                        viewData.actionable.createdAt,
                        statusDisplayOptions.useAbsoluteTime,
                    )
                }
            }
        }
    }

    private fun showNotificationContent(show: Boolean) {
        binding.statusDisplayName.visibility = if (show) View.VISIBLE else View.GONE
        binding.statusUsername.visibility = if (show) View.VISIBLE else View.GONE
        binding.statusMetaInfo.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationContentWarningDescription.visibility =
            if (show) View.VISIBLE else View.GONE
        binding.notificationContentWarningButton.visibility =
            if (show) View.VISIBLE else View.GONE
        binding.notificationContent.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationStatusAvatar.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationNotificationAvatar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setDisplayName(name: String, emojis: List<Emoji>?, animateEmojis: Boolean) {
        val emojifiedName = name.emojify(glide, emojis, binding.statusDisplayName, animateEmojis)
        binding.statusDisplayName.text = emojifiedName
    }

    private fun setUsername(name: String) {
        val context = binding.statusUsername.context
        val format = context.getString(DR.string.post_username_format)
        val usernameText = String.format(format, name)
        binding.statusUsername.text = usernameText
    }

    private fun setCreatedAt(createdAt: Date?, useAbsoluteTime: Boolean) {
        if (useAbsoluteTime) {
            binding.statusMetaInfo.text = absoluteTimeFormatter.format(createdAt, true)
        } else {
            // This is the visible timestampInfo.
            val readout: String
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */
            val readoutAloud: CharSequence
            if (createdAt != null) {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                readout = getRelativeTimeSpanString(binding.statusMetaInfo.context, then, now)
                readoutAloud = DateUtils.getRelativeTimeSpanString(
                    then,
                    now,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                )
            } else {
                // unknown minutes~
                readout = "?m"
                readoutAloud = "? minutes"
            }
            binding.statusMetaInfo.text = readout
            binding.statusMetaInfo.contentDescription = readoutAloud
        }
    }

    private fun setAvatar(statusAvatarUrl: String?, isBot: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean) {
        binding.notificationStatusAvatar.setPaddingRelative(0, 0, 0, 0)
        loadAvatar(
            glide,
            statusAvatarUrl,
            binding.notificationStatusAvatar,
            avatarRadius48dp,
            animateAvatars,
        )
        if (showBotOverlay && isBot) {
            binding.notificationNotificationAvatar.visibility = View.VISIBLE
            glide.load(DR.drawable.bot_badge)
                .into(binding.notificationNotificationAvatar)
        } else {
            binding.notificationNotificationAvatar.visibility = View.GONE
        }
    }

    private fun setAvatars(statusAvatarUrl: String?, notificationAvatarUrl: String?, animateAvatars: Boolean) {
        val padding = Utils.dpToPx(binding.notificationStatusAvatar.context, 12)
        binding.notificationStatusAvatar.setPaddingRelative(0, 0, padding, padding)
        loadAvatar(
            glide,
            statusAvatarUrl,
            binding.notificationStatusAvatar,
            avatarRadius36dp,
            animateAvatars,
        )
        binding.notificationNotificationAvatar.visibility = View.VISIBLE
        loadAvatar(
            glide,
            notificationAvatarUrl,
            binding.notificationNotificationAvatar,
            avatarRadius24dp,
            animateAvatars,
        )
    }

    fun setMessage(
        viewData: NotificationViewData,
        listener: LinkListener,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val statusViewData = viewData.statusViewData
        val displayName = viewData.account.name.unicodeWrap()
        val type = viewData.type
        val context = binding.notificationTopText.context
        val format: String
        val icon = type.icon(context)
        when (type) {
            NotificationEntity.Type.FAVOURITE -> {
                format = context.getString(R.string.notification_favourite_format)
            }

            NotificationEntity.Type.REBLOG -> {
                format = context.getString(R.string.notification_reblog_format)
            }

            NotificationEntity.Type.STATUS -> {
                format = context.getString(R.string.notification_subscription_format)
            }

            NotificationEntity.Type.UPDATE -> {
                format = context.getString(R.string.notification_update_format)
            }
            else -> {
                format = context.getString(R.string.notification_favourite_format)
            }
        }
        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(
            icon,
            null,
            null,
            null,
        )
        val wholeMessage = String.format(format, displayName)
        val str = SpannableStringBuilder(wholeMessage)
        val displayNameIndex = format.indexOf("%s")
        str.setSpan(
            StyleSpan(Typeface.BOLD),
            displayNameIndex,
            displayNameIndex + displayName.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val emojifiedText = str.emojify(
            glide,
            viewData.account.emojis,
            binding.notificationTopText,
            statusDisplayOptions.animateEmojis,
        )
        binding.notificationTopText.text = emojifiedText

        statusViewData ?: return

        val hasSpoiler = !TextUtils.isEmpty(statusViewData.status.spoilerText)
        binding.notificationContentWarningDescription.visibility =
            if (hasSpoiler) View.VISIBLE else View.GONE
        binding.notificationContentWarningButton.visibility =
            if (hasSpoiler) View.VISIBLE else View.GONE
        if (statusViewData.isExpanded) {
            binding.notificationContentWarningButton.setText(
                R.string.post_content_warning_show_less,
            )
        } else {
            binding.notificationContentWarningButton.setText(
                R.string.post_content_warning_show_more,
            )
        }
        binding.notificationContentWarningButton.setOnClickListener {
            if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                notificationActionListener.onExpandedChange(
                    viewData,
                    !statusViewData.isExpanded,
                )
            }
            binding.notificationContent.visibility =
                if (statusViewData.isExpanded) View.GONE else View.VISIBLE
        }
        setupContentAndSpoiler(listener, viewData, statusViewData, statusDisplayOptions)
    }

    private fun setupContentAndSpoiler(
        listener: LinkListener,
        viewData: NotificationViewData,
        statusViewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        // animateEmojis: Boolean,
    ) {
        val shouldShowContentIfSpoiler = statusViewData.isExpanded
        val hasSpoiler = !TextUtils.isEmpty(statusViewData.status.spoilerText)
        if (!shouldShowContentIfSpoiler && hasSpoiler) {
            binding.notificationContent.visibility = View.GONE
        } else {
            binding.notificationContent.visibility = View.VISIBLE
        }
        val content = statusViewData.content
        val emojis = statusViewData.actionable.emojis
        if (statusViewData.isCollapsible && (statusViewData.isExpanded || !hasSpoiler)) {
            binding.buttonToggleNotificationContent.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notificationActionListener.onNotificationContentCollapsedChange(
                        !statusViewData.isCollapsed,
                        viewData,
                    )
                }
            }
            binding.buttonToggleNotificationContent.visibility = View.VISIBLE
            if (statusViewData.isCollapsed) {
                binding.buttonToggleNotificationContent.setText(
                    R.string.post_content_warning_show_more,
                )
                binding.notificationContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleNotificationContent.setText(
                    R.string.post_content_warning_show_less,
                )
                binding.notificationContent.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleNotificationContent.visibility = View.GONE
            binding.notificationContent.filters = NO_INPUT_FILTER
        }
        setStatusContent(
            glide,
            binding.notificationContent,
            content,
            statusDisplayOptions,
            emojis,
            statusViewData.actionable.mentions,
            statusViewData.actionable.tags,
            listener,
        )

        val emojifiedContentWarning: CharSequence = statusViewData.spoilerText.emojify(
            glide,
            statusViewData.actionable.emojis,
            binding.notificationContentWarningDescription,
            statusDisplayOptions.animateEmojis,
        )
        binding.notificationContentWarningDescription.text = emojifiedContentWarning
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
