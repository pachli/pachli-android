/* Copyright 2022 Tusky Contributors
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

package app.pachli.components.viewthread

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.BaseActivity
import app.pachli.R
import app.pachli.components.accountlist.AccountListActivity
import app.pachli.components.accountlist.AccountListActivity.Companion.newIntent
import app.pachli.components.viewthread.edits.ViewEditsFragment
import app.pachli.databinding.FragmentViewThreadBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import app.pachli.util.hide
import app.pachli.util.openLink
import app.pachli.util.show
import app.pachli.util.viewBinding
import app.pachli.viewdata.AttachmentViewData.Companion.list
import app.pachli.viewdata.StatusViewData
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewThreadFragment :
    SFragment(),
    OnRefreshListener,
    StatusActionListener,
    MenuProvider {

    private val viewModel: ViewThreadViewModel by viewModels()

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var adapter: ThreadAdapter
    private lateinit var thisThreadsStatusId: String

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false

    /**
     * State of the "reveal" menu item that shows/hides content that is behind a content
     * warning. Setting this invalidates the menu to redraw the menu item.
     */
    private var revealButtonState = RevealButtonState.NO_BUTTON
        set(value) {
            field = value
            requireActivity().invalidateMenu()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thisThreadsStatusId = requireArguments().getString(ID_EXTRA)!!

        lifecycleScope.launch {
            val statusDisplayOptions = viewModel.statusDisplayOptions.value
            adapter = ThreadAdapter(statusDisplayOptions, this@ViewThreadFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_view_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this,
            ) { index -> adapter.currentList.getOrNull(index) },
        )
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )
        binding.recyclerView.addItemDecoration(ConversationLineItemDecoration(requireContext()))
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler

        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        var initialProgressBar = getProgressBarJob(binding.initialProgressBar, 500)
        var threadProgressBar = getProgressBarJob(binding.threadProgressBar, 500)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is ThreadUiState.Loading -> {
                        revealButtonState = RevealButtonState.NO_BUTTON

                        binding.recyclerView.hide()
                        binding.statusView.hide()

                        initialProgressBar = getProgressBarJob(binding.initialProgressBar, 500)
                        initialProgressBar.start()
                    }
                    is ThreadUiState.LoadingThread -> {
                        if (uiState.statusViewDatum == null) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        initialProgressBar.cancel()
                        threadProgressBar = getProgressBarJob(binding.threadProgressBar, 500)
                        threadProgressBar.start()

                        if (viewModel.isInitialLoad) {
                            adapter.submitList(listOf(uiState.statusViewDatum))

                            // else this "submit one and then all on success below" will always center on the one
                        }

                        revealButtonState = uiState.revealButton
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.show()
                        binding.statusView.hide()
                    }
                    is ThreadUiState.Error -> {
                        Log.w(TAG, "failed to load status", uiState.throwable)
                        initialProgressBar.cancel()
                        threadProgressBar.cancel()

                        revealButtonState = RevealButtonState.NO_BUTTON
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.hide()
                        binding.statusView.show()

                        binding.statusView.setup(uiState.throwable) { viewModel.retry(thisThreadsStatusId) }
                    }
                    is ThreadUiState.Success -> {
                        if (uiState.statusViewData.none { viewData -> viewData.isDetailed }) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        threadProgressBar.cancel()

                        adapter.submitList(uiState.statusViewData) {
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && viewModel.isInitialLoad) {
                                viewModel.isInitialLoad = false

                                // Ensure the top of the status is visible
                                (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                                    uiState.detailedStatusPosition,
                                    0,
                                )
                            }
                        }

                        revealButtonState = uiState.revealButton
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.show()
                        binding.statusView.hide()
                    }
                    is ThreadUiState.Refreshing -> {
                        threadProgressBar.cancel()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errors.collect { throwable ->
                Log.w(TAG, "failed to load status context", throwable)
                Snackbar.make(binding.root, R.string.error_generic, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.action_retry) {
                        viewModel.retry(thisThreadsStatusId)
                    }
                    .show()
            }
        }

        viewModel.loadThread(thisThreadsStatusId)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_view_thread, menu)
        val actionReveal = menu.findItem(R.id.action_reveal)
        actionReveal.isVisible = revealButtonState != RevealButtonState.NO_BUTTON
        actionReveal.setIcon(
            when (revealButtonState) {
                RevealButtonState.REVEAL -> R.drawable.ic_eye_24dp
                else -> R.drawable.ic_hide_media_24dp
            },
        )
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_reveal -> {
                viewModel.toggleRevealButton()
                true
            }
            R.id.action_open_in_web -> {
                context?.openLink(requireArguments().getString(URL_EXTRA)!!)
                true
            }
            R.id.action_refresh -> {
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_view_thread)
    }

    /**
     * Create a job to implement a delayed-visible progress bar.
     *
     * Delaying the visibility of the progress bar can improve user perception of UI speed because
     * fewer UI elements are appearing and disappearing.
     *
     * When started the job will wait `delayMs` then show `view`. If the job is cancelled at
     * any time `view` is hidden.
     */
    @CheckResult
    private fun getProgressBarJob(view: View, delayMs: Long) = viewLifecycleOwner.lifecycleScope.launch(
        start = CoroutineStart.LAZY,
    ) {
        try {
            delay(delayMs)
            view.show()
            awaitCancellation()
        } finally {
            view.hide()
        }
    }

    override fun onRefresh() {
        viewModel.refresh(thisThreadsStatusId)
    }

    override fun onReply(position: Int) {
        super.reply(adapter.currentList[position].status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.reblog(reblog, status)
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.favorite(favourite, status)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.bookmark(bookmark, status)
    }

    override fun onMore(view: View, position: Int) {
        super.more(adapter.currentList[position].status, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.currentList[position].status
        super.viewMedia(
            attachmentIndex,
            list(status, alwaysShowSensitiveMedia),
            view,
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter.currentList[position]
        if (thisThreadsStatusId == status.id) {
            // If already viewing this thread, don't reopen it.
            return
        }
        super.viewThread(status.actionableId, status.actionable.url)
    }

    override fun onViewUrl(url: String) {
        val status: StatusViewData? = viewModel.detailedStatus()
        if (status != null && status.status.url == url) {
            // already viewing the status with this url
            // probably just a preview federated and the user is clicking again to view more -> open the browser
            // this can happen with some friendica statuses
            requireContext().openLink(url)
            return
        }
        super.onViewUrl(url)
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in threads
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        viewModel.changeExpanded(expanded, adapter.currentList[position])
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        viewModel.changeContentShowing(isShowing, adapter.currentList[position])
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter.currentList[position].id
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (requireActivity() as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter.currentList[position].id
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (requireActivity() as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        viewModel.changeContentCollapsed(isCollapsed, adapter.currentList[position])
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    public override fun removeItem(position: Int) {
        adapter.currentList.getOrNull(position)?.let { status ->
            if (status.isDetailed) {
                // the main status we are viewing is being removed, finish the activity
                activity?.finish()
                return
            }
            viewModel.removeStatus(status)
        }
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter.currentList[position]
        viewModel.voteInPoll(choices, status)
    }

    override fun onShowEdits(position: Int) {
        val status = adapter.currentList[position]
        val viewEditsFragment = ViewEditsFragment.newInstance(status.actionableId)

        parentFragmentManager.commit {
            setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left, R.anim.slide_from_left, R.anim.slide_to_right)
            replace(R.id.fragment_container, viewEditsFragment, "ViewEditsFragment_$id")
            addToBackStack(null)
        }
    }

    override fun clearWarningAction(position: Int) {
        viewModel.clearWarning(adapter.currentList[position])
    }

    companion object {
        private const val TAG = "ViewThreadFragment"

        private const val ID_EXTRA = "id"
        private const val URL_EXTRA = "url"

        fun newInstance(id: String, url: String): ViewThreadFragment {
            val arguments = Bundle(2)
            val fragment = ViewThreadFragment()
            arguments.putString(ID_EXTRA, id)
            arguments.putString(URL_EXTRA, url)
            fragment.arguments = arguments
            return fragment
        }
    }
}
