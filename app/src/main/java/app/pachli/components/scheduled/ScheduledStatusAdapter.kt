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

package app.pachli.components.scheduled

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Checkable
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.components.scheduled.ScheduledStatusViewData.State
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.ScheduledStatus
import app.pachli.databinding.ItemScheduledStatusBinding
import com.bumptech.glide.RequestManager
import java.text.DateFormat

internal interface ScheduledStatusActionListener {
    fun edit(item: ScheduledStatus)
    fun delete(item: ScheduledStatus)

    /** @return True if [scheduledStatus] is checked. */
    fun isScheduledStatusChecked(scheduledStatus: ScheduledStatus): Boolean

    /** Sets the checked state of [scheduledStatus] to [isChecked]. */
    fun setScheduledStatusChecked(scheduledStatus: ScheduledStatus, isChecked: Boolean)
}

internal class ScheduledStatusAdapter(
    private val glide: RequestManager,
    private val listener: ScheduledStatusActionListener,
) : PagingDataAdapter<ScheduledStatusViewData, ScheduledStatusViewHolder>(
    object : DiffUtil.ItemCallback<ScheduledStatusViewData>() {
        override fun areItemsTheSame(oldItem: ScheduledStatusViewData, newItem: ScheduledStatusViewData): Boolean {
            return oldItem.scheduledStatus.id == newItem.scheduledStatus.id
        }

        override fun areContentsTheSame(oldItem: ScheduledStatusViewData, newItem: ScheduledStatusViewData): Boolean {
            return oldItem == newItem
        }
    },
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewTYpe: Int): ScheduledStatusViewHolder {
        val binding = ItemScheduledStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduledStatusViewHolder(glide, binding, listener)
    }

    override fun onBindViewHolder(holder: ScheduledStatusViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }
}

internal class ScheduledStatusViewHolder(
    glide: RequestManager,
    private val binding: ItemScheduledStatusBinding,
    private val listener: ScheduledStatusActionListener,
) : RecyclerView.ViewHolder(binding.root), Checkable {
    private var scheduledStatus: ScheduledStatus? = null

    private val mediaAdapter = ScheduledMediaAdapter(glide)

    init {
        with(binding) {
            mediaPreview.layoutManager = LinearLayoutManager(root.context, RecyclerView.HORIZONTAL, false)
            mediaPreview.adapter = mediaAdapter

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                scheduledStatus?.let { listener.setScheduledStatusChecked(it, isChecked) }
            }

            root.setOnClickListener {
                scheduledStatus?.let { listener.edit(it) }
            }
        }
    }

    fun bind(scheduledStatusViewData: ScheduledStatusViewData) = with(binding) {
        val scheduledStatus = scheduledStatusViewData.scheduledStatus
        this@ScheduledStatusViewHolder.scheduledStatus = scheduledStatus

        isChecked = scheduledStatusViewData.state == State.CHECKED
        binding.root.isActivated = isChecked

        checkBox.isVisible = scheduledStatusViewData.state != State.EDITING
        editButton.isVisible = scheduledStatusViewData.state == State.EDITING

        val statusParams = scheduledStatus.params
        contentWarning.visible(statusParams.spoilerText?.isNotEmpty() == true)
        contentWarning.text = statusParams.spoilerText
        content.text = statusParams.text

        mediaPreview.visible(scheduledStatus.mediaAttachments.isNotEmpty())
        mediaAdapter.submitList(scheduledStatus.mediaAttachments)

        statusParams.poll?.apply {
            draftPoll.show()
            draftPoll.setPoll(this)
        } ?: draftPoll.hide()

        timestamp.text = dateFormat.format(scheduledStatus.scheduledAt)
    }

    override fun isChecked() = binding.checkBox.isChecked

    override fun setChecked(checked: Boolean) {
        binding.checkBox.isChecked = checked
    }

    override fun toggle() {
        isChecked = !binding.checkBox.isChecked
    }

    companion object {
        private val dateFormat =
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
    }
}
