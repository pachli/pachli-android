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

package app.pachli.components.scheduled

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.asDraft
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.StatusScheduledEvent
import app.pachli.core.model.ScheduledStatus
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ReferencingStatus
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentScheduledStatusBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduledStatusFragment :
    Fragment(R.layout.fragment_scheduled_status),
    ScheduledStatusActionListener,
    MenuProvider,
    RefreshableFragment,
    ReselectableFragment {
    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: ScheduledStatusViewModel by viewModels()

    private val binding by viewBinding(FragmentScheduledStatusBinding::bind)

    private val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

    private val scheduledStatusAdapter: ScheduledStatusAdapter = ScheduledStatusAdapter(this)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scheduledTootList.applyDefaultWindowInsets()
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        with(binding.swipeRefreshLayout) {
            setOnRefreshListener(this@ScheduledStatusFragment::refreshContent)
            setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        }

        with(binding.scheduledTootList) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL),
            )
            adapter = scheduledStatusAdapter
        }

        scheduledStatusAdapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error) {
                binding.progressIndicator.hide()
                binding.errorMessageView.show()

                val errorState = loadState.refresh as LoadState.Error
                binding.errorMessageView.setup(errorState.error) { refreshContent() }
            }
            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            if (loadState.refresh is LoadState.NotLoading) {
                binding.progressIndicator.hide()
                if (scheduledStatusAdapter.itemCount == 0) {
                    binding.errorMessageView.setup(BackgroundMessage.Empty(R.string.no_scheduled_posts))
                    binding.errorMessageView.show()
                } else {
                    binding.errorMessageView.hide()
                }
            }
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            viewModel.data.collectLatest { pagingData ->
                scheduledStatusAdapter.submitData(pagingData)
            }
        }

        lifecycleScope.launch {
            eventHub.events.collect { event ->
                if (event is StatusScheduledEvent) {
                    scheduledStatusAdapter.refresh()
                }
            }
        }
    }

    override fun edit(item: ScheduledStatus) {
        val intent = ComposeActivityIntent(
            requireContext(),
            pachliAccountId,
            ComposeOptions(
                draft = item.asDraft(),
                mediaAttachments = item.mediaAttachments,
                referencingStatus = item.params.inReplyToId?.let {
                    ReferencingStatus.ReplyId(it)
                } ?: item.params.quotedStatusId?.let {
                    ReferencingStatus.QuoteId(it)
                },
                kind = ComposeOptions.ComposeKind.EDIT_SCHEDULED,
            ),
        )
        startActivity(intent)
    }

    override fun delete(item: ScheduledStatus) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.delete_scheduled_post_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteScheduledStatus(item)
            }
            .show()
    }

    override fun refreshContent() {
        scheduledStatusAdapter.refresh()
    }

    override fun onReselect() {
        binding.scheduledTootList.scrollToPosition(0)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_scheduled_status, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshContent()
                true
            }

            else -> false
        }
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): ScheduledStatusFragment {
            val fragment = ScheduledStatusFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
