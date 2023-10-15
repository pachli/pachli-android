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

package app.pachli.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.adapter.PollAdapter
import app.pachli.databinding.StatusPollBinding
import app.pachli.entity.Emoji
import app.pachli.util.AbsoluteTimeFormatter
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.formatPollDuration
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.viewdata.PollViewData
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import java.text.NumberFormat

/**
 * Compound view that displays [PollViewData].
 *
 * If the poll is still active / the user hasn't voted then poll options are shown as radio
 * buttons / checkboxes (depending on whether or not it is multiple choice) and the user can
 * vote.
 *
 * Otherwise the results of the poll are shown.
 *
 * Classes hosting this should provide a [PollView.OnClickListener] to be notified when the
 * user clicks on the poll (either to vote, or to navigate).
 */
class PollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    fun interface OnClickListener {
        /**
         * @param choices If null the user has clicked on the poll without voting and this
         *      should be treated as a navigation click. If non-null the user has voted,
         *      and [choices] contains the option(s) they voted for.
         */
        fun onClick(choices: List<Int>?): Unit
    }

    val binding: StatusPollBinding

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = StatusPollBinding.inflate(inflater, this)
        orientation = VERTICAL
    }

    fun bind(
        pollViewData: PollViewData,
        emojis: List<Emoji>,
        statusDisplayOptions: StatusDisplayOptions,
        numberFormat: NumberFormat,
        absoluteTimeFormatter: AbsoluteTimeFormatter,
        listener: OnClickListener,
    ) {
        val adapter = PollAdapter()
        binding.statusPollOptions.adapter = adapter
        binding.statusPollOptions.layoutManager = LinearLayoutManager(context)
        (binding.statusPollOptions.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        val now = System.currentTimeMillis()

        binding.statusPollOptions.show()

        binding.statusPollDescription.text = getPollInfoText(
            pollViewData,
            now,
            statusDisplayOptions,
            numberFormat,
            absoluteTimeFormatter,
        )
        binding.statusPollDescription.show()

        val expired = pollViewData.expired || ((pollViewData.expiresAt != null) && (now > pollViewData.expiresAt.time))

        // Poll expired or already voted, can't vote now
        if (expired || pollViewData.voted) {
            adapter.setup(
                pollViewData.options,
                pollViewData.votesCount,
                pollViewData.votersCount,
                emojis,
                PollAdapter.RESULT,
                { listener.onClick(null) },
                statusDisplayOptions.animateEmojis,
            )
            binding.statusPollButton.hide()
            return
        }

        // Active poll, can vote
        adapter.setup(
            pollViewData.options,
            pollViewData.votesCount,
            pollViewData.votersCount,
            emojis,
            if (pollViewData.multiple) PollAdapter.MULTIPLE else PollAdapter.SINGLE,
            null,
            statusDisplayOptions.animateEmojis,
            true,
        ) {
            binding.statusPollButton.isEnabled = adapter.getSelected().isNotEmpty()
        }

        binding.statusPollButton.show()
        binding.statusPollButton.isEnabled = false
        binding.statusPollButton.setOnClickListener {
            val selected = adapter.getSelected()
            if (selected.isNotEmpty()) listener.onClick(selected)
        }
    }

    fun hide() {
        binding.statusPollOptions.hide()
        binding.statusPollButton.hide()
        binding.statusPollDescription.hide()
    }

    private fun getPollInfoText(
        pollViewData: PollViewData,
        timestamp: Long,
        statusDisplayOptions: StatusDisplayOptions,
        numberFormat: NumberFormat,
        absoluteTimeFormatter: AbsoluteTimeFormatter,
    ): CharSequence {
        val votesText: String = if (pollViewData.votersCount == null) {
            val voters = numberFormat.format(pollViewData.votesCount.toLong())
            context.resources.getQuantityString(
                R.plurals.poll_info_votes,
                pollViewData.votesCount,
                voters,
            )
        } else {
            val voters = numberFormat.format(pollViewData.votersCount)
            context.resources.getQuantityString(
                R.plurals.poll_info_people,
                pollViewData.votersCount,
                voters,
            )
        }
        val pollDurationInfo: CharSequence = if (pollViewData.expired) {
            context.getString(R.string.poll_info_closed)
        } else if (pollViewData.expiresAt == null) {
            return votesText
        } else {
            if (statusDisplayOptions.useAbsoluteTime) {
                context.getString(
                    R.string.poll_info_time_absolute,
                    absoluteTimeFormatter.format(pollViewData.expiresAt, false),
                )
            } else {
                formatPollDuration(context, pollViewData.expiresAt.time, timestamp)
            }
        }
        return context.getString(
            R.string.poll_info_format,
            votesText,
            pollDurationInfo,
        )
    }

    @SuppressLint("StringFormatMatches") // Lint doesn't understand the spread (*) operator
    fun getPollDescription(
        pollViewData: PollViewData,
        statusDisplayOptions: StatusDisplayOptions,
        numberFormat: NumberFormat,
        absoluteTimeFormatter: AbsoluteTimeFormatter,
    ): CharSequence {
        val args: Array<CharSequence?> = arrayOfNulls(5)
        val options = pollViewData.options
        val totalVotes = pollViewData.votesCount
        val totalVoters = pollViewData.votersCount
        for (i in args.indices) {
            if (i < options.size) {
                val percent = calculatePercent(options[i].votesCount, totalVoters, totalVotes)
                args[i] = buildDescription(options[i].title, percent, options[i].voted, context)
            } else {
                args[i] = ""
            }
        }
        args[4] = getPollInfoText(
            pollViewData,
            System.currentTimeMillis(),
            statusDisplayOptions,
            numberFormat,
            absoluteTimeFormatter,
        )
        return context.getString(R.string.description_poll, *args)
    }
}
