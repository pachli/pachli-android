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

import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.set
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.database.model.LogEntryEntity
import app.pachli.core.ui.extensions.asDdHhMmSs
import app.pachli.feature.about.databinding.ItemLogEntryBinding
import java.time.Duration
import java.time.Instant

class LogEntryAdapter : ListAdapter<LogEntryEntity, LogEntryAdapter.ViewHolder>(diffCallback) {
    class ViewHolder(private val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(logEntry: LogEntryEntity) {
            val now = Instant.now()

            val tag = SpannableString(logEntry.tag).apply { set(0, this.length, tagSpan) }
            val duration = Duration.between(logEntry.instant, now).asDdHhMmSs()
            val instant = logEntry.instant.toString()

            val timestamp = SpannableStringBuilder()
                .append(tag)
                .append(": ")
                .append(
                    binding.root.context.getString(
                        R.string.notification_details_ago,
                        duration,
                        instant,
                    ),
                )
                .apply {
                    set(0, this.length, tagSpan)
                    set(0, this.length, RelativeSizeSpan(0.7f))
                }

            binding.timestamp.text = timestamp

            val text = SpannableStringBuilder()
            text.append(
                SpannableString(
                    "%s%s".format(
                        logEntry.message,
                        logEntry.t?.let { t -> " $t" } ?: "",
                    ),
                ).apply {
                    set(0, this.length, messageSpan)
                    set(0, this.length, prioritySpan[logEntry.priority] ?: ForegroundColorSpan(Color.GRAY))
                },
            )

            binding.text.text = text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        val tagSpan = ForegroundColorSpan(Color.GRAY)
        val messageSpan = ForegroundColorSpan(Color.BLACK)

        private val prioritySpan = mapOf(
            Log.VERBOSE to ForegroundColorSpan(Color.GRAY),
            Log.DEBUG to ForegroundColorSpan(Color.GRAY),
            Log.INFO to ForegroundColorSpan(Color.BLACK),
            Log.WARN to ForegroundColorSpan(Color.YELLOW),
            Log.ERROR to ForegroundColorSpan(Color.RED),
            Log.ASSERT to ForegroundColorSpan(Color.RED),
        )

        val diffCallback = object : DiffUtil.ItemCallback<LogEntryEntity>() {
            override fun areItemsTheSame(oldItem: LogEntryEntity, newItem: LogEntryEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: LogEntryEntity, newItem: LogEntryEntity) = false
        }
    }
}
