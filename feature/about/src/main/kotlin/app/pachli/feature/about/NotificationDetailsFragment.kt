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

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
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
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.NotificationConfig
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.feature.about.databinding.FragmentNotificationDetailsBinding
import app.pachli.feature.about.databinding.ItemUsageEventBinding
import app.pachli.feature.about.databinding.ItemWorkInfoBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Fragment that shows details from [NotificationConfig].
 */
@AndroidEntryPoint
class NotificationDetailsFragment :
    Fragment(R.layout.fragment_notification_details),
    RefreshableFragment {
    @Inject
    lateinit var accountManager: AccountManager

    private val viewModel: NotificationViewModel by viewModels()

    private val binding by viewBinding(FragmentNotificationDetailsBinding::bind)

    private val workInfoAdapter = WorkInfoAdapter()

    // Usage events need API 30, so this is conditionally created in onViewCreated.
    private var usageEventAdapter: UsageEventAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.workInfoRecyclerView.adapter = workInfoAdapter
        binding.workInfoRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            usageEventAdapter = UsageEventAdapter()
            binding.usageEventSection.show()
            binding.usageEventRecyclerView.adapter = usageEventAdapter
            binding.usageEventRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        } else {
            binding.usageEventSection.hide()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { bind(it) } }

                launch {
                    viewModel.pullWorkerFlow.collect {
                        workInfoAdapter.submitList(it.filter { it.state != WorkInfo.State.CANCELLED })
                    }
                }

                usageEventAdapter?.also { adapter ->
                    launch {
                        viewModel.usageEventsFlow.collect { adapter.submitList(it) }
                    }
                }
            }
        }
    }

    fun bind(uiState: UiState) {
        binding.androidNotificationsEnabled.isChecked = uiState.androidNotificationsEnabled
        binding.notificationsEnabledHelp.visible(!uiState.androidNotificationsEnabled)
        binding.notificationsEnabledAccounts.text = uiState.notificationEnabledAccounts

        if (uiState.androidNotificationsEnabled) {
            val method = uiState.notificationMethod
            binding.notificationMethod.text = method.label(requireContext())
            binding.notificationMethod.show()
        } else {
            binding.notificationMethod.hide()
            binding.pushSection.hide()
            binding.pullSection.hide()
            return
        }

        binding.pushSection.show()

        binding.unifiedPushAvailable.isChecked = uiState.unifiedPushAvailable
        binding.unifiedPushAvailableHelp.visible(!uiState.unifiedPushAvailable)

        binding.anyAccountNeedsMigration.isChecked = !uiState.anyAccountNeedsMigration
        binding.anyAccountNeedsMigrationHelp.visible(uiState.anyAccountNeedsMigration)
        binding.anyAccountNeedsMigrationAccounts.text = uiState.anyAccountNeedsMigrationAccounts

        binding.accountsUnifiedPushUrl.isChecked = uiState.allAccountsHaveUnifiedPushUrl
        binding.accountsUnifiedPushUrlHelp.visible(!uiState.allAccountsHaveUnifiedPushUrl)
        binding.accountsUnifiedPushUrlAccounts.text = uiState.allAccountsUnifiedPushUrlAccounts

        binding.accountsUnifiedPushSubscription.isChecked = uiState.allAccountsHaveUnifiedPushSubscription
        binding.accountsUnifiedPushSubscriptionHelp.visible(!uiState.allAccountsHaveUnifiedPushSubscription)
        binding.accountsUnifiedPushSubscriptionAccounts.text = uiState.allAccountsUnifiedPushSubscriptionAccounts

        binding.ntfyExempt.isChecked = uiState.ntfyIsExemptFromBatteryOptimisation
        binding.ntfyExemptHelp.visible(!uiState.ntfyIsExemptFromBatteryOptimisation)

        binding.pullSection.visible(NotificationConfig.notificationMethod == NotificationConfig.Method.Pull)

        binding.pachliExempt.isChecked = uiState.pachliIsExemptFromBatteryOptimisation
        binding.pachliExemptHelp.visible(!uiState.pachliIsExemptFromBatteryOptimisation)

        binding.lastFetch.text = uiState.lastFetch
    }

    override fun refreshContent() {
        viewModel.refresh()
    }

    companion object {
        fun newInstance() = NotificationDetailsFragment()
    }
}

fun NotificationConfig.Method.label(context: Context) = when (this) {
    is NotificationConfig.Method.Push -> context.getString(R.string.notification_log_method_push)
    is NotificationConfig.Method.Pull -> context.getString(R.string.notification_log_method_pull)
    is NotificationConfig.Method.Unknown -> context.getString(R.string.notification_log_method_unknown)
    is NotificationConfig.Method.PushError -> context.getString(
        R.string.notification_log_method_pusherror,
        this.t,
    )
}

class WorkInfoAdapter : ListAdapter<WorkInfo, WorkInfoAdapter.ViewHolder>(diffCallback) {
    class ViewHolder(private val binding: ItemWorkInfoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(workInfo: WorkInfo) = with(workInfo) {
            binding.id.text = id.toString()
            binding.state.text = state.toString()

            binding.runAttemptCount.text = binding.root.context.getString(
                R.string.notification_log_previous_attempts,
                runAttemptCount,
            )

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

            binding.runAttemptCount.show()

            if (runAttemptCount > 0 && state == WorkInfo.State.ENQUEUED) {
                binding.stopReason.show()
                binding.stopReason.text = stopReason.toString()
            } else {
                binding.stopReason.hide()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemWorkInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<WorkInfo>() {
            override fun areItemsTheSame(oldItem: WorkInfo, newItem: WorkInfo) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WorkInfo, newItem: WorkInfo) = false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
class UsageEventAdapter : ListAdapter<UsageEvents.Event, UsageEventAdapter.ViewHolder>(diffCallback) {
    class ViewHolder(private val binding: ItemUsageEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(usageEvent: UsageEvents.Event) = with(usageEvent) {
            val now = Instant.now()
            val then = Instant.ofEpochMilli(timeStamp)
            binding.text.text = binding.root.context.getString(
                bucketDesc[appStandbyBucket] ?: R.string.notification_details_standby_bucket_unknown,
            )
            binding.timestamp.text = binding.root.context.getString(
                R.string.notification_details_ago,
                Duration.between(then, now).asDdHhMmSs(),
                instantFormatter.format(then),
            )
        }
        companion object {
            /** Descriptions for each `STANDBY_BUCKET_` type */
            // Descriptions from https://developer.android.com/topic/performance/power/power-details
            val bucketDesc = mapOf(
                // 5 = STANDBY_BUCKET_EXEMPTED, marked as @SystemApi
                5 to R.string.notification_details_standby_bucket_exempted,
                STANDBY_BUCKET_ACTIVE to R.string.notification_details_standby_bucket_active,
                STANDBY_BUCKET_WORKING_SET to R.string.notification_details_standby_bucket_working_set,
                STANDBY_BUCKET_FREQUENT to R.string.notification_details_standby_bucket_frequent,
                STANDBY_BUCKET_RARE to R.string.notification_details_standby_bucket_rare,
                STANDBY_BUCKET_RESTRICTED to R.string.notification_details_standby_bucket_restricted,
                // 50 = STANDBY_BUCKET_NEVER, marked as @SystemApi
                50 to R.string.notification_details_standby_bucket_never,
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemUsageEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<UsageEvents.Event>() {
            override fun areItemsTheSame(oldItem: UsageEvents.Event, newItem: UsageEvents.Event) = oldItem.timeStamp == newItem.timeStamp
            override fun areContentsTheSame(oldItem: UsageEvents.Event, newItem: UsageEvents.Event) = false
        }
    }
}
