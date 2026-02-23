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
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.QuotedStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Status
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
) : StatusView<QuotedStatusViewData>(context, attrs, defStyleAttr, defStyleRes) {
    private val binding = QuotedStatusContentBinding.inflate(LayoutInflater.from(context), this)

    override val avatar = binding.quotedStatusContainer.quoteStatusAvatar
    override val avatarInset = binding.quotedStatusContainer.quoteStatusAvatarInset
    override val roleChipGroup = binding.quotedStatusContainer.quoteRoleChipGroup
    override val displayName = binding.quotedStatusContainer.quoteStatusDisplayName
    override val username = binding.quotedStatusContainer.quoteStatusUsername
    override val metaInfo = binding.quotedStatusContainer.quoteStatusMetaInfo
    override val pronounsChip = binding.quotedStatusContainer.quoteAccountPronouns
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

    /** Bottom padding to use on the root view if the "Remove quote" button is shown. */
    private val paddingBottomWithRemoveQuote = dpToPx(6f, context.resources.displayMetrics).toInt()

    /** Bottom padding to use on the root view if the "Remove quote" button is not shown. */
    private val paddingBottomWithoutRemoveQuote = dpToPx(14f, context.resources.displayMetrics).toInt()

    /**
     * Binds the [viewData] to the view, respecting [quoteState].
     *
     * Call this instead of [setupWithStatus(setStatusContent: SetStatusContent, glide: RequestManager, viewData: QuotedStatusViewData, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions)].
     *
     * @param setContent
     * @param glide
     * @param quoteState Whether to display the quote. If not
     * [QuoteState.ACCEPTED][Status.QuoteState.ACCEPTED] the quote is hidden and an
     * explanatory message is shown.
     * @param viewData
     * @param listener
     * @param statusDisplayOptions
     */
    fun setupWithStatus(setContent: SetContent, glide: RequestManager, quoteState: Status.QuoteState, viewData: QuotedStatusViewData?, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions) {
        val blockedRes = when (quoteState) {
            Status.QuoteState.ACCEPTED -> -1
            Status.QuoteState.UNKNOWN -> R.string.label_quote_state_unknown
            Status.QuoteState.PENDING -> R.string.label_quote_state_pending
            Status.QuoteState.REJECTED -> R.string.label_quote_state_rejected
            Status.QuoteState.REVOKED -> R.string.label_quote_state_revoked
            Status.QuoteState.DELETED -> R.string.label_quote_state_deleted
            Status.QuoteState.UNAUTHORIZED -> R.string.label_quote_state_unauthorized
            Status.QuoteState.BLOCKED_ACCOUNT -> R.string.label_quote_state_blocked_account
            Status.QuoteState.BLOCKED_DOMAIN -> R.string.label_quote_state_blocked_domain
            Status.QuoteState.MUTED_ACCOUNT -> R.string.label_quote_state_muted_account
        }

        if (blockedRes != -1) {
            binding.quotedStatusFiltered.root.hide()
            binding.quotedStatusContainer.root.hide()
            binding.quotedStatusHidden.setText(blockedRes)
            binding.quotedStatusHidden.show()
            return
        }

        if (viewData == null) {
            binding.quotedStatusFiltered.root.hide()
            binding.quotedStatusContainer.root.hide()
            binding.quotedStatusHidden.setText(R.string.label_quote_state_unknown)
            binding.quotedStatusHidden.show()
            return
        }

        setupWithStatus(setContent, glide, viewData, listener, statusDisplayOptions)
    }

    override fun setupWithStatus(setContent: SetContent, glide: RequestManager, viewData: QuotedStatusViewData, listener: StatusActionListener, statusDisplayOptions: StatusDisplayOptions) {
        when (viewData.contentFilterAction) {
            FilterAction.HIDE -> {
                binding.quotedStatusFiltered.root.hide()
                binding.quotedStatusContainer.root.hide()
                binding.quotedStatusHidden.setText(R.string.label_quoted_status_hidden)
                binding.quotedStatusHidden.show()
                return
            }

            FilterAction.WARN -> {
                val filterResults = viewData.actionable.filtered.orEmpty().groupBy { (filter, _, _) -> filter.filterAction }
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
                super.setupWithStatus(setContent, glide, viewData, listener, statusDisplayOptions)

                if (viewData.isUsersStatus) {
                    // The user posted this, provide an option to detach the quote.
                    // Adjust the bottom padding for visual balance and show the
                    // "Remove quote" button.
                    with(binding.quotedStatusContainer) {
                        root.setPadding(
                            root.paddingStart,
                            root.paddingTop,
                            root.paddingRight,
                            paddingBottomWithRemoveQuote,
                        )

                        quoteStatusControlDivider.show()
                        quoteStatusDetachQuoteButton.show()
                        quoteStatusDetachQuoteButton.setOnClickListener {
                            listener.onDetachQuote(viewData.actionableId, viewData.parentId)
                        }
                    }
                } else {
                    // No "Remove quote" button and section. Restore the bottom
                    // padding and hide the controls.
                    with(binding.quotedStatusContainer) {
                        root.setPadding(
                            root.paddingStart,
                            root.paddingTop,
                            root.paddingRight,
                            paddingBottomWithoutRemoveQuote,
                        )

                        quoteStatusControlDivider.hide()
                        quoteStatusDetachQuoteButton.hide()
                        quoteStatusDetachQuoteButton.setOnClickListener(null)
                    }
                }
            }
        }
    }
}
