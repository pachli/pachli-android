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

import android.app.ActivityManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import app.pachli.R
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.asDraft
import app.pachli.core.model.ScheduledStatus
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ComposeKind
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ReferencingStatus
import app.pachli.core.ui.AlertSuspendDialogFragment
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentScheduledStatusBinding
import com.bumptech.glide.Glide
import com.gaelmarhic.quadrant.QuadrantConstants
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduledStatusFragment :
    Fragment(R.layout.fragment_scheduled_status),
    ScheduledStatusActionListener,
    MenuProvider,
    RefreshableFragment,
    ReselectableFragment {
    private val viewModel: ScheduledStatusViewModel by viewModels()

    private val binding by viewBinding(FragmentScheduledStatusBinding::bind)

    private val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

    private lateinit var scheduledStatusAdapter: ScheduledStatusAdapter

    /**
     * Action mode when the user is selecting one or more scheduled statuses.
     *
     * Null if there is no active selection.
     */
    private var selectScheduledStatusActionMode: ActionMode? = null

    /**
     * Handles the user's scheduled status selections.
     *
     * - Displays a count of selected scheduled statuses in the title.
     * - Shows a "Delete selected" option in the menu.
     * - Responds to the "Delete selected" option, asks for confirmation to delete.
     */
    private val deleteActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            requireActivity().menuInflater.inflate(R.menu.fragment_scheduled_status_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.action_delete_scheduled_statuses)?.isVisible = viewModel.countChecked() > 0
            return true
        }

        override fun onActionItemClicked(actionMode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_delete_scheduled_statuses -> {
                    lifecycleScope.launch {
                        val button = AlertSuspendDialogFragment.newInstance(
                            title = getString(R.string.title_delete_scheduled_statuses),
                            message = getString(R.string.delete_scheduled_statuses_msg),
                            positiveText = getString(android.R.string.ok),
                            negativeText = getString(android.R.string.cancel),
                        ).await(childFragmentManager)

                        if (button == android.app.AlertDialog.BUTTON_POSITIVE) {
                            viewModel.deleteCheckedScheduledStatuses()
                            actionMode.finish()
                        }
                    }
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(actionMode: ActionMode) {
            selectScheduledStatusActionMode = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scheduledTootList.applyDefaultWindowInsets()
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        scheduledStatusAdapter = ScheduledStatusAdapter(Glide.with(this), this)

        with(binding.swipeRefreshLayout) {
            setOnRefreshListener(this@ScheduledStatusFragment::refreshContent)
            setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        }

        with(binding.scheduledTootList) {
            setHasFixedSize(true)
            adapter = scheduledStatusAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL),
            )
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
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
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refresh()
                launch {
                    viewModel.viewData.collectLatest { pagingData ->
                        scheduledStatusAdapter.submitData(pagingData)
                    }
                }
            }
        }
    }

    override fun setScheduledStatusChecked(scheduledStatus: ScheduledStatus, isChecked: Boolean) {
        val countChecked = viewModel.checkScheduledStatus(scheduledStatus, isChecked)
        if (selectScheduledStatusActionMode == null && countChecked > 0) {
            selectScheduledStatusActionMode = (requireActivity() as? AppCompatActivity)?.startSupportActionMode(deleteActionModeCallback)
        }
        if (selectScheduledStatusActionMode != null && countChecked == 0) {
            selectScheduledStatusActionMode?.finish()
            selectScheduledStatusActionMode = null
        }
        selectScheduledStatusActionMode?.title = resources.getQuantityString(R.plurals.selected_scheduled_statuses, countChecked, countChecked)
    }

    override fun isScheduledStatusChecked(scheduledStatus: ScheduledStatus) = viewModel.isScheduledStatusChecked(scheduledStatus)

    override fun edit(item: ScheduledStatus) {
        // Don't open scheduled statuses while selecting them to delete. Instead, tapping on
        // the scheduled status also selects it.
        selectScheduledStatusActionMode?.let {
            viewModel.toggleScheduledStatusChecked(item)
            return
        }

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
                kind = ComposeKind.EDIT_SCHEDULED,
            ),
        )
        resumeOrStartComposeActivity(intent)
        viewModel.refresh()
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

    /**
     * Show a [ComposeActivity][app.pachli.components.compose.ComposeActivity] for [intent].
     *
     * If an existing activity exists for the scheduled status in [intent] it is moved to
     * the front, as a scheduled status can only be edited by one activity at a time.
     *
     * If no existing activity exists then a new activity is started.
     *
     * @param intent The intent containing the [app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions] and draft information.
     */
    private fun resumeOrStartComposeActivity(intent: ComposeActivityIntent) {
        val scheduledStatusId = ComposeActivityIntent.getComposeOptions(intent).draft.statusId

        ContextCompat.getSystemService(requireContext(), ActivityManager::class.java)?.appTasks?.forEach {
            // No point in looking at anything except ComposeActivity
            if (it.taskInfo.baseActivity?.className != QuadrantConstants.COMPOSE_ACTIVITY) return@forEach

            val launchedComposeOptions = ComposeActivityIntent.getComposeOptionsOrNull(it.taskInfo.baseIntent) ?: return@forEach
            if (launchedComposeOptions.kind != ComposeKind.EDIT_SCHEDULED) return@forEach
            if (launchedComposeOptions.draft.statusId == scheduledStatusId) {
                it.moveToFront()
                return
            }
        }
        startActivity(intent)
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
