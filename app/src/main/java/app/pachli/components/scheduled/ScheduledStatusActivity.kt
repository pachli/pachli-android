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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.StatusScheduledEvent
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.InReplyTo
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.model.ScheduledStatus
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.ActivityScheduledStatusBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduledStatusActivity :
    BaseActivity(),
    ScheduledStatusActionListener,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: ScheduledStatusViewModel by viewModels()

    private val binding by viewBinding(ActivityScheduledStatusBinding::inflate)

    private val adapter = ScheduledStatusAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        addMenuProvider(this)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            title = getString(R.string.title_scheduled_posts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshStatuses)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.scheduledTootList.setHasFixedSize(true)
        binding.scheduledTootList.layoutManager = LinearLayoutManager(this)
        binding.scheduledTootList.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )
        binding.scheduledTootList.adapter = adapter
        binding.includedToolbar.appbar.setLiftOnScrollTargetView(binding.scheduledTootList)

        lifecycleScope.launch {
            viewModel.data.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error) {
                binding.progressBar.hide()
                binding.errorMessageView.show()

                val errorState = loadState.refresh as LoadState.Error
                binding.errorMessageView.setup(errorState.error) { refreshStatuses() }
            }
            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            if (loadState.refresh is LoadState.NotLoading) {
                binding.progressBar.hide()
                if (adapter.itemCount == 0) {
                    binding.errorMessageView.setup(BackgroundMessage.Empty(R.string.no_scheduled_posts))
                    binding.errorMessageView.show()
                } else {
                    binding.errorMessageView.hide()
                }
            }
        }

        lifecycleScope.launch {
            eventHub.events.collect { event ->
                if (event is StatusScheduledEvent) {
                    adapter.refresh()
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_announcements, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@ScheduledStatusActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.includedToolbar.toolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshStatuses()
                true
            }
            else -> false
        }
    }

    private fun refreshStatuses() {
        adapter.refresh()
    }

    override fun edit(item: ScheduledStatus) {
        val intent = ComposeActivityIntent(
            this,
            intent.pachliAccountId,
            ComposeOptions(
                scheduledTootId = item.id,
                content = item.params.text,
                contentWarning = item.params.spoilerText,
                mediaAttachments = item.mediaAttachments,
                inReplyTo = item.params.inReplyToId?.let { InReplyTo.Id(it) },
                visibility = item.params.visibility,
                scheduledAt = item.scheduledAt,
                sensitive = item.params.sensitive,
                kind = ComposeOptions.ComposeKind.EDIT_SCHEDULED,
                poll = item.params.poll,
                language = item.params.language,
            ),
        )
        startActivity(intent)
    }

    override fun delete(item: ScheduledStatus) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_scheduled_post_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteScheduledStatus(item)
            }
            .show()
    }
}
