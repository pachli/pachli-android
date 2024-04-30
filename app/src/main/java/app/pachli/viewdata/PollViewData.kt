/* Copyright 2019 Conny Duck
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

package app.pachli.viewdata

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import app.pachli.R
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.PollOption
import app.pachli.core.network.model.PollOptionEdit
import app.pachli.core.network.model.TranslatedPoll
import app.pachli.view.VotePercentSpan
import java.util.Date
import kotlin.math.roundToInt

data class PollViewData(
    val id: String,
    val expiresAt: Date?,
    val expired: Boolean,
    val multiple: Boolean,
    val votesCount: Int,
    val votersCount: Int?,
    val options: List<PollOptionViewData>,
    var voted: Boolean,
    val translatedPoll: TranslatedPoll?,
) {
    /**
     * @param timeInMs A timestamp in milliseconds-since-the-epoch
     * @return true if this poll is either marked as expired, or [timeInMs] is after this poll's
     *     expiry time.
     */
    fun expired(timeInMs: Long) = expired || ((expiresAt != null) && (timeInMs > expiresAt.time))

    companion object {
        fun from(poll: Poll) = PollViewData(
            id = poll.id,
            expiresAt = poll.expiresAt,
            expired = poll.expired,
            multiple = poll.multiple,
            votesCount = poll.votesCount,
            votersCount = poll.votersCount,
            options = poll.options.mapIndexed { index, option -> PollOptionViewData.from(option, poll.ownVotes?.contains(index) == true) },
            voted = poll.voted,
            translatedPoll = null,
        )
    }
}

data class PollOptionViewData(
    val title: String,
    var votesCount: Int,
    var selected: Boolean,
    var voted: Boolean,
) {
    companion object {
        fun from(pollOption: PollOption, voted: Boolean) = PollOptionViewData(
            title = pollOption.title,
            votesCount = pollOption.votesCount,
            selected = false,
            voted = voted,
        )

        fun from(pollOptionEdit: PollOptionEdit) = PollOptionViewData(
            title = pollOptionEdit.title,
            votesCount = 0,
            selected = false,
            voted = false,
        )
    }
}

fun calculatePercent(fraction: Int, totalVoters: Int?, totalVotes: Int): Int {
    return if (fraction == 0) {
        0
    } else {
        val total = totalVoters ?: totalVotes
        (fraction / total.toDouble() * 100).roundToInt()
    }
}

fun buildDescription(title: String, percent: Int, voted: Boolean, context: Context): Spanned {
    val percentStr = context.getString(R.string.poll_percent_format, percent)
    val builder = SpannableStringBuilder(percentStr)
    builder.setSpan(VotePercentSpan(), 0, percentStr.length, SPAN_EXCLUSIVE_EXCLUSIVE)
    if (voted) {
        builder.append(" ✓ ")
    }
    return builder.append(title)
}
