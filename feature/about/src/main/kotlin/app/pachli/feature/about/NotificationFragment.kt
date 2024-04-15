/*
 * Copyright 2024 Pachli Association
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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.core.activity.CustomFragmentStateAdapter
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.feature.about.databinding.FragmentNotificationBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fragment that hosts [NotificationDetailsFragment] and [NotificationLogFragment]
 * and helper functions they use.
 */
@AndroidEntryPoint
class NotificationFragment :
    Fragment(R.layout.fragment_notification),
    MenuProvider,
    OnRefreshListener {
    private val binding by viewBinding(FragmentNotificationBinding::bind)

    lateinit var adapter: NotificationFragmentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        adapter = NotificationFragmentAdapter(this)
        binding.pager.adapter = adapter
        binding.pager.reduceSwipeSensitivity()

        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = adapter.title(position)
        }.attach()

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    override fun onRefresh() {
        adapter.refreshContent()
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_notification, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                true
            }
            else -> false
        }
    }

    companion object {
        fun newInstance() = NotificationFragment()
    }
}

class NotificationFragmentAdapter(val fragment: Fragment) : CustomFragmentStateAdapter(fragment) {
    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> NotificationDetailsFragment.newInstance()
            1 -> NotificationLogFragment.newInstance()
            else -> throw IllegalStateException()
        }
    }

    fun title(position: Int): CharSequence {
        return when (position) {
            0 -> fragment.getString(R.string.notification_details_title)
            1 -> fragment.getString(R.string.notification_log_title)
            else -> throw IllegalStateException()
        }
    }

    fun refreshContent() {
        for (i in 0..itemCount) {
            (getFragment(i) as? RefreshableFragment)?.refreshContent()
        }
    }
}

/**
 * @return The [Duration] formatted as `NNdNNhNNmNNs` (e.g., `01d23h15m23s`) with any leading
 *     zero components removed. So 34 minutes and 15 seconds is `34m15s` not `00d00h34m15s`.
 */
fun Duration.asDdHhMmSs(): String {
    val days = this.toDaysPart()
    val hours = this.toHoursPart()
    val minutes = this.toMinutesPart()
    val seconds = this.toSecondsPart()

    return when {
        days > 0 -> "%02dd%02dh%02dm%02ds".format(days, hours, minutes, seconds)
        hours > 0 -> "%02dh%02dm%02ds".format(hours, minutes, seconds)
        minutes > 0 -> "%02dm%02ds".format(minutes, seconds)
        else -> "%02ds".format(seconds)
    }
}

val instantFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")
    .withZone(ZoneId.systemDefault())
