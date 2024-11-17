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

package app.pachli.components.notifications

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.components.timeline.TimelineLoadStateAdapter
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.model.FilterAction
import app.pachli.core.navigation.AttachmentViewData.Companion.list
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.TabTapBehaviour
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.FragmentTimelineNotificationsBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.AccountActionListener
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import app.pachli.util.UserRefreshState
import app.pachli.util.asRefreshState
import app.pachli.viewdata.NotificationViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.properties.Delegates
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
class NotificationsFragment :
    SFragment<NotificationViewData>(),
    StatusActionListener<NotificationViewData>,
    NotificationActionListener,
    AccountActionListener,
    OnRefreshListener,
    MenuProvider,
    ReselectableFragment {

    private val viewModel: NotificationsViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<NotificationsViewModel.Factory> { factory ->
                factory.create(requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID))
            }
        },
    )

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var adapter: NotificationsPagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    private var talkBackWasEnabled = false

    override var pachliAccountId by Delegates.notNull<Long>()

    // Update post timestamps
    private val updateTimestampFlow = flow {
        while (true) {
            delay(60000)
            emit(Unit)
        }
    }.onEach {
        adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)

        adapter = NotificationsPagingAdapter(
            notificationDiffCallback,
            pachliAccountId,
            statusActionListener = this@NotificationsFragment,
            notificationActionListener = this@NotificationsFragment,
            accountActionListener = this@NotificationsFragment,
            statusDisplayOptions = viewModel.statusDisplayOptions.value,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_timeline_notifications, container, false)
    }

    private fun confirmClearNotifications() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.notification_clear_text)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> clearNotifications() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(pachliAccountId, binding.recyclerView, this) { pos: Int ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            },
        )
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

        val saveIdListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != SCROLL_STATE_IDLE) return

                // Save the ID of the first notification visible in the list, so the user's
                // reading position is always restorable.
                layoutManager.findFirstVisibleItemPosition().takeIf { it != NO_POSITION }?.let { position ->
                    adapter.snapshot().getOrNull(position)?.id?.let { id ->
                        viewModel.accept(InfallibleUiAction.SaveVisibleId(pachliAccountId, visibleId = id))
                    }
                }
            }
        }
        binding.recyclerView.addOnScrollListener(saveIdListener)

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

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = TimelineLoadStateAdapter { adapter.retry() },
            footer = TimelineLoadStateAdapter { adapter.retry() },
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagingData.collectLatest { pagingData ->
                        Timber.d("Submitting data to adapter")
                        adapter.submitData(pagingData)
                    }
                }

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
                launch {
                    viewModel.uiError.collect { error ->
                        val message = getString(
                            error.message,
                            error.throwable.getErrorString(requireContext()),
                        )
                        Timber.d(error.throwable, message)
                        val snackbar = Snackbar.make(
                            // Without this the FAB will not move out of the way
                            (activity as ActionButtonActivity).actionButton ?: binding.root,
                            message,
                            Snackbar.LENGTH_INDEFINITE,
                        )
                        error.action?.let { action ->
                            snackbar.setAction(app.pachli.core.ui.R.string.action_retry) {
                                viewModel.accept(action)
                            }
                        }
                        snackbar.show()

                        // The status view has pre-emptively updated its state to show
                        // that the action succeeded. Since it hasn't, re-bind the view
                        // to show the correct data.
                        error.action?.let { action ->
                            if (action !is StatusAction) return@let

                            val position = adapter.snapshot().indexOfFirst {
                                it?.statusViewData?.status?.id == action.statusViewData.id
                            }
                            if (position != NO_POSITION) {
                                adapter.notifyItemChanged(position)
                            }
                        }
                    }
                }

                // Show successful notification action as brief snackbars, so the
                // user is clear the action has happened.
                launch {
                    viewModel.uiSuccess
                        .filterIsInstance<NotificationActionSuccess>()
                        .collect {
                            Snackbar.make(
                                (activity as ActionButtonActivity).actionButton ?: binding.root,
                                getString(it.msg),
                                Snackbar.LENGTH_SHORT,
                            ).show()

                            when (it) {
                                // The follow request is no longer valid, refresh the adapter to
                                // remove it.
                                is NotificationActionSuccess.AcceptFollowRequest,
                                is NotificationActionSuccess.RejectFollowRequest,
                                -> adapter.refresh()
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
                                .firstOrNull { notificationViewData ->
                                    notificationViewData.value?.statusViewData?.status?.id ==
                                        it.action.statusViewData.id
                                } ?: return@collect

                            val statusViewData =
                                indexedViewData.value?.statusViewData ?: return@collect

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
                            }
                            indexedViewData.value?.statusViewData = statusViewData.copy(
                                status = status,
                            )

                            adapter.notifyItemChanged(indexedViewData.index)
                        }
                }

                // Refresh adapter on mutes and blocks
                launch {
                    viewModel.uiSuccess.collectLatest {
                        when (it) {
                            is UiSuccess.Block, is UiSuccess.Mute, is UiSuccess.MuteConversation ->
                                adapter.refresh()

                            else -> {
                                /* nothing to do */
                            }
                        }
                    }
                }

                // Collect the uiState. Nothing is done with it, but if you don't collect it then
                // accessing viewModel.uiState.value (e.g., when the filter dialog is created)
                // returns an empty object.
                launch { viewModel.uiState.collect() }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically update the timestamp in the list gui elements.
                launch {
                    viewModel.statusDisplayOptions
                        .collectLatest {
                            // NOTE this this also triggered (emitted?) on resume.

                            adapter.statusDisplayOptions = it
                            adapter.notifyItemRangeChanged(0, adapter.itemCount, null)

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
                    /** True if the previous prepend resulted in a peek, false otherwise */
                    var peeked = false

                    /** ID of the item that was first in the adapter before the refresh */
                    var previousFirstId: String? = null

                    refreshState.collect {
                        when (it) {
                            // Refresh has started, reset peeked, and save the ID of the first item
                            // in the adapter
                            UserRefreshState.ACTIVE -> {
                                peeked = false
                                if (adapter.itemCount != 0) previousFirstId = adapter.peek(0)?.id
                            }

                            // Refresh has finished, pages are being prepended.
                            UserRefreshState.COMPLETE -> {
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

                            else -> {
                                /* nothing to do */
                            }
                        }
                    }
                }

                // Manage the display of progress bars. Rather than hide them as soon as the
                // Refresh portion completes, hide them when then first Prepend completes. This
                // is a better signal to the user that it is now possible to scroll up and see
                // new content.
                launch {
                    refreshState.collect {
                        when (it) {
                            UserRefreshState.ACTIVE -> {
                                if (adapter.itemCount == 0 && !binding.swipeRefreshLayout.isRefreshing) {
                                    binding.progressBar.show()
                                }
                            }

                            UserRefreshState.COMPLETE, UserRefreshState.ERROR -> {
                                binding.progressBar.hide()
                                binding.swipeRefreshLayout.isRefreshing = false
                            }

                            else -> {
                                /* nothing to do */
                            }
                        }
                    }
                }

                // Update the UI from the loadState
                adapter.loadStateFlow
                    .collect { loadState ->
                        binding.statusView.hide()
                        if (loadState.refresh is LoadState.NotLoading) {
                            if (adapter.itemCount == 0) {
                                binding.statusView.setup(BackgroundMessage.Empty())
                                binding.recyclerView.hide()
                                binding.statusView.show()
                            } else {
                                binding.statusView.hide()
                            }
                        }

                        if (loadState.refresh is LoadState.Error) {
                            binding.statusView.setup((loadState.refresh as LoadState.Error).error) { adapter.retry() }
                            binding.recyclerView.hide()
                            binding.statusView.show()
                        }
                    }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_notifications, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = makeIcon(requireContext(), GoogleMaterial.Icon.gmd_refresh, IconicsSize.dp(20))
        }
        menu.findItem(R.id.action_edit_notification_filter)?.apply {
            icon = makeIcon(requireContext(), GoogleMaterial.Icon.gmd_tune, IconicsSize.dp(20))
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                true
            }
            R.id.load_newest -> {
                viewModel.accept(InfallibleUiAction.LoadNewest)
                true
            }
            R.id.action_edit_notification_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_clear_notifications -> {
                confirmClearNotifications()
                true
            }
            else -> false
        }
    }

    override fun onRefresh() {
        binding.progressBar.isVisible = false
        adapter.refresh()
        clearNotificationsForAccount(requireContext(), pachliAccountId)
    }

    override fun onPause() {
        super.onPause()

        // Save the ID of the first notification visible in the list
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position >= 0) {
            adapter.snapshot().getOrNull(position)?.id?.let { id ->
                viewModel.accept(InfallibleUiAction.SaveVisibleId(pachliAccountId, visibleId = id))
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val a11yManager = ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)
        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        clearNotificationsForAccount(requireContext(), pachliAccountId)
    }

    override fun onReply(pachliAccountId: Long, viewData: NotificationViewData) {
        super.reply(pachliAccountId, viewData.statusViewData!!.actionable)
    }

    override fun onReblog(viewData: NotificationViewData, reblog: Boolean) {
        viewModel.accept(StatusAction.Reblog(reblog, viewData.statusViewData!!))
    }

    override fun onFavourite(viewData: NotificationViewData, favourite: Boolean) {
        viewModel.accept(StatusAction.Favourite(favourite, viewData.statusViewData!!))
    }

    override fun onBookmark(viewData: NotificationViewData, bookmark: Boolean) {
        viewModel.accept(StatusAction.Bookmark(bookmark, viewData.statusViewData!!))
    }

    override fun onVoteInPoll(viewData: NotificationViewData, poll: Poll, choices: List<Int>) {
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, viewData.statusViewData!!))
    }

    override fun onMore(view: View, viewData: NotificationViewData) {
        super.more(view, viewData)
    }

    override fun onViewMedia(viewData: NotificationViewData, attachmentIndex: Int, view: View?) {
        super.viewMedia(
            viewData.statusViewData!!.status.account.username,
            attachmentIndex,
            list(viewData.statusViewData!!.status, viewModel.statusDisplayOptions.value.showSensitiveMedia),
            view,
        )
    }

    override fun onViewThread(status: Status) {
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onOpenReblog(status: Status) {
        onViewAccount(status.account.id)
    }

    override fun onExpandedChange(pachliAccountId: Long, viewData: NotificationViewData, expanded: Boolean) {
        adapter.snapshot().withIndex()
            .filter {
                it.value?.statusViewData?.actionableId == viewData.statusViewData!!.actionableId
            }
            .map {
                it.value?.statusViewData = it.value?.statusViewData?.copy(isExpanded = expanded)
                adapter.notifyItemChanged(it.index)
            }
    }

    override fun onContentHiddenChange(pachliAccountId: Long, viewData: NotificationViewData, isShowing: Boolean) {
        adapter.snapshot().withIndex()
            .filter {
                it.value?.statusViewData?.actionableId == viewData.statusViewData!!.actionableId
            }
            .map {
                it.value?.statusViewData = it.value?.statusViewData?.copy(isShowingContent = isShowing)
                adapter.notifyItemChanged(it.index)
            }
    }

    override fun onContentCollapsedChange(pachliAccountId: Long, viewData: NotificationViewData, isCollapsed: Boolean) {
        adapter.snapshot().withIndex().filter {
            it.value?.statusViewData?.actionableId == viewData.statusViewData!!.actionableId
        }
            .map {
                it.value?.statusViewData = it.value?.statusViewData?.copy(isCollapsed = isCollapsed)
                adapter.notifyItemChanged(it.index)
            }
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        requireActivity().startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onNotificationContentCollapsedChange(
        pachliAccountId: Long,
        isCollapsed: Boolean,
        viewData: NotificationViewData,
    ) {
        onContentCollapsedChange(pachliAccountId, viewData, isCollapsed)
    }

    override fun clearWarningAction(pachliAccountId: Long, viewData: NotificationViewData) {
        adapter.snapshot().withIndex().filter { it.value?.statusViewData?.actionableId == viewData.statusViewData!!.actionableId }
            .map {
                it.value?.statusViewData = it.value?.statusViewData?.copy(
                    contentFilterAction = FilterAction.NONE,
                )
                adapter.notifyItemChanged(it.index)
            }
    }

    private fun clearNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.isVisible = false
        viewModel.accept(FallibleUiAction.ClearNotifications)
    }

    private fun showFilterDialog() {
        FilterDialogFragment(viewModel.uiState.value.activeFilter) { filter ->
            if (viewModel.uiState.value.activeFilter != filter) {
                viewModel.accept(InfallibleUiAction.ApplyFilter(pachliAccountId, filter))
            }
        }.show(parentFragmentManager, "dialogFilter")
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        adapter.refresh()
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        adapter.refresh()
    }

    override fun onRespondToFollowRequest(accept: Boolean, accountId: String, position: Int) {
        if (accept) {
            viewModel.accept(NotificationAction.AcceptFollowRequest(accountId))
        } else {
            viewModel.accept(NotificationAction.RejectFollowRequest(accountId))
        }
    }

    override fun onViewThreadForStatus(status: Status) {
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onViewReport(reportId: String) {
        requireContext().openLink(
            "https://${viewModel.account.domain}/admin/reports/$reportId",
        )
    }

    override fun removeItem(viewData: NotificationViewData) {
        // Empty -- this fragment doesn't remove items
    }

    override fun onReselect() {
        if (isAdded) {
            when (viewModel.uiState.value.tabTapBehaviour) {
                TabTapBehaviour.JUMP_TO_NEXT_PAGE -> layoutManager.scrollToPosition(0)
                TabTapBehaviour.JUMP_TO_NEWEST -> viewModel.accept(InfallibleUiAction.LoadNewest)
            }
        }
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): NotificationsFragment {
            val fragment = NotificationsFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }

        private val notificationDiffCallback: DiffUtil.ItemCallback<NotificationViewData> =
            object : DiffUtil.ItemCallback<NotificationViewData>() {
                override fun areItemsTheSame(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData,
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData,
                ): Boolean {
                    return false
                }

                override fun getChangePayload(
                    oldItem: NotificationViewData,
                    newItem: NotificationViewData,
                ): Any? {
                    return if (oldItem == newItem) {
                        //  If items are equal - update timestamp only
                        listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                    } else {
                        // If items are different - update a whole view holder
                        null
                    }
                }
            }
    }
}

class FilterDialogFragment(
    private val activeFilter: Set<Notification.Type>,
    private val listener: ((filter: Set<Notification.Type>) -> Unit),
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val items = Notification.Type.visibleTypes.map { getString(it.uiString()) }.toTypedArray()
        val checkedItems = Notification.Type.visibleTypes.map {
            !activeFilter.contains(it)
        }.toBooleanArray()

        val builder = AlertDialog.Builder(context)
            .setTitle(R.string.notifications_apply_filter)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val excludes: MutableSet<Notification.Type> = HashSet()
                for (i in Notification.Type.visibleTypes.indices) {
                    if (!checkedItems[i]) excludes.add(Notification.Type.visibleTypes[i])
                }
                listener(excludes)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
        return builder.create()
    }
}

@StringRes
fun Notification.Type.uiString(): Int = when (this) {
    Notification.Type.UNKNOWN -> R.string.notification_unknown_name
    Notification.Type.MENTION -> R.string.notification_mention_name
    Notification.Type.REBLOG -> R.string.notification_boost_name
    Notification.Type.FAVOURITE -> R.string.notification_favourite_name
    Notification.Type.FOLLOW -> R.string.notification_follow_name
    Notification.Type.FOLLOW_REQUEST -> R.string.notification_follow_request_name
    Notification.Type.POLL -> R.string.notification_poll_name
    Notification.Type.STATUS -> R.string.notification_subscription_name
    Notification.Type.SIGN_UP -> R.string.notification_sign_up_name
    Notification.Type.UPDATE -> R.string.notification_update_name
    Notification.Type.REPORT -> R.string.notification_report_name
    Notification.Type.SEVERED_RELATIONSHIPS -> R.string.notification_severed_relationships_name
}
