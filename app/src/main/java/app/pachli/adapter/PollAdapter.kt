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

package app.pachli.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.Emoji
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.emojify
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.ItemPollBinding
import app.pachli.viewdata.PollOptionViewData
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import com.bumptech.glide.RequestManager
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import kotlin.properties.Delegates

/** Listener for user clicks on poll items */
typealias PollOptionClickListener = (List<PollOptionViewData>) -> Unit

/** Listener for user clicks on results */
typealias ResultClickListener = () -> Unit

// This can't take [app.pachli.viewdata.PollViewData] as a parameter as it also needs to show
// data from polls that have been edited, and the "shape" of that data is quite different (no
// information about vote counts, poll IDs, etc).
class PollAdapter(
    private val glide: RequestManager,
    val options: List<PollOptionViewData>,
    private val votesCount: Int,
    private val votersCount: Int?,
    val emojis: List<Emoji>,
    val animateEmojis: Boolean,
    val displayMode: DisplayMode,
    /** True if the user can vote in this poll, false otherwise (e.g., it's from an edit) */
    val enabled: Boolean = true,
    /** Listener to call when the user clicks on the poll results */
    private val resultClickListener: ResultClickListener? = null,
    /** Listener to call when the user clicks on a poll option */
    private val pollOptionClickListener: PollOptionClickListener? = null,
) : RecyclerView.Adapter<BindingHolder<ItemPollBinding>>() {

    /** How to display a poll */
    enum class DisplayMode {
        /** Show the results, no voting */
        RESULT,

        /** Single choice (display as radio buttons) */
        SINGLE_CHOICE,

        /** Multiple choice (display as check boxes) */
        MULTIPLE_CHOICE,

        /** Snapshot of a poll from a status' edit history */
        EDIT_HISTORY,
    }

    /**
     * True if the poll's current vote details should be shown with the controls to
     * vote, false otherwise. Ignored if the display maode is [DisplayMode.RESULT]
     */
    var showVotes: Boolean by Delegates.observable(false) { _, _, _ ->
        notifyItemRangeChanged(0, itemCount)
    }

    /** @return the indices of the selected options */
    fun getSelected() = options.withIndex().filter { it.value.selected }.map { it.index }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemPollBinding> {
        val binding = ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun getItemCount() = options.size

    override fun onBindViewHolder(holder: BindingHolder<ItemPollBinding>, position: Int) {
        val option = options[position]

        val resultTextView = holder.binding.statusPollOptionResult
        val radioButton = holder.binding.statusPollRadioButton
        val checkBox = holder.binding.statusPollCheckbox

        resultTextView.visible(displayMode == DisplayMode.RESULT || displayMode == DisplayMode.EDIT_HISTORY)
        radioButton.visible(displayMode == DisplayMode.SINGLE_CHOICE)
        checkBox.visible(displayMode == DisplayMode.MULTIPLE_CHOICE)

        // Poll edit history doesn't indicate if it was single or multiple choice at this point, so use
        // a general "vote" icon instead of a radio button or checkbox.
        if (displayMode == DisplayMode.EDIT_HISTORY) {
            val icon = makeIcon(resultTextView.context, GoogleMaterial.Icon.gmd_how_to_vote, resultTextView.textSize.toInt())
            resultTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        }

        // Enable/disable the option widgets as appropriate. Disabling them will also change
        // the text colour, which is undesirable (this happens when showing status edits) so
        // reset the text colour as necessary.
        val defaultTextColor = radioButton.currentTextColor
        radioButton.isEnabled = enabled
        checkBox.isEnabled = enabled
        if (!enabled) {
            radioButton.setTextColor(defaultTextColor)
            checkBox.setTextColor(defaultTextColor)
        }

        val percent = calculatePercent(option.votesCount, votersCount, votesCount)
        val level: Int
        val tintColor: Int
        val textColor: Int
        val itemText: CharSequence

        when {
            displayMode == DisplayMode.RESULT && option.voted -> {
                level = percent * 100
                tintColor = MaterialColors.getColor(resultTextView, R.attr.colorPrimaryContainer)
                textColor = MaterialColors.getColor(resultTextView, R.attr.colorOnPrimaryContainer)
                itemText = buildDescription(option.title, percent, option.voted, resultTextView.context)
                    .emojify(glide, emojis, resultTextView, animateEmojis)
            }
            displayMode == DisplayMode.RESULT || showVotes -> {
                level = percent * 100
                tintColor = MaterialColors.getColor(resultTextView, R.attr.colorSecondaryContainer)
                textColor = MaterialColors.getColor(resultTextView, R.attr.colorOnSecondaryContainer)
                itemText = buildDescription(option.title, percent, option.voted, resultTextView.context)
                    .emojify(glide, emojis, resultTextView, animateEmojis)
            }
            else -> {
                level = 0
                tintColor = MaterialColors.getColor(resultTextView, R.attr.colorSecondaryContainer)
                textColor = MaterialColors.getColor(resultTextView, android.R.attr.textColorPrimary)
                itemText = option.title.emojify(glide, emojis, radioButton, animateEmojis)
            }
        }

        when (displayMode) {
            DisplayMode.RESULT, DisplayMode.EDIT_HISTORY -> with(resultTextView) {
                text = itemText
                background.level = level
                background.setTint(tintColor)
                setTextColor(textColor)
                setOnClickListener { resultClickListener?.invoke() }
            }
            DisplayMode.SINGLE_CHOICE -> with(radioButton) {
                isChecked = option.selected
                text = itemText
                background.level = level
                background.setTint(tintColor)
                setTextColor(textColor)
                setOnClickListener {
                    options.forEachIndexed { index, pollOption ->
                        pollOption.selected = index == holder.bindingAdapterPosition
                        notifyItemChanged(index)
                    }
                    pollOptionClickListener?.invoke(options)
                }
            }
            DisplayMode.MULTIPLE_CHOICE -> with(checkBox) {
                isChecked = option.selected
                text = itemText
                background.level = level
                background.setTint(tintColor)
                setTextColor(textColor)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    options[holder.bindingAdapterPosition].selected = isChecked
                    pollOptionClickListener?.invoke(options)
                }
            }
        }
    }
}
