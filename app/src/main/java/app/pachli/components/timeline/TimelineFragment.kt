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
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.components.timeline.viewmodel.CachedTimelineViewModel
import app.pachli.components.timeline.viewmodel.InfallibleUiAction
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.timeline.viewmodel.StatusAction
import app.pachli.components.timeline.viewmodel.StatusActionSuccess
import app.pachli.components.timeline.viewmodel.TimelineViewModel
import app.pachli.components.timeline.viewmodel.UiError
import app.pachli.components.timeline.viewmodel.UiSuccess
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import at.connyduck.sparkbutton.helpers.Utils
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import postPrepend
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
    private val viewModel: TimelineViewModel<out Any> by lazy {
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

    override var pachliAccountId by Delegates.notNull<Long>()

    /**
     * Collect this flow to notify the adapter that the timestamps of the visible items have
     * changed
     */
    private val updateTimestampFlow = flow {
        while (true) {
            delay(60.seconds)
            emit(Unit)
        }
    }.onEach {
        adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = requireArguments()

        pachliAccountId = arguments.getLong(ARG_PACHLI_ACCOUNT_ID)

        timeline = arguments.getParcelable(ARG_KIND)!!

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        adapter = TimelinePagingAdapter(this, viewModel.statusDisplayOptions.value)
//        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Wait for the very first page load, then scroll recyclerview
                    // to the refresh key.
                    (viewModel as? CachedTimelineViewModel)?.let { vm ->
                        vm.initialRefreshKey.combine(adapter.onPagesUpdatedFlow) { key, _ -> key }
                            .take(1)
                            .filterNotNull()
                            .collect { key ->
                                val snapshot = adapter.snapshot()
                                val index = snapshot.items.indexOfFirst { it.id == key }
                                binding.recyclerView.scrollToPosition(
                                    snapshot.placeholdersBefore + index,
                                )
                            }
                    }
                }

                launch { viewModel.statuses.collectLatest { adapter.submitData(it) } }

                launch { viewModel.uiResult.collect(::bindUiResult) }

                // Collect the uiState.
                launch {
                    viewModel.uiState.collect { uiState ->
                        if (layoutManager.reverseLayout != uiState.reverseTimeline) {
                            layoutManager.reverseLayout = uiState.reverseTimeline
                            layoutManager.stackFromEnd = uiState.reverseTimeline
                            // Force recyclerView to re-layout everything.
                            binding.recyclerView.layoutManager = null
                            binding.recyclerView.layoutManager = layoutManager
                        }
                    }
                }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically re-bind the UI.
                launch {
                    viewModel.statusDisplayOptions.collectLatest {
                        adapter.statusDisplayOptions = it
                        adapter.notifyItemRangeChanged(0, adapter.itemCount, null)

                        if (!it.useAbsoluteTime) {
                            updateTimestampFlow.collect()
                        }
                    }
                }

                // TODO: Move to bindLoadState function
                adapter.loadStateFlow.distinctUntilChangedBy { it.refresh }.collect(::bindLoadState)
            }
        }
    }


    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
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
        uiResult.onFailure { uiError ->
            val message = getString(
                uiError.message,
                uiError.throwable.getErrorString(requireContext()),
            )
            Timber.d(uiError.throwable, message)
            snackbar?.dismiss()
            snackbar = Snackbar.make(
                // Without this the FAB will not move out of the way
                (activity as? ActionButtonActivity)?.actionButton ?: binding.root,
                message,
                Snackbar.LENGTH_INDEFINITE,
            )
            uiError.action?.let { action ->
                snackbar!!.setAction(app.pachli.core.ui.R.string.action_retry) {
                    viewModel.accept(action)
                }
            }
            snackbar!!.show()

            // The status view has pre-emptively updated its state to show
            // that the action succeeded. Since it hasn't, re-bind the view
            // to show the correct data.
            uiError.action?.let { action ->
                if (action !is StatusAction) return@let

                adapter.snapshot()
                    .indexOfFirst { it?.id == action.statusViewData.id }
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { adapter.notifyItemChanged(it) }
            }
        }

        uiResult.onSuccess {
            // Update adapter data when status actions are successful, and re-bind to update
            // the UI.
            // TODO: No - this should be handled by the ViewModel updating the data
            // and invalidating the paging source
            if (it is StatusActionSuccess) {
                val indexedViewData = adapter.snapshot()
                    .withIndex()
                    .firstOrNull { indexed ->
                        indexed.value?.id == it.action.statusViewData.id
                    } ?: return

                val statusViewData = indexedViewData.value ?: return

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

//                adapter.notifyItemChanged(indexedViewData.index)
            }

            // Refresh adapter on mutes and blocks
            when (it) {
                is UiSuccess.Block, is UiSuccess.Mute, is UiSuccess.MuteConversation,
                -> adapter.refresh()

                is UiSuccess.StatusSent -> handleStatusSentOrEdit(it.status)
                is UiSuccess.StatusEdited -> handleStatusSentOrEdit(it.status)

                is UiSuccess.LoadNewest -> {
                    // Scroll to the top when prepending completes.
                    viewLifecycleOwner.lifecycleScope.launch {
                        adapter.postPrepend {
                            binding.recyclerView.post {
                                view ?: return@post
                                binding.recyclerView.scrollToPosition(0)
                            }
                        }
                    }
                    adapter.refresh()
                }

                else -> { /* nothing to do */ }
            }
        }
    }

    /**
     * Binds [CombinedLoadStates] to the UI.
     *
     * Updates the UI based on the contents of [loadState.refresh][CombinedLoadStates.refresh]
     * to show/hide Error, Loading, and NotLoading states.
     */
    private fun bindLoadState(loadState: CombinedLoadStates) {
        when (loadState.refresh) {
            is LoadState.Error -> {
                binding.progressIndicator.hide()
                binding.statusView.setup((loadState.refresh as LoadState.Error).error) {
                    adapter.retry()
                }
                binding.recyclerView.hide()
                binding.statusView.show()
                binding.swipeRefreshLayout.isRefreshing = false
            }

            LoadState.Loading -> {
                binding.statusView.hide()
                binding.progressIndicator.show()
            }

            is LoadState.NotLoading -> {
                // Might still be loading if source.refresh is Loading, so only update
                // the UI when loading is completely quiet.
                if (loadState.source.refresh !is LoadState.Loading) {
                    binding.progressIndicator.hide()
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (adapter.itemCount == 0) {
                        binding.statusView.setup(BackgroundMessage.Empty())
                        if (timeline == Timeline.Home) {
                            binding.statusView.showHelp(R.string.help_empty_home)
                        }
                        binding.recyclerView.hide()
                        binding.statusView.show()
                    } else {
                        binding.statusView.hide()
                        binding.recyclerView.show()
                    }
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_timeline, menu)

        if (isSwipeToRefreshEnabled) {
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
                Timber.d("Reload because user chose load newest menu item")
                viewModel.accept(InfallibleUiAction.LoadNewest)
                true
            }
            else -> false
        }
    }

    private fun getFirstVisibleStatus() = (
        layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findLastVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
        )?.let { adapter.snapshot().getOrNull(it) }

    /**
     * Saves the ID of the first visible status as the reading position.
     *
     * If null then the ID of the best status is used.
     *
     * The best status is the first completely visible status, if available. We assume the user
     * has read this far, or will recognise it on return.
     *
     * However, there may not be a completely visible status. E.g., the user is viewing one
     * status that is longer the the height of the screen, or the user is at the midpoint of
     * two statuses that are each longer than half the height of the screen.
     *
     * In this case the best status is the last partially visible status, as we can assume the
     * user has read this far.
     */
    fun saveVisibleId() {
        val id = getFirstVisibleStatus()?.id
        if (BuildConfig.DEBUG && id == null) {
            Toast.makeText(requireActivity(), "Could not find ID of item to save", LENGTH_LONG).show()
        }
        id?.let {
            viewModel.accept(InfallibleUiAction.SaveVisibleId(pachliAccountId, id))
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(pachliAccountId, binding.recyclerView, this) { pos ->
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
        onRefresh()
    }

    /**
     * Listener for the user swiping on the SwipeRefreshLayout. The SwipeRefreshLayout has
     * handled displaying the animated spinner.
     */
    override fun onRefresh() {
        Timber.d("Reloading via onRefresh")

        // Peek the list when refreshing completes.
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.postPrepend {
                binding.recyclerView.post {
                    view ?: return@post
                    binding.recyclerView.smoothScrollBy(
                        0,
                        Utils.dpToPx(requireContext(), -30),
                    )
                }
            }
        }

        binding.swipeRefreshLayout.isRefreshing = false
        adapter.refresh()
    }

    override fun onReply(viewData: StatusViewData) {
        super.reply(viewData.pachliAccountId, viewData.actionable)
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

    override fun clearContentFilter(viewData: StatusViewData) {
        viewModel.clearWarning(viewData)
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        requireActivity().startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
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

    override fun onContentHiddenChange(viewData: StatusViewData, isShowingContent: Boolean) {
        viewModel.changeContentShowing(isShowingContent, viewData)
    }

    override fun onShowReblogs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.REBLOGGED, statusId)
        activity?.startActivityWithDefaultTransition(intent)
    }

    override fun onShowFavs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.FAVOURITED, statusId)
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

        super.viewMedia(actionable.account.username, attachmentIndex, AttachmentViewData.list(actionable), view)
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
            is Timeline.Link,
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
            when (viewModel.uiState.value.tabTapBehaviour) {
                TabTapBehaviour.JUMP_TO_NEXT_PAGE -> {
                    binding.recyclerView.scrollToPosition(0)
                    binding.recyclerView.stopScroll()
                    saveVisibleId()
                }

                TabTapBehaviour.JUMP_TO_NEWEST -> viewModel.accept(InfallibleUiAction.LoadNewest)
            }
        }
    }

    companion object {
        private const val ARG_KIND = "app.pachli.ARG_KIND"
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "app.pachli.ARG_ENABLE_SWIPE_TO_REFRESH"

        fun newInstance(
            pachliAccountId: Long,
            timeline: Timeline,
            enableSwipeToRefresh: Boolean = true,
        ): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(3)
            arguments.putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            arguments.putParcelable(ARG_KIND, timeline)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh)
            fragment.arguments = arguments
            return fragment
        }
    }
}
