/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report.adapter

import android.text.TextUtils
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.report.model.StatusViewState
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.common.util.shouldTrimStatus
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.model.Emoji
import app.pachli.core.model.HashTag
import app.pachli.core.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.PollViewData
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.getRelativeTimeSpanString
import app.pachli.core.ui.setClickableMentions
import app.pachli.databinding.ItemReportStatusBinding
import app.pachli.util.StatusViewHelper
import app.pachli.util.StatusViewHelper.Companion.COLLAPSE_INPUT_FILTER
import app.pachli.util.StatusViewHelper.Companion.NO_INPUT_FILTER
import com.bumptech.glide.RequestManager
import java.util.Date

open class StatusViewHolder(
    private val binding: ItemReportStatusBinding,
    private val glide: RequestManager,
    private val setStatusContent: SetStatusContent,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val viewState: StatusViewState,
    private val adapterHandler: AdapterHandler,
    private val getStatusForPosition: (Int) -> StatusViewData?,
) : RecyclerView.ViewHolder(binding.root) {
    private val statusViewHelper = StatusViewHelper(glide, itemView, adapterHandler)
    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    init {
        binding.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            viewdata()?.let { viewdata ->
                adapterHandler.setStatusChecked(viewdata.status, isChecked)
            }
        }
        binding.attachmentGrid.clipToOutline = true
    }

    fun bind(viewData: StatusViewData) {
        binding.statusSelection.isChecked = adapterHandler.isStatusChecked(viewData.id)

        updateTextView()

        statusViewHelper.setMediaPreviews(
            viewData,
            statusDisplayOptions,
            viewState.isMediaShow(viewData.id, viewData.status.sensitive),
        )

        viewData.status.poll?.let {
            statusViewHelper.setupPollReadonly(PollViewData.from(it), viewData.status.emojis, statusDisplayOptions)
        } ?: statusViewHelper.hidePoll()

        setCreatedAt(viewData.status.createdAt)
    }

    private fun updateTextView() {
        viewdata()?.let { viewdata ->
            setupCollapsedState(
                shouldTrimStatus(viewdata.content.parseAsMastodonHtml()),
                viewState.isCollapsed(viewdata.id, true),
                viewState.isContentShow(viewdata.id, viewdata.status.sensitive),
                viewdata.spoilerText,
            )

            if (viewdata.spoilerText.isBlank()) {
                setTextVisible(true, viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
                binding.statusContentWarningButton.hide()
                binding.statusContentWarningDescription.hide()
            } else {
                val emojiSpoiler = viewdata.spoilerText.emojify(glide, viewdata.status.emojis, binding.statusContentWarningDescription, statusDisplayOptions.animateEmojis)
                binding.statusContentWarningDescription.text = emojiSpoiler
                binding.statusContentWarningDescription.show()
                binding.statusContentWarningButton.show()
                setContentWarningButtonText(viewState.isContentShow(viewdata.id, true))
                binding.statusContentWarningButton.setOnClickListener {
                    viewdata()?.let { viewdata ->
                        val contentShown = viewState.isContentShow(viewdata.id, true)
                        binding.statusContentWarningDescription.invalidate()
                        viewState.setContentShow(viewdata.id, !contentShown)
                        setTextVisible(!contentShown, viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
                        setContentWarningButtonText(!contentShown)
                    }
                }
                setTextVisible(viewState.isContentShow(viewdata.id, true), viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
            }
        }
    }

    private fun setContentWarningButtonText(contentShown: Boolean) {
        if (contentShown) {
            binding.statusContentWarningButton.setText(R.string.post_content_warning_show_less)
        } else {
            binding.statusContentWarningButton.setText(R.string.post_content_warning_show_more)
        }
    }

    private fun setTextVisible(
        expanded: Boolean,
        content: CharSequence,
        mentions: List<Status.Mention>,
        tags: List<HashTag>?,
        emojis: List<Emoji>,
        listener: LinkListener,
    ) {
        if (expanded) {
            setStatusContent(glide, binding.statusContent, content, statusDisplayOptions, emojis, mentions, tags, listener)
        } else {
            setClickableMentions(binding.statusContent, mentions, listener)
        }
        if (binding.statusContent.text.isNullOrBlank()) {
            binding.statusContent.hide()
        } else {
            binding.statusContent.show()
        }
    }

    private fun setCreatedAt(createdAt: Date?) {
        if (statusDisplayOptions.useAbsoluteTime) {
            binding.timestampInfo.text = absoluteTimeFormatter.format(createdAt)
        } else {
            binding.timestampInfo.text = if (createdAt != null) {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                getRelativeTimeSpanString(binding.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                "?m"
            }
        }
    }

    private fun setupCollapsedState(collapsible: Boolean, collapsed: Boolean, expanded: Boolean, spoilerText: String) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            binding.buttonToggleContent.setOnClickListener {
                viewdata()?.let { viewdata ->
                    viewState.setCollapsed(viewdata.id, !collapsed)
                    updateTextView()
                }
            }

            binding.buttonToggleContent.show()
            if (collapsed) {
                binding.buttonToggleContent.setText(R.string.post_content_show_more)
                binding.statusContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleContent.setText(R.string.post_content_show_less)
                binding.statusContent.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleContent.hide()
            binding.statusContent.filters = NO_INPUT_FILTER
        }
    }

    private fun viewdata() = getStatusForPosition(bindingAdapterPosition)
}
