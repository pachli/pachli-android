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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.network.model.Emoji
import app.pachli.databinding.ItemPollBinding
import app.pachli.util.BindingHolder
import app.pachli.util.emojify
import app.pachli.util.visible
import app.pachli.viewdata.PollOptionViewData
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import com.google.android.material.color.MaterialColors

class PollAdapter : RecyclerView.Adapter<BindingHolder<ItemPollBinding>>() {

    private var pollOptions: List<PollOptionViewData> = emptyList()
    private var voteCount: Int = 0
    private var votersCount: Int? = null
    private var mode = RESULT
    private var emojis: List<Emoji> = emptyList()
    private var resultClickListener: View.OnClickListener? = null
    private var animateEmojis = false
    private var enabled = true

    /** Listener to call when the user clicks on a poll option */
    private var optionClickListener: View.OnClickListener? = null

    @JvmOverloads
    fun setup(
        options: List<PollOptionViewData>,
        voteCount: Int,
        votersCount: Int?,
        emojis: List<Emoji>,
        mode: Int,
        resultClickListener: View.OnClickListener?,
        animateEmojis: Boolean,
        enabled: Boolean = true,
        optionClickListener: View.OnClickListener? = null,
    ) {
        this.pollOptions = options
        this.voteCount = voteCount
        this.votersCount = votersCount
        this.emojis = emojis
        this.mode = mode
        this.resultClickListener = resultClickListener
        this.animateEmojis = animateEmojis
        this.enabled = enabled
        this.optionClickListener = optionClickListener
        notifyDataSetChanged()
    }

    fun getSelected(): List<Int> {
        return pollOptions.filter { it.selected }
            .map { pollOptions.indexOf(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemPollBinding> {
        val binding = ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun getItemCount() = pollOptions.size

    override fun onBindViewHolder(holder: BindingHolder<ItemPollBinding>, position: Int) {
        val option = pollOptions[position]

        val resultTextView = holder.binding.statusPollOptionResult
        val radioButton = holder.binding.statusPollRadioButton
        val checkBox = holder.binding.statusPollCheckbox

        resultTextView.visible(mode == RESULT)
        radioButton.visible(mode == SINGLE)
        checkBox.visible(mode == MULTIPLE)

        radioButton.isEnabled = enabled
        checkBox.isEnabled = enabled

        when (mode) {
            RESULT -> {
                val percent = calculatePercent(option.votesCount, votersCount, voteCount)
                resultTextView.text = buildDescription(option.title, percent, option.voted, resultTextView.context)
                    .emojify(emojis, resultTextView, animateEmojis)

                val level = percent * 100
                val optionColor: Int
                val textColor: Int
                // Use the "container" colours to ensure the text is visible on the container
                // and on the background, per https://github.com/pachli/pachli-android/issues/85
                if (option.voted) {
                    optionColor = MaterialColors.getColor(resultTextView, com.google.android.material.R.attr.colorPrimaryContainer)
                    textColor = MaterialColors.getColor(resultTextView, com.google.android.material.R.attr.colorOnPrimaryContainer)
                } else {
                    optionColor = MaterialColors.getColor(resultTextView, com.google.android.material.R.attr.colorSecondaryContainer)
                    textColor = MaterialColors.getColor(resultTextView, com.google.android.material.R.attr.colorOnSecondaryContainer)
                }

                resultTextView.background.level = level
                resultTextView.background.setTint(optionColor)
                resultTextView.setTextColor(textColor)
                resultTextView.setOnClickListener(resultClickListener)
            }
            SINGLE -> {
                radioButton.text = option.title.emojify(emojis, radioButton, animateEmojis)
                radioButton.isChecked = option.selected
                radioButton.setOnClickListener {
                    pollOptions.forEachIndexed { index, pollOption ->
                        pollOption.selected = index == holder.bindingAdapterPosition
                        notifyItemChanged(index)
                    }
                    optionClickListener?.onClick(radioButton)
                }
            }
            MULTIPLE -> {
                checkBox.text = option.title.emojify(emojis, checkBox, animateEmojis)
                checkBox.isChecked = option.selected
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    pollOptions[holder.bindingAdapterPosition].selected = isChecked
                    optionClickListener?.onClick(checkBox)
                }
            }
        }
    }

    companion object {
        const val RESULT = 0
        const val SINGLE = 1
        const val MULTIPLE = 2
    }
}
