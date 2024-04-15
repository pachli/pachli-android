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

package app.pachli.feature.about

import android.app.Activity
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.text.set
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.model.LogEntryEntity
import app.pachli.core.ui.extensions.await
import app.pachli.feature.about.databinding.FragmentNotificationLogBinding
import app.pachli.feature.about.databinding.ItemLogEntryBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment that shows logs from [LogEntryDao], and can download them as a text
 * report with additional information from [app.pachli.core.activity.NotificationConfig].
 */
@AndroidEntryPoint
class NotificationLogFragment :
    Fragment(R.layout.fragment_notification_log),
    RefreshableFragment {
    @Inject
    @ApplicationContext
    lateinit var applicationContext: Context

    @Inject
    lateinit var logEntryDao: LogEntryDao

    private val viewModel: NotificationViewModel by viewModels()

    private val binding by viewBinding(FragmentNotificationLogBinding::bind)

    private val adapter = LogEntryAdapter()

    private lateinit var layoutManager: LinearLayoutManager

    /** The set of log priorities to show */
    private val shownPriorities = MutableStateFlow(defaultPriorities)

    /** Increment to trigger a reload */
    private val reload = MutableStateFlow(0)

    /** True if the log should be sorted in reverse, newest entries first */
    private val sortReverse = MutableStateFlow(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = layoutManager

        binding.filter.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val filters = shownPriorities.value.toMutableSet()
                val result = showFilterDialog(view.context, filters)
                if (result == AlertDialog.BUTTON_POSITIVE) {
                    shownPriorities.value = filters
                }
            }
        }

        binding.sort.setOnCheckedChangeListener { _, isChecked ->
            sortReverse.value = isChecked
        }

        binding.download.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plan"
                val now = Instant.now()

                putExtra(
                    Intent.EXTRA_TITLE,
                    "pachli-notification-logs-${instantFormatter.format(now)}.txt",
                )
            }
            startActivityForResult(intent, CREATE_FILE)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect pullWorkerFlow and usageEventsFlow so `.value` can be read in onActivityResult.
                launch { viewModel.pullWorkerFlow.collect() }
                launch { viewModel.usageEventsFlow.collect() }

                launch {
                    sortReverse.collect {
                        layoutManager.stackFromEnd = it
                        layoutManager.reverseLayout = it
                    }
                }

                launch {
                    combine(shownPriorities, reload) { shownLevels, _ ->
                        logEntryDao.loadAll().filter { it.priority in shownLevels }
                    }.collect {
                        adapter.submitList(it)
                    }
                }
            }
        }
    }

    fun bind() {
        reload.getAndUpdate { it + 1 }
    }

    override fun refreshContent() {
        bind()
    }

    /**
     * Shows a dialog allowing the user to select one or more Log [priorities].
     *
     * @param priorities Initial set of priorities to show. This will be modified as
     *     the user interacts with the dialog, and **is not** restored if they
     *     cancel.
     */
    private suspend fun showFilterDialog(context: Context, priorities: MutableSet<Int>): Int {
        val items = arrayOf("Verbose", "Debug", "Info", "Warn", "Error", "Assert")
        val checkedItems = booleanArrayOf(
            Log.VERBOSE in priorities,
            Log.DEBUG in priorities,
            Log.INFO in priorities,
            Log.WARN in priorities,
            Log.ERROR in priorities,
            Log.ASSERT in priorities,
        )

        return AlertDialog.Builder(context)
            .setTitle(R.string.notitication_log_filter_dialog_title)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                val priority = when (which) {
                    0 -> Log.VERBOSE
                    1 -> Log.DEBUG
                    2 -> Log.INFO
                    3 -> Log.WARN
                    4 -> Log.ERROR
                    5 -> Log.ASSERT
                    else -> throw IllegalStateException("unknown log priority in filter dialog")
                }
                if (isChecked) {
                    priorities.add(priority)
                } else {
                    priorities.remove(priority)
                }
            }
            .create()
            .await(android.R.string.ok, android.R.string.cancel)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != CREATE_FILE) return
        if (resultCode != Activity.RESULT_OK) return

        data?.data?.also { uri ->
            val contentResolver = applicationContext.contentResolver

            try {
                contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { stream ->
                        stream.write(
                            viewModel.uiState.value.asReport(
                                applicationContext,
                                Instant.now(),
                                viewModel.pullWorkerFlow.value,
                                viewModel.usageEventsFlow.value,
                            ).toByteArray(),
                        )
                        stream.write(
                            adapter.currentList.joinToString("\n").toByteArray(),
                        )
                    }
                }
            } catch (e: FileNotFoundException) {
                Timber.e(e, "Download failed")
                Snackbar.make(
                    binding.root,
                    getString(R.string.notification_log_download_failed, e.message),
                    Snackbar.LENGTH_INDEFINITE,
                ).show()
            } catch (e: IOException) {
                Timber.e(e, "Download failed")
                Snackbar.make(
                    binding.root,
                    getString(R.string.notification_log_download_failed, e.message),
                    Snackbar.LENGTH_INDEFINITE,
                ).show()
            }
        }
    }

    companion object {
        /** Request code for startActivityForResult when creating a new file */
        private const val CREATE_FILE = 0

        fun newInstance() = NotificationLogFragment()

        /** Default log priorities to show */
        private val defaultPriorities = setOf(
            Log.VERBOSE,
            Log.DEBUG,
            Log.INFO,
            Log.WARN,
            Log.ERROR,
            Log.ASSERT,
        )
    }

    private fun Boolean.tick() = if (this) '✔' else ' '

    /**
     * @return A plain text report detailing the contents of [UiState],
     * [pullWorkers], and [usageEvents].
     */
    private fun UiState.asReport(
        context: Context,
        now: Instant,
        pullWorkers: List<WorkInfo>,
        usageEvents: List<UsageEvents.Event>,
    ): String {
        return """
        [%c] Android notifications are enabled?
        %s

        %s

        ---
        UnifiedPush

        [%c] UnifiedPush is available?
        [%c] All accounts have 'push' OAuth scope?
        %s

        [%c] All accounts have a UnifiedPush URL
        %s

        [%c] All accounts are subscribed
        %s

        [%c] ntfy is exempt from battery optimisation

        ---

        Pull notifications
        [%c] Pachli is exempt from battery optimisation

        Workers
        %s

        ---

        Last /api/v1/notifications request
        %s

        ---

        Power management restrictions
        %s
        ---
        Log follows:


        """.trimIndent().format(
            this.androidNotificationsEnabled.tick(),
            this.notificationEnabledAccounts,
            this.notificationMethod.label(context),

            this.unifiedPushAvailable.tick(),
            (!this.anyAccountNeedsMigration).tick(),
            this.anyAccountNeedsMigrationAccounts,

            this.allAccountsHaveUnifiedPushUrl.tick(),
            this.allAccountsUnifiedPushUrlAccounts,

            this.allAccountsHaveUnifiedPushSubscription.tick(),
            this.allAccountsUnifiedPushSubscriptionAccounts,

            this.ntfyIsExemptFromBatteryOptimisation.tick(),

            this.pachliIsExemptFromBatteryOptimisation.tick(),
            pullWorkers
                .filter { it.state != CANCELLED }
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { it.asReport(now) } ?: "No workers in non-CANCELLED state!",

            this.lastFetch,

            usageEvents
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { it.asReport(context, now) } ?: "No usage events",
        )
    }

    private fun WorkInfo.asReport(now: Instant): String {
        return "%s #%d %s %s\n".format(
            state,
            runAttemptCount,
            if (state == ENQUEUED) {
                val then = Instant.ofEpochMilli(nextScheduleTimeMillis)
                "Scheduled in %s @ %s".format(
                    Duration.between(now, then).asDdHhMmSs(),
                    instantFormatter.format(then),
                )
            } else {
                ""
            },
            if (runAttemptCount > 0 && state == ENQUEUED) {
                stopReason
            } else {
                ""
            },
        )
    }

    private fun UsageEvents.Event.asReport(context: Context, now: Instant): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "No events, Build SDK ${Build.VERSION.SDK_INT} < R (${Build.VERSION_CODES.R}"
        }
        val then = Instant.ofEpochMilli(timeStamp)
        return "%s %s".format(
            context.getString(UsageEventAdapter.ViewHolder.bucketDesc[appStandbyBucket] ?: R.string.notification_details_standby_bucket_unknown),
            context.getString(
                R.string.notification_details_ago,
                Duration.between(then, now).asDdHhMmSs(),
                instantFormatter.format(then),
            ),
        )
    }
}

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
                SpannableString("%s%s".format(logEntry.message, logEntry.t?.let { t -> " $t" } ?: "")).apply {
                    set(0, this.length, messageSpan)
                    set(0, this.length, prioritySpan[logEntry.priority] ?: ForegroundColorSpan(Color.GRAY))
                },
            )

            binding.text.text = text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

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
