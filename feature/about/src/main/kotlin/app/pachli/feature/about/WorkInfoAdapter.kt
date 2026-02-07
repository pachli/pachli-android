/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.about

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.ui.extensions.asDdHhMmSs
import app.pachli.core.ui.extensions.instantFormatter
import app.pachli.feature.about.databinding.ItemWorkInfoBinding
import java.time.Duration
import java.time.Instant

@SuppressLint("SetTextI18n")
class WorkInfoAdapter : ListAdapter<WorkInfo, WorkInfoAdapter.ViewHolder>(diffCallback) {
    class ViewHolder(private val binding: ItemWorkInfoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(workInfo: WorkInfo) = with(workInfo) {
            binding.id.text = id.toString()
            binding.tags.text = "Tags: ${tags.joinToString(", ")}"
            binding.state.text = state.toString()

            binding.runAttemptCount.text = binding.root.context.getString(
                R.string.notification_log_previous_attempts,
                runAttemptCount,
            )

            binding.runAttemptCount.show()

            if (state == WorkInfo.State.ENQUEUED) {
                binding.nextScheduleTime.show()
                val now = Instant.now()
                val nextScheduleInstant = Instant.ofEpochMilli(nextScheduleTimeMillis)
                binding.nextScheduleTime.text = binding.root.context.getString(
                    R.string.notification_log_scheduled_in,
                    Duration.between(now, nextScheduleInstant).asDdHhMmSs(),
                    instantFormatter.format(nextScheduleInstant),
                )
            } else {
                binding.nextScheduleTime.hide()
            }

            binding.periodicity.show()
            binding.periodicity.text = periodicityInfo?.let {
                val repeatInterval = Duration.ofMillis(it.repeatIntervalMillis).asDdHhMmSs()
                val flexInterval = Duration.ofMillis(it.flexIntervalMillis).asDdHhMmSs()
                "repeat: $repeatInterval, flex: $flexInterval"
            } ?: "One shot worker"

            if (Build.VERSION.SDK_INT < 31) {
                binding.stopReason.text = "SDK < 31, stop reason not available"
            } else {
                binding.stopReason.text = when (stopReason) {
                    WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> "STOP_REASON_FOREGROUND_SERVICE_TIMEOUT"
                    WorkInfo.STOP_REASON_NOT_STOPPED -> "STOP_REASON_NOT_STOPPED"
                    WorkInfo.STOP_REASON_UNKNOWN -> "STOP_REASON_UNKNOWN"
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "STOP_REASON_CANCELLED_BY_APP"
                    WorkInfo.STOP_REASON_PREEMPT -> "STOP_REASON_PREEMPT"
                    WorkInfo.STOP_REASON_TIMEOUT -> "STOP_REASON_TIMEOUT"
                    WorkInfo.STOP_REASON_DEVICE_STATE -> "STOP_REASON_DEVICE_STATE"
                    WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW"
                    WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "STOP_REASON_CONSTRAINT_CHARGING"
                    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "STOP_REASON_CONSTRAINT_CONNECTIVITY"
                    WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "STOP_REASON_CONSTRAINT_DEVICE_IDLE"
                    WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW"
                    WorkInfo.STOP_REASON_QUOTA -> "STOP_REASON_QUOTA"
                    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "STOP_REASON_BACKGROUND_RESTRICTION"
                    WorkInfo.STOP_REASON_APP_STANDBY -> "STOP_REASON_APP_STANDBY"
                    WorkInfo.STOP_REASON_USER -> "STOP_REASON_USER"
                    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "STOP_REASON_SYSTEM_PROCESSING"
                    WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED"
                    else -> "Unknown stop reason: $stopReason"
                }
                binding.stopReason.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemWorkInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<WorkInfo>() {
            override fun areItemsTheSame(oldItem: WorkInfo, newItem: WorkInfo) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WorkInfo, newItem: WorkInfo) = false
        }
    }
}
