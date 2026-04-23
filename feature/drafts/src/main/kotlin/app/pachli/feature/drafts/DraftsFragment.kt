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

package app.pachli.feature.drafts

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.model.Draft
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.sendstatus.SendStatusUseCase
import app.pachli.core.ui.AlertSuspendDialogFragment
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.drafts.databinding.FragmentDraftsBinding
import com.bumptech.glide.Glide
import com.gaelmarhic.quadrant.QuadrantConstants
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class DraftsFragment :
    Fragment(R.layout.fragment_drafts),
    DraftActionListener,
    ReselectableFragment {
    private val viewModel: DraftsViewModel by viewModels()

    private val binding by viewBinding(FragmentDraftsBinding::bind)

    private val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

    private lateinit var draftsAdapter: DraftsAdapter

    /**
     * Action mode when the user is selecting one or more drafts.
     *
     * Null if there is no active selection.
     */
    private var selectDraftsActionMode: ActionMode? = null

    /**
     * Handles the user's draft selections.
     *
     * - Displays a count of selected drafts in the title.
     * - Shows a "Delete selected" option in the menu.
     * - Responds to the "Delete selected" option, asks for confirmation to delete.
     */
    private val deleteActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            requireActivity().menuInflater.inflate(R.menu.fragment_drafts, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.action_delete_drafts)?.isVisible = viewModel.countChecked() > 0
            return true
        }

        override fun onActionItemClicked(actionMode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_delete_drafts -> {
                    lifecycleScope.launch {
                        val button = AlertSuspendDialogFragment.newInstance(
                            title = getString(R.string.title_delete_drafts),
                            message = getString(R.string.delete_drafts_msg),
                            positiveText = getString(android.R.string.ok),
                            negativeText = getString(android.R.string.cancel),
                        ).await(childFragmentManager)

                        if (button == AlertDialog.BUTTON_POSITIVE) {
                            viewModel.deleteCheckedDrafts()
                        }
                        actionMode.finish()
                    }
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(actionMode: ActionMode) {
            selectDraftsActionMode = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.draftsRecyclerView.applyDefaultWindowInsets()

        binding.draftsErrorMessageView.setup(BackgroundMessage.Empty(R.string.no_drafts))

        draftsAdapter = DraftsAdapter(Glide.with(this), this)

        with(binding.draftsRecyclerView) {
            adapter = draftsAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL),
            )
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        draftsAdapter.addLoadStateListener {
            binding.draftsErrorMessageView.visible(draftsAdapter.itemCount == 0 && it.refresh is LoadState.NotLoading)
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    val notificationManager = ContextCompat.getSystemService(requireContext(), NotificationManager::class.java) ?: return@launch
                    viewModel.draftViewData.collectLatest { draftData ->
                        // Cancel any notifications about statuses saved to drafts so the user
                        // doesn't have to cancel them manually.
                        //
                        // Do this here to resolve a race condition.
                        //
                        // 1. DraftsActivity starts, shows the user a failed draft.
                        // 2. User taps draft to re-open, then re-sends.
                        // 3. DraftsActivity resumes.
                        // 4. SendStatusService tries to send the status, fails, updates the draft
                        // and posts a notification.
                        //
                        // If we don't cancel the notification here the user sees a notification for
                        // a draft they are already looking at.
                        notificationManager.activeNotifications?.forEach {
                            if (it.tag == SendStatusUseCase.TAG_SAVED_TO_DRAFTS) notificationManager.cancel(SendStatusUseCase.TAG_SAVED_TO_DRAFTS, it.id)
                        }

                        draftsAdapter.submitData(draftData)
                    }
                }
            }
        }
    }

    /**
     * Checks/unchecks [draft] according to [isChecked].
     *
     * If any draft is checked then [selectDraftsActionMode] is started (if
     * necessary) and the title is updated to show the count of selected
     * drafts.
     */
    override fun setDraftChecked(draft: Draft, isChecked: Boolean) {
        viewModel.checkDraft(draft, isChecked)
        val countChecked = viewModel.countChecked()
        if (selectDraftsActionMode == null && countChecked > 0) {
            selectDraftsActionMode = (requireActivity() as? AppCompatActivity)?.startSupportActionMode(deleteActionModeCallback)
        }
        if (selectDraftsActionMode != null && countChecked == 0) {
            selectDraftsActionMode?.finish()
            selectDraftsActionMode = null
        }
        selectDraftsActionMode?.title = resources.getQuantityString(R.plurals.selected_drafts, countChecked, countChecked)
    }

    /** @return True if [draft] is checked. */
    override fun isDraftChecked(draft: Draft) = viewModel.isDraftChecked(draft)

    /**
     * Handles taps on [draft].
     *
     * If [selectDraftsActionMode] is active then tapping a draft is equivalent to
     * toggling the [draft]'s checked state.
     *
     * Otherwise, prepare and launch [app.pachli.core.navigation.ComposeActivityIntent] to edit the draft.
     */
    override fun onOpenDraft(draft: Draft) {
        // Don't open drafts while selecting drafts to delete. Instead, tapping on the draft also
        // selects it.
        selectDraftsActionMode?.let {
            if (draft.state == Draft.State.DEFAULT) viewModel.toggleDraftChecked(draft)
            return
        }

        val composeOptions = ComposeOptions(
            draft = draft,
            kind = ComposeOptions.ComposeKind.EDIT_DRAFT,
        )

        if (draft.inReplyToId == null && draft.quotedStatusId == null) {
            resumeOrStartComposeActivity(ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions))
            return
        }

        draft.inReplyToId?.let { inReplyToId ->
            lifecycleScope.launch {
                viewModel.getStatus(inReplyToId)
                    .onSuccess {
                        resumeOrStartComposeActivity(
                            ComposeActivityIntent(
                                requireContext(),
                                pachliAccountId,
                                composeOptions.copy(
                                    referencingStatus = ComposeOptions.ReferencingStatus.ReplyingTo.from(it.body.asModel()),
                                ),
                            ),
                        )
                    }
                    .onFailure { error ->
                        Timber.w("failed loading reply information: %s", error)

                        if (error is ClientError.NotFound) {
                            // the original status to which a reply was drafted has been deleted
                            // let's open the ComposeActivity without reply information
                            Toast.makeText(context, getString(R.string.drafts_post_reply_removed), Toast.LENGTH_LONG).show()
                            resumeOrStartComposeActivity(ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions))
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.drafts_load_reply_failed_fmt, error.fmt(requireContext())),
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }
                    }
            }
            return
        }

        draft.quotedStatusId?.let { quotedStatusId ->
            lifecycleScope.launch {
                viewModel.getStatus(quotedStatusId)
                    .onSuccess {
                        resumeOrStartComposeActivity(
                            ComposeActivityIntent(
                                requireContext(),
                                pachliAccountId,
                                composeOptions.copy(
                                    referencingStatus = ComposeOptions.ReferencingStatus.Quoting.from(it.body.asModel()),
                                ),
                            ),
                        )
                    }
                    .onFailure { error ->
                        Timber.w("failed loading quote information: %s", error)

                        if (error is ClientError.NotFound) {
                            // the original status being quoted has been deleted
                            // let's open the ComposeActivity without quote information
                            Toast.makeText(context, getString(R.string.drafts_post_quote_removed), Toast.LENGTH_LONG).show()
                            resumeOrStartComposeActivity(ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions))
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.drafts_load_quote_failed_fmt, error.fmt(requireContext())),
                                Snackbar.LENGTH_SHORT,
                            ).show()
                        }
                    }
            }
        }
    }

    /**
     * Show a [ComposeActivity][app.pachli.components.compose.ComposeActivity] for [intent].
     *
     * If an existing activity exists for the draft in [intent] it is moved to the front, as
     * a draft can only be edited by one activity at a time.
     *
     * If no existing activity exists then a new activity is started.
     *
     * @param intent The intent containing the [app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions] and draft information.
     */
    private fun resumeOrStartComposeActivity(intent: ComposeActivityIntent) {
        val draftId = ComposeActivityIntent.getComposeOptions(intent).draft.id

        ContextCompat.getSystemService(requireContext(), ActivityManager::class.java)?.appTasks?.forEach {
            // No point in looking at anything except ComposeActivity
            if (it.taskInfo.baseActivity?.className != QuadrantConstants.COMPOSE_ACTIVITY) return@forEach

            val launchedComposeOptions = ComposeActivityIntent.getComposeOptionsOrNull(it.taskInfo.baseIntent) ?: return@forEach
            if (launchedComposeOptions.draft.id == draftId) {
                it.moveToFront()
                return
            }
        }
        startActivity(intent)
    }

    override fun onReselect() {
        binding.draftsRecyclerView.scrollToPosition(0)
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): DraftsFragment {
            val fragment = DraftsFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
