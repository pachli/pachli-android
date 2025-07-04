/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.compose.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import app.pachli.R
import app.pachli.adapter.PreviewPollOptionsAdapter
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.NewPoll
import app.pachli.databinding.ViewPollPreviewBinding

class PollPreviewView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) :
    LinearLayout(context, attrs, defStyleAttr) {

    private val adapter = PreviewPollOptionsAdapter()

    private val binding = ViewPollPreviewBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL

        setBackgroundResource(R.drawable.card_frame)

        val padding = resources.getDimensionPixelSize(DR.dimen.poll_preview_padding)

        setPadding(padding, padding, padding, padding)

        binding.pollPreviewOptions.adapter = adapter
    }

    fun setPoll(poll: NewPoll) {
        adapter.update(poll.options, poll.multiple)

        val pollDurationId = resources.getIntArray(R.array.poll_duration_values).indexOfLast {
            it <= poll.expiresIn
        }
        binding.pollDurationPreview.text = resources.getStringArray(R.array.poll_duration_names)[pollDurationId]
    }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        adapter.setOnClickListener(l)
    }
}
