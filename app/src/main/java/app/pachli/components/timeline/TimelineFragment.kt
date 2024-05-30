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

package app.pachli.components.timeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.components.timeline.util.isExpected
import app.pachli.components.timeline.viewmodel.CachedTimelineViewModel
import app.pachli.components.timeline.viewmodel.InfallibleUiAction
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.timeline.viewmodel.StatusAction
import app.pachli.components.timeline.viewmodel.StatusActionSuccess
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.components.timeline.viewmodel.UiSuccess
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.interfaces.ReselectableFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import app.pachli.util.PresentationState
import app.pachli.util.UserRefreshState
import app.pachli.util.asRefreshState
import app.pachli.util.withPresentationState
import app.pachli.viewdata.StatusViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TimelineFragment :
    SFragment<StatusViewData>(),
    OnRefreshListener,
    StatusActionListener<StatusViewData>,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    // Create the correct view model. Do this lazily because it depends on the value of
    // `timelineKind`, which won't be known until part way through `onCreate`. Pass this in
    // the "extras" to the view model, which are populated in to the `SavedStateHandle` it
    // takes as a parameter.
    //
    // If the navigation library was being used this would happen automatically, so this
    // workaround can be removed when that change happens.
    private val viewModel: TimelineViewModel by lazy {
        if (timeline == Timeline.Home) {
            viewModels<CachedTimelineViewModel>(
                extrasProducer = {
                    MutableCreationExtras(defaultViewModelCreationExtras).apply {
                        set(DEFAULT_ARGS_KEY, TimelineViewModel.creationExtras(timeline))
                    }
                },
            ).value
        } else {
            viewModels<NetworkTimelineViewModel>(
                extrasProducer = {
                    MutableCreationExtras(defaultViewModelCreationExtras).apply {
                        set(DEFAULT_ARGS_KEY, TimelineViewModel.creationExtras(timeline))
                    }
                },
            ).value
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var timeline: Timeline

    private lateinit var adapter: TimelinePagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    /** The active snackbar, if any */
    // TODO: This shouldn't be necessary, the snackbar should dismiss itself if the layout
    // changes. It doesn't, because the CoordinatorLayout is in the activity, not the fragment.
    // I think the correct fix is to include the FAB in each fragment layout that needs it,
    // ensuring that the outermost fragment view is a CoordinatorLayout. That will auto-dismiss
    // the snackbar when the fragment is paused.
    private var snackbar: Snackbar? = null

    private var isSwipeToRefreshEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = requireArguments()

        timeline = arguments.getParcelable(KIND_ARG)!!

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        adapter = TimelinePagingAdapter(this, viewModel.statusDisplayOptions.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.statuses.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }
            }
        }

        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        layoutManager = LinearLayoutManager(context)

        setupSwipeRefreshLayout()
        setupRecyclerView()

        (activity as? ActionButtonActivity)?.actionButton?.let { actionButton ->
            actionButton.show()

            val actionButtonScrollListener = ActionButtonScrollListener(actionButton)
            binding.recyclerView.addOnScrollListener(actionButtonScrollListener)

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.uiState.distinctUntilChangedBy { it.showFabWhileScrolling }.collect {
                        actionButtonScrollListener.showActionButtonWhileScrolling = it.showFabWhileScrolling
                    }
                }
            }
        }

        /**
         * Collect this flow to notify the adapter that the timestamps of the visible items have
         * changed
         */
        // TODO: Copied from NotificationsFragment
        val updateTimestampFlow = flow {
            while (true) {
                delay(60.seconds)
                emit(Unit)
            }
        }.onEach {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Show errors from the view model as snack bars.
                //
                // Errors are shown:
                // - Indefinitely, so the user has a chance to read and understand
                //   the message
                // - With a max of 5 text lines, to allow space for longer errors.
                //   E.g., on a typical device, an error message like "Bookmarking
                //   post failed: Unable to resolve host 'mastodon.social': No
                //   address associated with hostname" is 3 lines.
                // - With a "Retry" option if the error included a UiAction to retry.
                // TODO: Very similar to same code in NotificationsFragment
                launch {
                    viewModel.uiError.collect { error ->
                        val message = getString(
                            error.message,
                            error.throwable.getErrorString(requireContext()),
                        )
                        Timber.d(error.throwable, message)
                        snackbar = Snackbar.make(
                            // Without this the FAB will not move out of the way
                            (activity as? ActionButtonActivity)?.actionButton ?: binding.root,
                            message,
                            Snackbar.LENGTH_INDEFINITE,
                        )
                        error.action?.let { action ->
                            snackbar!!.setAction(app.pachli.core.ui.R.string.action_retry) {
                                viewModel.accept(action)
                            }
                        }
                        snackbar!!.show()

                        // The status view has pre-emptively updated its state to show
                        // that the action succeeded. Since it hasn't, re-bind the view
                        // to show the correct data.
                        error.action?.let { action ->
                            if (action !is StatusAction) return@let

                            adapter.snapshot()
                                .indexOfFirst { it?.id == action.statusViewData.id }
                                .takeIf { it != RecyclerView.NO_POSITION }
                                ?.let { adapter.notifyItemChanged(it) }
                        }
                    }
                }

                // Update adapter data when status actions are successful, and re-bind to update
                // the UI.
                launch {
                    viewModel.uiSuccess
                        .filterIsInstance<StatusActionSuccess>()
                        .collect {
                            val indexedViewData = adapter.snapshot()
                                .withIndex()
                                .firstOrNull { indexed ->
                                    indexed.value?.id == it.action.statusViewData.id
                                } ?: return@collect

                            val statusViewData =
                                indexedViewData.value ?: return@collect

                            val status = when (it) {
                                is StatusActionSuccess.Bookmark ->
                                    statusViewData.status.copy(bookmarked = it.action.state)
                                is StatusActionSuccess.Favourite ->
                                    statusViewData.status.copy(favourited = it.action.state)
                                is StatusActionSuccess.Reblog ->
                                    statusViewData.status.copy(reblogged = it.action.state)
                                is StatusActionSuccess.VoteInPoll ->
                                    statusViewData.status.copy(
                                        poll = it.action.poll.votedCopy(it.action.choices),
                                    )
                                is StatusActionSuccess.Translate -> statusViewData.status
                            }
                            (indexedViewData.value as StatusViewData).status = status

                            adapter.notifyItemChanged(indexedViewData.index)
                        }
                }

                // Refresh adapter on mutes and blocks
                launch {
                    viewModel.uiSuccess.collectLatest {
                        when (it) {
                            is UiSuccess.Block,
                            is UiSuccess.Mute,
                            is UiSuccess.MuteConversation,
                            ->
                                adapter.refresh()

                            is UiSuccess.StatusSent -> handleStatusSentOrEdit(it.status)
                            is UiSuccess.StatusEdited -> handleStatusSentOrEdit(it.status)

                            else -> { /* nothing to do */ }
                        }
                    }
                }

                // Collect the uiState. Nothing is done with it, but if you don't collect it then
                // accessing viewModel.uiState.value (e.g., to check whether the FAB should be
                // hidden) always returns the initial state.
                launch { viewModel.uiState.collect() }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically re-bind the UI.
                launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            adapter.statusDisplayOptions = it
                            layoutManager.findFirstVisibleItemPosition().let { first ->
                                first == RecyclerView.NO_POSITION && return@let
                                val count = layoutManager.findLastVisibleItemPosition() - first
                                adapter.notifyItemRangeChanged(
                                    first,
                                    count,
                                    null,
                                )
                            }

                            if (!it.useAbsoluteTime) {
                                updateTimestampFlow.collect()
                            }
                        }
                }

                /** StateFlow (to allow multiple consumers) of UserRefreshState */
                val refreshState = adapter.loadStateFlow.asRefreshState().stateIn(lifecycleScope)

                // Scroll the list down (peek) if a refresh has completely finished. A refresh is
                // finished when both the initial refresh is complete and any prepends have
                // finished (so that DiffUtil has had a chance to process the data).
                launch {
                    if (!isSwipeToRefreshEnabled) return@launch

                    /** True if the previous prepend resulted in a peek, false otherwise */
                    var peeked = false

                    /** ID of the item that was first in the adapter before the refresh */
                    var previousFirstId: String? = null

                    refreshState.collect { userRefreshState ->
                        if (userRefreshState == UserRefreshState.ACTIVE) {
                            // Refresh has started, reset peeked, and save the ID of the first item
                            // in the adapter
                            peeked = false
                            if (adapter.itemCount != 0) previousFirstId = adapter.peek(0)?.id
                        }

                        if (userRefreshState == UserRefreshState.COMPLETE) {
                            // Refresh has finished, pages are being prepended.

                            // There might be multiple prepends after a refresh, only continue
                            // if one them has not already caused a peek.
                            if (peeked) return@collect

                            // Compare the ID of the current first item with the previous first
                            // item. If they're the same then this prepend did not add any new
                            // items, and can be ignored.
                            val firstId = if (adapter.itemCount != 0) adapter.peek(0)?.id else null
                            if (previousFirstId == firstId) return@collect

                            // New items were added and haven't peeked for this refresh. Schedule
                            // a scroll to disclose that new items are available.
                            binding.recyclerView.post {
                                getView() ?: return@post
                                binding.recyclerView.smoothScrollBy(
                                    0,
                                    Utils.dpToPx(requireContext(), -30),
                                )
                            }
                            peeked = true
                        }
                    }
                }

                // Manage the progress display. Rather than hide as soon as the Refresh portion
                // completes, hide when then first Prepend completes. This is a better signal to
                // the user that it is now possible to scroll up and see new content.
                launch {
                    refreshState.collect {
                        when (it) {
                            UserRefreshState.COMPLETE, UserRefreshState.ERROR -> {
                                binding.swipeRefreshLayout.isRefreshing = false
                            }
                            else -> { /* nothing to do */ }
                        }
                    }
                }

                // Update the UI from the combined load state
                launch {
                    adapter.loadStateFlow.withPresentationState()
                        .collect { (loadState, presentationState) ->
                            when (presentationState) {
                                PresentationState.ERROR -> {
                                    val error = (loadState.mediator?.refresh as? LoadState.Error)?.error
                                        ?: (loadState.source.refresh as? LoadState.Error)?.error
                                        ?: IllegalStateException("unknown error")

                                    // TODO: This error message should be specific about the operation
                                    // At the moment it's just e.g., "An error occurred: HTTP 503"
                                    // and a "Retry" button, so the user has no idea what's going
                                    // to be retried.
                                    val message = error.getErrorString(requireContext())

                                    // Show errors as a snackbar if there is existing content to show
                                    // (either cached, or in the adapter), or as a full screen error
                                    // otherwise.
                                    //
                                    // Expected errors can be retried, unexpected ones cannot
                                    if (adapter.itemCount > 0) {
                                        snackbar = Snackbar.make(
                                            (activity as ActionButtonActivity).actionButton
                                                ?: binding.root,
                                            message,
                                            Snackbar.LENGTH_INDEFINITE,
                                        ).apply {
                                            if (error.isExpected()) {
                                                setAction(app.pachli.core.ui.R.string.action_retry) { adapter.retry() }
                                            }
                                        }

                                        snackbar!!.show()
                                    } else {
                                        val callback: ((v: View) -> Unit)? = if (error.isExpected()) {
                                            {
                                                snackbar?.dismiss()
                                                adapter.retry()
                                            }
                                        } else {
                                            null
                                        }

                                        binding.statusView.setup(error, callback)
                                        binding.statusView.show()
                                        binding.recyclerView.hide()
                                    }
                                }

                                PresentationState.PRESENTED -> {
                                    if (adapter.itemCount == 0) {
                                        binding.statusView.setup(BackgroundMessage.Empty())
                                        if (timeline == Timeline.Home) {
                                            binding.statusView.showHelp(R.string.help_empty_home)
                                        }
                                        binding.statusView.show()
                                        binding.recyclerView.hide()
                                    } else {
                                        binding.recyclerView.show()
                                        binding.statusView.hide()
                                    }
                                }

                                else -> {
                                    // Nothing to do -- show/hiding the progress bars in non-error states
                                    // is handled via refreshState.
                                }
                            }
                        }
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (isSwipeToRefreshEnabled) {
            menuInflater.inflate(R.menu.fragment_timeline, menu)
            menu.findItem(R.id.action_refresh)?.apply {
                icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                    sizeDp = 20
                    colorInt =
                        MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
                }
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                if (isSwipeToRefreshEnabled) {
                    Timber.d("Reload because user chose refresh menu item")
                    refreshContent()
                    true
                } else {
                    false
                }
            }
            R.id.action_load_newest -> {
                Timber.d("Reload because user choose load newest menu item")
                viewModel.accept(InfallibleUiAction.LoadNewest)
                refreshContent()
                true
            }
            else -> false
        }
    }

    /**
     * Save [statusId] as the reading position. If null then the ID of the first completely visible
     * status is used. It is the first status so that when performing a pull-refresh the
     * previous first status always remains visible.
     */
    fun saveVisibleId(statusId: String? = null) {
        val id = statusId ?: layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let { adapter.snapshot().getOrNull(it)?.id }

        id?.let {
            Timber.d("Saving ID: %s", it)
            viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = it))
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this) { pos ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            },
        )
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = TimelineLoadStateAdapter { adapter.retry() },
            footer = TimelineLoadStateAdapter { adapter.retry() },
        )

        binding.recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == SCROLL_STATE_IDLE) saveVisibleId()
                }
            },
        )
    }

    /** Refresh the displayed content, as if the user had swiped on the SwipeRefreshLayout */
    override fun refreshContent() {
        Timber.d("Reloading via refreshContent")
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    /**
     * Listener for the user swiping on the SwipeRefreshLayout. The SwipeRefreshLayout has
     * handled displaying the animated spinner.
     */
    override fun onRefresh() {
        Timber.d("Reloading via onRefresh")
        binding.statusView.hide()
        snackbar?.dismiss()
        adapter.refresh()
    }

    override fun onReply(viewData: StatusViewData) {
        super.reply(viewData.actionable)
    }

    override fun onReblog(viewData: StatusViewData, reblog: Boolean) {
        viewModel.accept(StatusAction.Reblog(reblog, viewData))
    }

    override fun onFavourite(viewData: StatusViewData, favourite: Boolean) {
        viewModel.accept(StatusAction.Favourite(favourite, viewData))
    }

    override fun onBookmark(viewData: StatusViewData, bookmark: Boolean) {
        viewModel.accept(StatusAction.Bookmark(bookmark, viewData))
    }

    override fun onVoteInPoll(viewData: StatusViewData, poll: Poll, choices: List<Int>) {
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, viewData))
    }

    override fun clearWarningAction(viewData: StatusViewData) {
        viewModel.clearWarning(viewData)
    }

    override fun onMore(view: View, viewData: StatusViewData) {
        super.more(view, viewData)
    }

    override fun onOpenReblog(status: Status) {
        super.openReblog(status)
    }

    override fun onExpandedChange(viewData: StatusViewData, expanded: Boolean) {
        viewModel.changeExpanded(expanded, viewData)
    }

    override fun onContentHiddenChange(viewData: StatusViewData, isShowing: Boolean) {
        viewModel.changeContentShowing(isShowing, viewData)
    }

    override fun onShowReblogs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), AccountListActivityIntent.Kind.REBLOGGED, statusId)
        activity?.startActivityWithDefaultTransition(intent)
    }

    override fun onShowFavs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), AccountListActivityIntent.Kind.FAVOURITED, statusId)
        activity?.startActivityWithDefaultTransition(intent)
    }

    override fun onContentCollapsedChange(viewData: StatusViewData, isCollapsed: Boolean) {
        viewModel.changeContentCollapsed(isCollapsed, viewData)
    }

    // Can only translate the home timeline at the moment
    override fun canTranslate() = timeline == Timeline.Home

    override fun onTranslate(statusViewData: StatusViewData) {
        viewModel.accept(StatusAction.Translate(statusViewData))
    }

    override fun onTranslateUndo(statusViewData: StatusViewData) {
        viewModel.accept(InfallibleUiAction.TranslateUndo(statusViewData))
    }

    override fun onViewMedia(viewData: StatusViewData, attachmentIndex: Int, view: View?) {
        // Pass the translated media descriptions through (if appropriate)
        val actionable = if (viewData.translationState == TranslationState.SHOW_TRANSLATION) {
            viewData.actionable.copy(
                attachments = viewData.translation?.attachments?.zip(viewData.actionable.attachments) { t, a ->
                    a.copy(description = t.description)
                } ?: viewData.actionable.attachments,
            )
        } else {
            viewData.actionable
        }

        super.viewMedia(attachmentIndex, AttachmentViewData.list(actionable), view)
    }

    override fun onViewThread(status: Status) {
        super.viewThread(status.id, status.url)
    }

    override fun onViewTag(tag: String) {
        val timelineKind = viewModel.timeline

        // If already viewing a tag page, then ignore any request to view that tag again.
        if ((timelineKind as? Timeline.Hashtags)?.tags?.contains(tag) == true) {
            return
        }

        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        val timelineKind = viewModel.timeline

        // Ignore request to view the account page we're currently viewing
        if (timelineKind is Timeline.User && timelineKind.id == id) {
            return
        }

        super.viewAccount(id)
    }

    /**
     * A status the user has written has either:
     *
     * - Been successfully posted
     * - Been edited by the user
     *
     * Depending on the timeline kind it may need refreshing to show the new status or the changes
     * that have been made to it.
     */
    private fun handleStatusSentOrEdit(status: Status) {
        when (timeline) {
            is Timeline.User.Pinned -> return

            is Timeline.Home,
            is Timeline.PublicFederated,
            is Timeline.PublicLocal,
            -> adapter.refresh()
            is Timeline.User -> if (status.account.id == (timeline as Timeline.User).id) {
                adapter.refresh()
            }
            is Timeline.Bookmarks,
            is Timeline.Favourites,
            is Timeline.Hashtags,
            is Timeline.TrendingStatuses,
            is Timeline.UserList,
            is Timeline.Conversations,
            Timeline.Notifications,
            Timeline.TrendingHashtags,
            Timeline.TrendingLinks,
            -> return
        }
    }

    public override fun removeItem(viewData: StatusViewData) {
        viewModel.removeStatusWithId(viewData.id)
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Timber.d("talkback was enabled: %s, now %s", wasEnabled, talkBackWasEnabled)
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        (requireActivity() as? AppBarLayoutHost)?.appBarLayout?.setLiftOnScrollTargetView(binding.recyclerView)
    }

    override fun onPause() {
        super.onPause()

        saveVisibleId()
        snackbar?.dismiss()
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.scrollToPosition(0)
            binding.recyclerView.stopScroll()
            saveVisibleId()
        }
    }

    companion object {
        private const val KIND_ARG = "kind"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"

        fun newInstance(
            timeline: Timeline,
            enableSwipeToRefresh: Boolean = true,
        ): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(2)
            arguments.putParcelable(KIND_ARG, timeline)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh)
            fragment.arguments = arguments
            return fragment
        }
    }
}
