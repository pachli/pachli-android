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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.adapter.PollAdapter
import app.pachli.adapter.PollOptionClickListener
import app.pachli.adapter.ResultClickListener
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.network.model.Emoji
import app.pachli.databinding.StatusPollBinding
import app.pachli.util.formatPollDuration
import app.pachli.viewdata.PollViewData
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import java.text.NumberFormat

/**
 * @param choices If null the user has clicked on the poll without voting and this
 *      should be treated as a navigation click. If non-null the user has voted,
 *      and [choices] contains the option(s) they voted for.
 */
typealias PollClickListener = (choices: List<Int>?) -> Unit

/**
 * Compound view that displays [PollViewData].
 *
 * If the poll is still active / the user hasn't voted then poll options are shown as radio
 * buttons / checkboxes (depending on whether or not it is multiple choice) and the user can
 * vote.
 *
 * Otherwise the results of the poll are shown.
 *
 * Classes hosting this should provide a [PollClickListener] to be notified when the
 * user clicks on the poll (either to vote, or to navigate).
 */
class PollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {
    val binding: StatusPollBinding

    init {
        val inflater = context.getSystemService(LayoutInflater::class.java)
        binding = StatusPollBinding.inflate(inflater, this)
    }

    fun bind(
        pollViewData: PollViewData,
        emojis: List<Emoji>,
        statusDisplayOptions: StatusDisplayOptions,
        numberFormat: NumberFormat,
        absoluteTimeFormatter: AbsoluteTimeFormatter,
        listener: PollClickListener,
    ) {
        val now = System.currentTimeMillis()
        var displayMode: PollAdapter.DisplayMode = PollAdapter.DisplayMode.RESULT
        var resultClickListener: ResultClickListener? = null
        var pollOptionClickListener: PollOptionClickListener? = null

        // Translated? Create new options from old, using the translated title
        val options = pollViewData.translatedPoll?.let {
            it.options.zip(pollViewData.options) { t, o ->
                o.copy(title = t.title)
            }
        } ?: pollViewData.options

        val canVote = !(pollViewData.expired(now) || pollViewData.voted)
        if (canVote) {
            pollOptionClickListener = {
                binding.statusPollVoteButton.isEnabled = it.any { it.selected }
            }
            displayMode = if (pollViewData.multiple) PollAdapter.DisplayMode.MULTIPLE_CHOICE else PollAdapter.DisplayMode.SINGLE_CHOICE
        } else {
            resultClickListener = { listener(null) }
            binding.statusPollVoteButton.hide()
            binding.statusPollShowResults.hide()
        }

        val adapter = PollAdapter(
            options = options,
            votesCount = pollViewData.votesCount,
            votersCount = pollViewData.votersCount,
            emojis = emojis,
            animateEmojis = statusDisplayOptions.animateEmojis,
            displayMode = displayMode,
            enabled = true,
            resultClickListener = resultClickListener,
            pollOptionClickListener = pollOptionClickListener,
        )

        binding.statusPollOptions.adapter = adapter
        binding.statusPollOptions.layoutManager = LinearLayoutManager(context)
        (binding.statusPollOptions.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        binding.statusPollOptions.show()

        binding.statusPollDescription.text = getPollInfoText(
            pollViewData,
            now,
            statusDisplayOptions,
            numberFormat,
            absoluteTimeFormatter,
        )
        binding.statusPollDescription.show()

        // Poll expired or already voted, can't vote now
        if (!canVote) return

        // Set up voting
        with(binding.statusPollVoteButton) {
            show()
            isEnabled = false
            setOnClickListener {
                val selected = adapter.getSelected()
                if (selected.isNotEmpty()) listener(selected)
            }
        }

        // Set up showing/hiding votes
        if (pollViewData.votesCount > 0) {
            with(binding.statusPollShowResults) {
                show()
                isChecked = adapter.showVotes
                setOnCheckedChangeListener { _, isChecked ->
                    adapter.showVotes = isChecked
                }
            }
        }
    }

    fun hide() {
        binding.statusPollOptions.hide()
        binding.statusPollVoteButton.hide()
        binding.statusPollShowResults.hide()
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

/**
 * Span to show vote percentages inline in a poll.
 *
 * Shows the text at 80% of normal size and bold. Text is right-justified in a space guaranteed
 * to be large enough to accomodate "100%".
 */
class VotePercentSpan : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        paint.textSize *= 0.8f
        return paint.measureText(TEMPLATE).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        text ?: return
        val actualText = text.subSequence(start, end).toString()

        paint.textSize *= 0.8f
        paint.typeface = Typeface.create(paint.typeface, Typeface.BOLD)

        // Compute an x-offset for the text so it will be right aligned
        val actualTextWidth = paint.measureText(actualText)
        val spanWidth = paint.measureText(TEMPLATE)
        val xOffset = spanWidth - actualTextWidth

        // Compute a new y value so the text will be centre-aligned within the span
        val textBounds = Rect()
        paint.getTextBounds(actualText, 0, actualText.length, textBounds)
        val spanHeight = (bottom - top)
        val newY = (spanHeight / 2) + (textBounds.height() / 2)

        canvas.drawText(actualText, start, end, x + xOffset, newY.toFloat(), paint)
    }

    companion object {
        /** Span will be sized to be large enough for this text */
        private const val TEMPLATE = "100%"
    }
}
