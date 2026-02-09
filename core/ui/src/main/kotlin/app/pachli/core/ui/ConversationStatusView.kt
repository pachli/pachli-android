/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import androidx.core.util.TypedValueCompat
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.ConversationViewData
import app.pachli.core.data.model.IStatusItemViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.ui.databinding.StatusContentConversationBinding
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

/**
 * Compound view displaying [app.pachli.core.data.model.ConversationViewData] using
 * [StatusContentConversationBinding].
 *
 * Similar to [TimelineStatusView], except:
 *
 * - The primary avatar is the avatar of the account in the
 * conversation, not the avatar of the account that sent the
 * status being displayed.
 * - Up to two other avatars are shown underneath the primary
 * avatar if multiple accounts are participating in the
 * conversation.
 */
class ConversationStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<ConversationViewData>(context, attrs, defStyleAttr, defStyleRes) {
    val binding = StatusContentConversationBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.statusAvatar
    override val avatarInset = binding.statusAvatarInset
    override val roleChipGroup = binding.roleChipGroup
    override val displayName = binding.statusDisplayName
    override val username = binding.statusUsername
    override val metaInfo = binding.statusMetaInfo
    override val pronounsChip = binding.accountPronouns
    override val contentWarningDescription = binding.statusContentWarningDescription
    override val contentWarningButton: Button = binding.statusContentWarningButton
    override val content = binding.statusContent
    override val buttonToggleContent = binding.buttonToggleContent
    override val attachmentsView = binding.attachmentGrid
    override val pollView = binding.statusPoll
    override val cardView = binding.statusCardView
    override val translationProvider = binding.translationProvider.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private val avatars: Array<ImageView> = arrayOf(
        binding.statusAvatar,
        binding.statusAvatar1,
        binding.statusAvatar2,
    )

    private val avatarPadding = TypedValueCompat.dpToPx(2f, context.resources.displayMetrics).toInt()

    override fun setupWithStatus(setStatusContent: SetStatusContent, glide: RequestManager, viewData: ConversationViewData, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions) {
        super.setupWithStatus(setStatusContent, glide, viewData, listener, statusDisplayOptions)

        // Load additional avatars.
        avatars.forEachIndexed { index, view ->
            viewData.accounts.getOrNull(index)?.also { account ->
                loadAvatar(
                    glide,
                    account.avatar,
                    view,
                    avatarRadius48dp,
                    statusDisplayOptions.animateAvatars,
                )
                view.show()
            } ?: view.hide()
        }

        // setAvatar will have cleared the padding on the first avatar, set it back.
        binding.statusAvatar.setPaddingRelative(avatarPadding, avatarPadding, avatarPadding, avatarPadding)

        val quotedViewData = (viewData as? IStatusItemViewData)?.asQuotedStatusViewData()
        if (quotedViewData == null || !viewData.isShowingContent) {
            binding.statusQuote.hide()
            return
        }

        binding.statusQuote.setupWithStatus(setStatusContent, glide, quotedViewData, listener, statusDisplayOptions)
        binding.statusQuote.show()
    }
}
