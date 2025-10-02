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

package app.pachli.util

import android.text.InputFilter
import android.view.View
import android.widget.TextView
import app.pachli.R
import app.pachli.components.report.adapter.AdapterHandler
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.common.util.SmartLengthInputFilter
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.Emoji
import app.pachli.core.model.Status
import app.pachli.core.ui.AttachmentsView
import app.pachli.core.ui.PollViewData
import app.pachli.core.ui.buildDescription
import app.pachli.core.ui.calculatePercent
import app.pachli.core.ui.emojify
import app.pachli.core.ui.formatPollDuration
import com.bumptech.glide.RequestManager
import com.google.android.material.color.MaterialColors
import java.text.NumberFormat

// TODO:
//
// This is a copy of functionality in StatusBaseViewHolder so that
// report.adapter.StatusViewHolder can use it.
//
// The duplication is not great since it's easy for changes to get
// out of sync. Better to encapsulate this behaviour in custom
// views where possible, and where not, to ensure that
// StatusBaseViewHolder also uses this code.

class StatusViewHelper(
    private val glide: RequestManager,
    private val itemView: View,
    private val listener: AdapterHandler,
) {
    /** [AttachmentsView] that encompasses and lays out all the attachment previews. */
    private val attachmentsView: AttachmentsView = itemView.findViewById(R.id.attachmentGrid)

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    fun setMediaPreviews(
        viewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        mediaPreviewEnabled: Boolean,
    ) {
        val actionable = viewData.actionable
        val attachments = if (viewData.translationState == TranslationState.SHOW_TRANSLATION) {
            viewData.translation?.attachments?.zip(actionable.attachments) { t, a ->
                a.copy(description = t.description)
            } ?: actionable.attachments
        } else {
            actionable.attachments
        }

        attachmentsView.bind(
            glide,
            viewData,
            attachments,
            mediaPreviewEnabled,
            statusDisplayOptions.useBlurhash,
            onViewAttachment = { view, viewData, index -> listener.showMedia(view, viewData.status, index) },
            listener::onAttachmentDisplayActionChange,
        )
    }

    /**
     * Configures and shows poll views based on [poll].
     */
    fun setupPollReadonly(poll: PollViewData, emojis: List<Emoji>, statusDisplayOptions: StatusDisplayOptions) {
        val pollResults = listOf<TextView>(
            itemView.findViewById(R.id.status_poll_option_result_0),
            itemView.findViewById(R.id.status_poll_option_result_1),
            itemView.findViewById(R.id.status_poll_option_result_2),
            itemView.findViewById(R.id.status_poll_option_result_3),
        )

        val pollDescription = itemView.findViewById<TextView>(R.id.status_poll_description)

        val timestamp = System.currentTimeMillis()

        setupPollResult(poll, emojis, pollResults, statusDisplayOptions.animateEmojis)

        pollDescription.show()
        pollDescription.text = getPollInfoText(timestamp, poll, pollDescription, statusDisplayOptions.useAbsoluteTime)
    }

    /**
     * Hides views related to polls.
     */
    fun hidePoll() {
        val pollResults = listOf<TextView>(
            itemView.findViewById(R.id.status_poll_option_result_0),
            itemView.findViewById(R.id.status_poll_option_result_1),
            itemView.findViewById(R.id.status_poll_option_result_2),
            itemView.findViewById(R.id.status_poll_option_result_3),
        )

        for (pollResult in pollResults) {
            pollResult.hide()
        }
        itemView.findViewById<TextView>(R.id.status_poll_description).hide()
    }

    private fun getPollInfoText(timestamp: Long, poll: PollViewData, pollDescription: TextView, useAbsoluteTime: Boolean): CharSequence {
        val context = pollDescription.context

        val votesText = if (poll.votersCount == null) {
            val votes = NumberFormat.getNumberInstance().format(poll.votesCount.toLong())
            context.resources.getQuantityString(app.pachli.core.ui.R.plurals.poll_info_votes, poll.votesCount, votes)
        } else {
            poll.votersCount?.let { votersCount ->
                val votes = NumberFormat.getNumberInstance().format(votersCount.toLong())
                context.resources.getQuantityString(app.pachli.core.ui.R.plurals.poll_info_people, votersCount, votes)
            }
        }
        val pollDurationInfo = if (poll.expired) {
            context.getString(app.pachli.core.ui.R.string.poll_info_closed)
        } else {
            if (useAbsoluteTime) {
                context.getString(app.pachli.core.ui.R.string.poll_info_time_absolute, absoluteTimeFormatter.format(poll.expiresAt, false))
            } else {
                formatPollDuration(context, poll.expiresAt!!.time, timestamp)
            }
        }

        return context.getString(app.pachli.core.ui.R.string.poll_info_format, votesText, pollDurationInfo)
    }

    private fun setupPollResult(poll: PollViewData, emojis: List<Emoji>, pollResults: List<TextView>, animateEmojis: Boolean) {
        val options = poll.options

        // TODO: This is very similar to code in PollAdapter.onBindViewHolder, investigate how it
        // can best be reused.
        for (i in 0 until Status.MAX_POLL_OPTIONS) {
            if (i < options.size) {
                val percent = calculatePercent(options[i].votesCount, poll.votersCount, poll.votesCount)

                val pollOptionText = buildDescription(options[i].title, percent, options[i].voted, pollResults[i].context)
                pollResults[i].text = pollOptionText.emojify(glide, emojis, pollResults[i], animateEmojis)
                pollResults[i].visibility = View.VISIBLE

                val level = percent * 100

                val optionColor: Int
                val textColor: Int
                if (options[i].voted) {
                    optionColor = MaterialColors.getColor(pollResults[i], com.google.android.material.R.attr.colorPrimary)
                    textColor = MaterialColors.getColor(pollResults[i], com.google.android.material.R.attr.colorOnPrimary)
                } else {
                    optionColor = MaterialColors.getColor(pollResults[i], com.google.android.material.R.attr.colorSecondary)
                    textColor = MaterialColors.getColor(pollResults[i], com.google.android.material.R.attr.colorOnSecondary)
                }

                pollResults[i].background.level = level
                pollResults[i].background.setTint(optionColor)
                pollResults[i].setTextColor(textColor)
            } else {
                pollResults[i].visibility = View.GONE
            }
        }
    }

    companion object {
        val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
