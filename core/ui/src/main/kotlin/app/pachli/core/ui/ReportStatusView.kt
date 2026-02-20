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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.ui.databinding.StatusContentBinding
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

/**
 * Compound view that displays [StatusViewData] using [StatusContentBinding] when
 * reviewing as part of the "report an account" flow.
 *
 * Similar to [TimelineStatusView], except:
 *
 * - Interaction with polls is disabled.
 */
class ReportStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<StatusItemViewData>(context, attrs, defStyleAttr, defStyleRes) {
    val binding = StatusContentBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.statusAvatar
    override val avatarInset = binding.statusAvatarInset
    override val roleChipGroup = binding.roleChipGroup
    override val displayName = binding.statusDisplayName
    override val username = binding.statusUsername
    override val metaInfo = binding.statusMetaInfo
    override val pronounsChip = binding.accountPronouns
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

    override fun setupWithStatus(setContent: SetContent, glide: RequestManager, viewData: StatusItemViewData, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions) {
        super.setupWithStatus(setContent, glide, viewData, listener, statusDisplayOptions)

        // Can't vote while reporting statuses.
        binding.statusPoll.isEnabled = false

        val quote = viewData.actionable.quote
        if (!viewData.isShowingContent || quote == null) {
            binding.statusQuote.hide()
            return
        }

        val quotedViewData = viewData.asQuotedStatusViewData()
        binding.statusQuote.setupWithStatus(setContent, glide, quote.state, quotedViewData, listener, statusDisplayOptions)
        binding.statusQuote.show()
    }
}
