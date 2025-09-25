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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.components.timeline.viewmodel.CachedTimelineViewModel
import app.pachli.components.timeline.viewmodel.FallibleStatusAction
import app.pachli.components.timeline.viewmodel.InfallibleStatusAction
import app.pachli.components.timeline.viewmodel.InfallibleUiAction
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
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
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.SetMarkdownContent
import app.pachli.core.ui.SetMastodonHtmlContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.util.ListStatusAccessibilityDelegate
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
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
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
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
    // the "extras" to the view model.
    //
    // If the navigation library was being used this would happen automatically, so this
    // workaround can be removed when that change happens.
    private val viewModel: TimelineViewModel<out Any, out TimelineRepository<out Any>> by lazy {
        if (timeline == Timeline.Home) {
            viewModels<CachedTimelineViewModel>(
                extrasProducer = {
                    defaultViewModelCreationExtras.withCreationCallback<CachedTimelineViewModel.Factory> {
                        it.create(timeline)
                    }
                },
            ).value
        } else {
            viewModels<NetworkTimelineViewModel>(
                extrasProducer = {
                    defaultViewModelCreationExtras.withCreationCallback<NetworkTimelineViewModel.Factory> {
                        it.create(timeline)
                    }
                },
            ).value
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private val timeline: Timeline by unsafeLazy { requireArguments().getParcelable(ARG_KIND)!! }

    private lateinit var adapter: TimelinePagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    /** The active snackbar, if any */
    // TODO: This shouldn't be necessary, the snackbar should dismiss itself if the layout
    // changes. It doesn't, because the CoordinatorLayout is in the activity, not the fragment.
    // I think the correct fix is to include the FAB in each fragment layout that needs it,
    // ensuring that the outermost fragment view is a CoordinatorLayout. That will auto-dismiss
    // the snackbar when the fragment is paused.
    private var snackbar: Snackbar? = null

    private val isSwipeToRefreshEnabled by unsafeLazy { requireArguments().getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true) }

    override val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

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
        adapter.notifyItemRangeChanged(
            0,
            adapter.itemCount,
            listOf(StatusViewDataDiffCallback.Payload.CREATED),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.accept(InfallibleUiAction.LoadPachliAccount(pachliAccountId))
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

        val setStatusContent = if (viewModel.statusDisplayOptions.value.renderMarkdown) {
            SetMarkdownContent(requireContext())
        } else {
            SetMastodonHtmlContent
        }

        adapter = TimelinePagingAdapter(Glide.with(this), setStatusContent, this, viewModel.statusDisplayOptions.value)

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
            // Position restoration happens at CREATED so it runs once at the Fragment
            // start, not every time the Fragment resumes / becomes visible.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    if (timeline.remoteKeyTimelineId == null) {
                        // Reading position is not restored. Jump to the top of the list
                        // when prepending completes.
                        adapter.postPrepend {
                            Timber.d("timeline: $timeline, scrolling to 0 because null remoteKeyTimelineId")
                            binding.recyclerView.scrollToPosition(0)
                        }
                    } else {
                        // Reading position is restored. Can't use loadStateFlow here as it
                        // generally updates too late -- by the time the refresh is emitted
                        // the prepend might have started and the UI is already updated. This
                        // results in jerky scrolling around the list as the position is
                        // restored.
                        //
                        // However, onPagesUpdateFlow appears to update sooner, so the position
                        // of the list can be set before it is first displayed, and the UI
                        // updates smoothly.
                        viewModel.initialRefreshStatusId.combine(adapter.onPagesUpdatedFlow) { statusId, _ -> Pair(statusId, adapter.snapshot()) }
                            // Logging shows that some initial updated pages may be empty or may not
                            // contain the ID we expect (no idea how that can happen). Filter those
                            // out.
                            .onEach { (statusId, _) -> Timber.d("timeline: $timeline, Checking contains $statusId") }
                            .map { (statusId, snapshot) -> Triple(statusId, snapshot, snapshot.indexOfFirst { it?.id == statusId }) }
                            .filter { (_, _, index) -> index != -1 }
                            // Only going to restore the position manually once over the lifetime of this
                            // fragment. Other position restoration is handled by the RecyclerView.
                            .take(1)
                            .collect { (statusId, snapshot, index) ->
                                Timber.d("timeline: $timeline, snapshot.size: ${snapshot.size}")
                                Timber.d("timeline: $timeline, snapshot.items.size: ${snapshot.items.size}")
                                Timber.d("timeline: $timeline, snapshot.items.first id: ${snapshot.items.firstOrNull()?.id}")
                                Timber.d("timeline: $timeline, snapshot.items.last  id: ${snapshot.items.lastOrNull()?.id}")
                                Timber.d("timeline: $timeline, placeholdersBefore: ${snapshot.placeholdersBefore}")

                                // If the recyclerview is using a ConcatAdapter to display a progress spinner while
                                // loads are happening, and a load is happening, then we need to offset the found
                                // position by 1 to account for it, otherwise the position is restored one item
                                // too early.
                                val offset = if (((binding.recyclerView.adapter as? ConcatAdapter)?.adapters?.firstOrNull() as? LoadStateAdapter)?.loadState is LoadState.NotLoading) {
                                    0
                                } else {
                                    1
                                }
                                Timber.d("timeline: $timeline, offset: $offset")

                                val position = index + offset
                                Timber.d("timeline: $timeline, flow: Restoring last position, statusId: $statusId, index: $index, position: $position")
                                Timber.d("timeline: $timeline, scrolling to $position because restoring position")
                                binding.recyclerView.scrollToPosition(position)
                            }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.statuses.collect {
                        adapter.submitData(it)
                    }
                }

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
                uiError.error.fmt(requireContext()),
            )
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
                if (action !is FallibleStatusAction) return@let

                adapter.snapshot()
                    .indexOfFirst { it?.id == action.statusViewData.id }
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { adapter.notifyItemChanged(it) }
            }
        }

        uiResult.onSuccess {
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
                                Timber.d("timeline: $timeline, scrolling to 0 because LoadNewest completed")
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
                viewModel.accept(InfallibleUiAction.LoadNewest(pachliAccountId))
                true
            }
            else -> false
        }
    }

    /**
     * Returns the top-most status on the screen that starts on the screen.
     *
     * If one or more statuses are fully visible then this is the first fully
     * visible status.
     *
     * Otherwise the screen is showing two statuses where the first status
     * starts off the top of the screen and the second status runs off the
     * bottom of the screen. In this case return the the second status.
     *
     * May return null if no statuses are showing.
     */
    private fun getFirstVisibleStatus() = (
        layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findLastVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
        )?.let { adapter.snapshot().getOrNull(it) }

    /**
     * Saves the ID of the first visible status as the reading position.
     *
     * Does nothing if [timeline.remoteKeyTimelineId][Timeline.remoteKeyTimelineId] is null.
     *
     * @see [getFirstVisibleStatus]
     */
    fun saveVisibleId() {
        if (timeline.remoteKeyTimelineId == null) return
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
        binding.recyclerView.applyDefaultWindowInsets()
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(pachliAccountId, binding.recyclerView, this, openUrl) { pos ->
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
                    Timber.d("scrolling up by -30px because peeking after refresh")
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
        viewModel.accept(FallibleStatusAction.Reblog(reblog, viewData))
    }

    override fun onFavourite(viewData: StatusViewData, favourite: Boolean) {
        viewModel.accept(FallibleStatusAction.Favourite(favourite, viewData))
    }

    override fun onBookmark(viewData: StatusViewData, bookmark: Boolean) {
        viewModel.accept(FallibleStatusAction.Bookmark(bookmark, viewData))
    }

    override fun onVoteInPoll(viewData: StatusViewData, poll: Poll, choices: List<Int>) {
        viewModel.accept(FallibleStatusAction.VoteInPoll(poll, choices, viewData))
    }

    override fun clearContentFilter(viewData: StatusViewData) {
        viewModel.clearWarning(viewData)
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        startActivityWithTransition(
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
        viewModel.onChangeExpanded(expanded, viewData)
    }

    override fun onAttachmentDisplayActionChange(viewData: StatusViewData, newDecision: AttachmentDisplayAction) {
        viewModel.onChangeAttachmentDisplayAction(viewData, newDecision)
    }

    override fun onShowReblogs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.REBLOGGED, statusId)
        startActivityWithDefaultTransition(intent)
    }

    override fun onShowFavs(statusId: String) {
        val intent = AccountListActivityIntent(requireContext(), pachliAccountId, AccountListActivityIntent.Kind.FAVOURITED, statusId)
        startActivityWithDefaultTransition(intent)
    }

    override fun onContentCollapsedChange(viewData: StatusViewData, isCollapsed: Boolean) {
        viewModel.onContentCollapsed(isCollapsed, viewData)
    }

    override fun onTranslate(viewData: StatusViewData) {
        viewModel.accept(FallibleStatusAction.Translate(viewData))
    }

    override fun onTranslateUndo(viewData: StatusViewData) {
        viewModel.accept(InfallibleStatusAction.TranslateUndo(viewData))
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
        super.viewThread(status.actionableId, status.url)
    }

    override fun onViewTag(tag: String) {
        // If already viewing a tag page, then ignore any request to view that tag again.
        if ((timeline as? Timeline.Hashtags)?.tags?.contains(tag) == true) return

        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        // Ignore request to view the account page we're currently viewing
        (timeline as? Timeline.User)?.let { if (it.id == id) return }

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
                    Timber.d("timeline: $timeline, scroll to 0 because onReselect")
                    binding.recyclerView.scrollToPosition(0)
                    binding.recyclerView.stopScroll()
                    saveVisibleId()
                }

                TabTapBehaviour.JUMP_TO_NEWEST -> viewModel.accept(InfallibleUiAction.LoadNewest(pachliAccountId))
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
