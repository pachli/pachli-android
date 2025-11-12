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
import androidx.core.text.HtmlCompat
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.model.FilterAction
import app.pachli.core.ui.databinding.QuotedStatusContentBinding
import com.bumptech.glide.RequestManager
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

/**
 * Compound view that displays [StatusViewData] as a quote using [QuotedStatusContentBinding].
 */
class QuotedStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : StatusView<StatusViewData, IStatusViewData>(context, attrs, defStyleAttr, defStyleRes) {
    private val binding = QuotedStatusContentBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.quotedStatusContainer.quoteStatusAvatar
    override val avatarInset = binding.quotedStatusContainer.quoteStatusAvatarInset
    override val roleChipGroup = binding.quotedStatusContainer.quoteRoleChipGroup
    override val displayName = binding.quotedStatusContainer.quoteStatusDisplayName
    override val username = binding.quotedStatusContainer.quoteStatusUsername
    override val metaInfo = binding.quotedStatusContainer.quoteStatusMetaInfo
    override val pronouns = binding.quotedStatusContainer.quoteAccountPronouns
    override val contentWarningDescription = binding.quotedStatusContainer.quoteStatusContentWarningDescription
    override val contentWarningButton = binding.quotedStatusContainer.quoteStatusContentWarningButton
    override val content = binding.quotedStatusContainer.quoteStatusContent
    override val buttonToggleContent = binding.quotedStatusContainer.quoteButtonToggleContent
    override val attachmentsView = binding.quotedStatusContainer.quoteAttachmentGrid
    override val pollView = binding.quotedStatusContainer.quoteStatusPoll
    override val cardView = binding.quotedStatusContainer.quoteStatusCardView
    override val translationProvider = binding.quotedStatusContainer.quoteTranslationProvider.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    override fun setupWithStatus(setStatusContent: SetStatusContent, glide: RequestManager, viewData: StatusViewData, listener: StatusActionListener<IStatusViewData>, statusDisplayOptions: StatusDisplayOptions) {
        val filterResults = viewData.actionable.filtered.orEmpty().groupBy { (filter, keywordMatches, statusMatches) -> filter.filterAction }

        val filterAction = viewData.contentFilterAction

        when (filterAction) {
            FilterAction.HIDE -> {
                binding.quotedStatusFiltered.root.hide()
                binding.quotedStatusContainer.root.hide()
                binding.quotedStatusHidden.show()
                return
            }

            FilterAction.WARN -> {
                filterResults[FilterAction.WARN]?.let { filters ->
                    binding.quotedStatusContainer.root.hide()
                    binding.quotedStatusHidden.hide()
                    binding.quotedStatusFiltered.root.show()

                    val label = HtmlCompat.fromHtml(
                        context.getString(
                            R.string.status_filter_placeholder_label_format,
                            filters.first().filter.title,
                        ),
                        HtmlCompat.FROM_HTML_MODE_LEGACY,
                    )

                    binding.root.contentDescription = label
                    binding.quotedStatusFiltered.statusFilterLabel.text = label

                    binding.quotedStatusFiltered.statusFilterShowAnyway.setOnClickListener {
                        listener.clearContentFilter(viewData)
                    }

                    binding.quotedStatusFiltered.statusFilterEditFilter.setOnClickListener {
                        listener.onEditFilterById(viewData.pachliAccountId, filters.first().filter.id)
                    }

                    return
                }
            }

            FilterAction.BLUR,
            FilterAction.NONE,
            -> {
                binding.quotedStatusHidden.hide()
                binding.quotedStatusFiltered.root.hide()
                binding.quotedStatusContainer.root.show()
                super.setupWithStatus(setStatusContent, glide, viewData, listener, statusDisplayOptions)
            }
        }
    }
}
