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
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.IStatusItemViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.ui.databinding.StatusContentDetailedBinding
import app.pachli.core.ui.extensions.description
import app.pachli.core.ui.extensions.icon
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.text.DateFormat
import java.util.Locale

/**
 * Compound view displaying [StatusViewData] using
 * [StatusContentDetailedBinding].
 *
 * The "detailed" status is shown when the user opens a thread as the
 * "focused" thread, parent/child statuses are shown relative to this
 * one.
 *
 * Differences from [TimelineStatusView] include:
 *
 * - The overall layout.
 * - Status timestamp is shown in full.
 * - Status language is shown.
 * - The status statistics are shown (per user preference) in a separate
 * row, and not part of the status controls.
 */
class DetailedStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<StatusItemViewData>(context, attrs, defStyleAttr, defStyleRes) {
    val binding = StatusContentDetailedBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.statusAvatar
    override val avatarInset = binding.statusAvatarInset
    override val roleChipGroup = binding.roleChipGroup
    override val displayName = binding.statusDisplayName
    override val metaInfo = binding.statusMetaInfo
    override val username = binding.statusUsername
    override val pronounsChip = binding.accountPronouns
    override val contentWarningDescription = binding.statusContentWarningDescription
    override val contentWarningButton = binding.statusContentWarningButton
    override val content = binding.statusContent
    override val buttonToggleContent = null
    override val attachmentsView = binding.attachmentGrid
    override val pollView = binding.statusPoll
    override val cardView = binding.statusCardView
    override val translationProvider = binding.translationProvider.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    override fun setupWithStatus(setContent: SetContent, glide: RequestManager, viewData: StatusItemViewData, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions) {
        // We never collapse statuses in the detail view
        val uncollapsedViewdata = if (viewData.isCollapsible && viewData.isCollapsed) {
            viewData.copy(statusViewData = viewData.statusViewData.copy(isCollapsed = false))
        } else {
            viewData
        }

        super.setupWithStatus(setContent, glide, uncollapsedViewdata, listener, statusDisplayOptions)

        if (!statusDisplayOptions.hideStatsInDetailedView) {
            setStatCounters(uncollapsedViewdata, listener)
        } else {
            hideQuantitativeStats()
        }

        val quote = viewData.actionable.quote
        if (!viewData.isShowingContent || quote == null) {
            binding.statusQuote.hide()
            return
        }

        val quotedViewData = (viewData as? IStatusItemViewData)?.asQuotedStatusViewData()
        binding.statusQuote.setupWithStatus(setContent, glide, quote.state, quotedViewData, listener, statusDisplayOptions)
        binding.statusQuote.show()
    }

    private fun setStatCounters(
        viewData: StatusItemViewData,
        listener: StatusActionListener,
    ) {
        val reblogCount = viewData.actionable.reblogsCount
        val favCount = viewData.actionable.favouritesCount
        val quotesCount = viewData.actionable.quotesCount

        val showStats = reblogCount > 0 || favCount > 0 || quotesCount > 0

        if (!showStats) {
            hideQuantitativeStats()
            return
        }

        binding.statusInfoDivider.show()

        if (reblogCount > 0) {
            binding.statusReblogs.text = getReblogsCountDescription(reblogCount)
            binding.statusReblogs.show()
            binding.statusReblogs.setOnClickListener {
                listener.onShowReblogs(viewData.actionableId)
            }
        } else {
            binding.statusReblogs.hide()
        }

        if (favCount > 0) {
            binding.statusFavourites.text = getFavouritesCountDescription(favCount)
            binding.statusFavourites.show()
            binding.statusFavourites.setOnClickListener {
                listener.onShowFavs(viewData.actionableId)
            }
        } else {
            binding.statusFavourites.hide()
        }

        if (quotesCount > 0) {
            binding.statusQuotes.text = getQuotesCountDescription(quotesCount)
            binding.statusQuotes.show()
            binding.statusQuotes.setOnClickListener {
                listener.onShowQuotes(viewData.actionableId)
            }
        } else {
            binding.statusQuotes.hide()
        }
    }

    private fun hideQuantitativeStats() {
        binding.statusInfoDivider.hide()
        binding.statusReblogs.hide()
        binding.statusFavourites.hide()
        binding.statusQuotes.hide()
        binding.statusReblogs.setOnClickListener(null)
        binding.statusFavourites.setOnClickListener(null)
        binding.statusQuotes.setOnClickListener(null)
    }

    override fun setMetaData(viewData: StatusItemViewData, statusDisplayOptions: StatusDisplayOptions, listener: StatusActionListener) {
        val createdAt = viewData.actionable.createdAt
        val editedAt = viewData.actionable.editedAt
        val visibility = viewData.actionable.visibility
        val app = viewData.actionable.application

        val visibilityIcon = visibility.icon(binding.statusMetaInfo)
        val visibilityString = visibility.description(context)
        val sb = SpannableStringBuilder(visibilityString)
        visibilityIcon?.also {
            val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
            val visibilityIconSpan = ImageSpan(it, alignment)
            sb.setSpan(visibilityIconSpan, 0, visibilityString?.length ?: 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val metadataJoiner = context.getString(R.string.metadata_joiner)
        sb.append(" ")
        sb.append(dateFormat.format(createdAt))

        viewData.status.language?.also {
            sb.append(metadataJoiner)
            sb.append(it.uppercase(Locale.getDefault()))
        }

        editedAt?.also {
            val editedAtString = context.getString(R.string.post_edited, dateFormat.format(it))
            sb.append(metadataJoiner)
            val spanStart = sb.length
            val spanEnd = spanStart + editedAtString.length
            sb.append(editedAtString)
            viewData.status.editedAt?.also {
                val editedClickSpan = NoUnderlineURLSpan(viewData.actionableId, listener::onShowEdits)
                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        app?.also { (name, website) ->
            sb.append(metadataJoiner)
            website?.also { sb.append(createClickableText(name, it, listener::onViewUrl)) } ?: sb.append(name)
        }

        binding.statusMetaInfo.movementMethod = LinkMovementMethod.getInstance()
        binding.statusMetaInfo.text = sb
    }

    // Always show the card for detailed statuses.
    override fun setupCard(glide: RequestManager, viewData: StatusItemViewData, expanded: Boolean, cardViewMode: CardViewMode, statusDisplayOptions: StatusDisplayOptions, listener: StatusActionListener) {
        super.setupCard(glide, viewData, viewData.isExpanded, CardViewMode.FULL_WIDTH, statusDisplayOptions, listener)
    }

    companion object {
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
    }
}
