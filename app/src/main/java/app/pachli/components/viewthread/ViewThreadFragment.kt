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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.viewthread.edits.ViewEditsFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AttachmentViewData.Companion.list
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.databinding.FragmentViewThreadBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import app.pachli.viewdata.StatusViewData
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlin.properties.Delegates
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ViewThreadFragment :
    SFragment<StatusViewData>(),
    OnRefreshListener,
    StatusActionListener<StatusViewData>,
    MenuProvider {

    private val viewModel: ViewThreadViewModel by viewModels()

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var adapter: ThreadAdapter
    private lateinit var thisThreadsStatusId: String

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false

    override var pachliAccountId by Delegates.notNull<Long>()

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
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
        thisThreadsStatusId = requireArguments().getString(ARG_ID)!!

        lifecycleScope.launch {
            val statusDisplayOptions = viewModel.statusDisplayOptions.value
            adapter = ThreadAdapter(pachliAccountId, statusDisplayOptions, this@ViewThreadFragment)
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
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                pachliAccountId,
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is ThreadUiState.Loading -> {
                        revealButtonState = RevealButtonState.NO_BUTTON

                        binding.recyclerView.hide()
                        binding.statusView.hide()

                        binding.initialProgressBar.show()
                    }
                    is ThreadUiState.LoadingThread -> {
                        if (uiState.statusViewDatum == null) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        binding.initialProgressBar.hide()
                        binding.threadProgressBar.show()

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
                        Timber.w(uiState.throwable, "failed to load status")
                        binding.initialProgressBar.hide()
                        binding.threadProgressBar.hide()

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

                        binding.threadProgressBar.hide()

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
                        binding.threadProgressBar.hide()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errors.collect { throwable ->
                Timber.w(throwable, "failed to load status context")
                val msg = throwable.getErrorString(view.context)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(app.pachli.core.ui.R.string.action_retry) {
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
                context?.openLink(requireArguments().getString(ARG_URL)!!)
                true
            }
            R.id.action_refresh -> {
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun canTranslate() = true

    override fun onTranslate(statusViewData: StatusViewData) {
        viewModel.translate(statusViewData)
    }

    override fun onTranslateUndo(statusViewData: StatusViewData) {
        viewModel.translateUndo(statusViewData)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_view_thread)
    }

    override fun onRefresh() {
        viewModel.refresh(thisThreadsStatusId)
    }

    override fun onReply(viewData: StatusViewData) {
        super.reply(viewData.actionable)
    }

    override fun onReblog(viewData: StatusViewData, reblog: Boolean) {
        viewModel.reblog(reblog, viewData)
    }

    override fun onFavourite(viewData: StatusViewData, favourite: Boolean) {
        viewModel.favorite(favourite, viewData)
    }

    override fun onBookmark(viewData: StatusViewData, bookmark: Boolean) {
        viewModel.bookmark(bookmark, viewData)
    }

    override fun onMore(view: View, viewData: StatusViewData) {
        super.more(view, viewData)
    }

    override fun onViewMedia(viewData: StatusViewData, attachmentIndex: Int, view: View?) {
        super.viewMedia(
            viewData.username,
            attachmentIndex,
            list(viewData.actionable, alwaysShowSensitiveMedia),
            view,
        )
    }

    override fun onViewThread(status: Status) {
        if (thisThreadsStatusId == status.id) {
            // If already viewing this thread, don't reopen it.
            return
        }
        super.viewThread(status.actionableId, status.actionableStatus.url)
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

    override fun onOpenReblog(status: Status) {
        // there are no reblogs in threads
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        requireActivity().startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onExpandedChange(pachliAccountId: Long, viewData: StatusViewData, expanded: Boolean) {
        viewModel.changeExpanded(expanded, viewData)
    }

    override fun onContentHiddenChange(pachliAccountId: Long, viewData: StatusViewData, isShowing: Boolean) {
        viewModel.changeContentShowing(isShowing, viewData)
    }

    override fun onShowReblogs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.REBLOGGED, statusId)
        activity?.startActivityWithDefaultTransition(intent)
    }

    override fun onShowFavs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.FAVOURITED, statusId)
        activity?.startActivityWithDefaultTransition(intent)
    }

    override fun onContentCollapsedChange(pachliAccountId: Long, viewData: StatusViewData, isCollapsed: Boolean) {
        viewModel.changeContentCollapsed(isCollapsed, viewData)
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    public override fun removeItem(viewData: StatusViewData) {
        if (viewData.isDetailed) {
            // the main status we are viewing is being removed, finish the activity
            activity?.finish()
            return
        }
        viewModel.removeStatus(viewData)
    }

    override fun onVoteInPoll(viewData: StatusViewData, poll: Poll, choices: List<Int>) {
        viewModel.voteInPoll(poll, choices, viewData)
    }

    override fun onShowEdits(statusId: String) {
        val viewEditsFragment = ViewEditsFragment.newInstance(pachliAccountId, statusId)

        parentFragmentManager.commit {
            setCustomAnimations(
                DR.anim.activity_open_enter,
                DR.anim.activity_open_exit,
                DR.anim.activity_close_enter,
                DR.anim.activity_close_exit,
            )
            replace(R.id.fragment_container, viewEditsFragment, "ViewEditsFragment_$id")
            addToBackStack(null)
        }
    }

    override fun clearWarningAction(pachliAccountId: Long, viewData: StatusViewData) {
        viewModel.clearWarning(viewData)
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
        private const val ARG_ID = "app.pachli.ARG_ID"
        private const val ARG_URL = "app.pachli.ARG_URL"

        fun newInstance(pachliAccountId: Long, id: String, url: String?): ViewThreadFragment {
            val fragment = ViewThreadFragment()
            fragment.arguments = Bundle(3).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
                putString(ARG_ID, id)
                putString(ARG_URL, url)
            }
            return fragment
        }
    }
}
