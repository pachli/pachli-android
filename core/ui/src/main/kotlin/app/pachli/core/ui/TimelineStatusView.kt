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
import android.view.View
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.IStatusViewDataQ
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.IStatus
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.ui.databinding.QuotedStatusContentBinding
import app.pachli.core.ui.databinding.StatusContentBinding
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

/** Compound view that displays [StatusViewDataQ] using [StatusContentBinding]. */
class TimelineStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<IStatusViewDataQ>(context, attrs, defStyleAttr, defStyleRes) {
    val binding = StatusContentBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.statusAvatar
    override val avatarInset = binding.statusAvatarInset
    override val roleChipGroup = binding.roleChipGroup
    override val displayName = binding.statusDisplayName
    override val username = binding.statusUsername
    override val metaInfo = binding.statusMetaInfo
    override val pronouns = binding.accountPronouns
    override val contentWarningDescription = binding.statusContentWarningDescription
    override val contentWarningButton = binding.statusContentWarningButton
    override val content = binding.statusContent
    override val buttonToggleContent = binding.buttonToggleContent
    override val attachmentsView = binding.attachmentGrid
    override val pollView = binding.statusPoll
    override val cardView = binding.statusCardView
    override val translationProvider = binding.translationProvider.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    override fun setupWithStatus(setStatusContent: SetStatusContent, glide: RequestManager, viewData: IStatusViewDataQ, listener: StatusActionListener<IStatusViewDataQ>, statusDisplayOptions: StatusDisplayOptions) {
        super.setupWithStatus(setStatusContent, glide, viewData, listener, statusDisplayOptions)

        val quotedViewData = viewData.quotedViewData
        if (quotedViewData == null) {
            binding.statusQuote.hide()
            return
        }

        binding.statusQuote.setupWithStatus(setStatusContent, glide, quotedViewData, L, statusDisplayOptions)
        binding.statusQuote.show()
    }
}

object L : StatusActionListener<StatusViewData> {
    override fun onReply(viewData: StatusViewData) {
        TODO("Not yet implemented")
    }

    override fun onReblog(viewData: StatusViewData, reblog: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onFavourite(viewData: StatusViewData, favourite: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onBookmark(viewData: StatusViewData, bookmark: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onMore(view: View, viewData: StatusViewData) {
        TODO("Not yet implemented")
    }

    override fun onViewAttachment(view: View?, viewData: StatusViewData, attachmentIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun onViewThread(status: Status) {
        TODO("Not yet implemented")
    }

    override fun onOpenReblog(status: IStatus) {
        TODO("Not yet implemented")
    }

    override fun onExpandedChange(viewData: StatusViewData, expanded: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onAttachmentDisplayActionChange(viewData: StatusViewData, newAction: AttachmentDisplayAction) {
        TODO("Not yet implemented")
    }

    override fun onContentCollapsedChange(viewData: StatusViewData, isCollapsed: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onVoteInPoll(viewData: StatusViewData, poll: Poll, choices: List<Int>) {
        TODO("Not yet implemented")
    }

    override fun clearContentFilter(viewData: StatusViewData) {
        TODO("Not yet implemented")
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        TODO("Not yet implemented")
    }

    override fun onViewMedia(pachliAccountId: Long, username: String, url: String) {
        TODO("Not yet implemented")
    }

    override fun onViewTag(tag: String) {
        TODO("Not yet implemented")
    }

    override fun onViewAccount(id: String) {
        TODO("Not yet implemented")
    }

    override fun onViewUrl(url: String) {
        TODO("Not yet implemented")
    }
}

class QuotedStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<StatusViewData>(context, attrs, defStyleAttr, defStyleRes) {
    val binding = QuotedStatusContentBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.statusAvatar
    override val avatarInset = binding.statusAvatarInset
    override val roleChipGroup = binding.roleChipGroup
    override val displayName = binding.statusDisplayName
    override val username = binding.statusUsername
    override val metaInfo = binding.statusMetaInfo
    override val pronouns = binding.accountPronouns
    override val contentWarningDescription = binding.statusContentWarningDescription
    override val contentWarningButton = binding.statusContentWarningButton
    override val content = binding.statusContent
    override val buttonToggleContent = binding.buttonToggleContent
    override val attachmentsView = binding.attachmentGrid
    override val pollView = binding.statusPoll
    override val cardView = binding.statusCardView
    override val translationProvider = binding.translationProvider.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }
}
