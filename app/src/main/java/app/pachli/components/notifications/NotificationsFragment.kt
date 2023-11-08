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
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
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
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Status
import app.pachli.databinding.FragmentTimelineNotificationsBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.AccountActionListener
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.ReselectableFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import app.pachli.util.UserRefreshState
import app.pachli.util.asRefreshState
import app.pachli.util.hide
import app.pachli.util.openLink
import app.pachli.util.show
import app.pachli.util.viewBinding
import app.pachli.util.visible
import app.pachli.viewdata.AttachmentViewData.Companion.list
import app.pachli.viewdata.NotificationViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationsFragment :
    SFragment(),
    StatusActionListener,
    NotificationActionListener,
    AccountActionListener,
    OnRefreshListener,
    MenuProvider,
    ReselectableFragment {

    private val viewModel: NotificationsViewModel by viewModels()

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var adapter: NotificationsPagingAdapter

    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NotificationsPagingAdapter(
            notificationDiffCallback,
            accountId = viewModel.account.accountId,
            statusActionListener = this,
            notificationActionListener = this,
            accountActionListener = this,
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
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this,
            ) { pos: Int ->
                val notification = adapter.snapshot().getOrNull(pos)
                // We support replies only for now
                if (notification is NotificationViewData) {
                    notification.statusViewData
                } else {
                    null
                }
            },
        )
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

        binding.recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                val actionButton = (activity as? ActionButtonActivity)?.actionButton

                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    actionButton?.visible(viewModel.uiState.value.showFabWhileScrolling || dy == 0)
                }

                @Suppress("SyntheticAccessor")
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    newState != SCROLL_STATE_IDLE && return

                    actionButton?.show()

                    // Save the ID of the first notification visible in the list, so the user's
                    // reading position is always restorable.
                    layoutManager.findFirstVisibleItemPosition().takeIf { it != NO_POSITION }?.let { position ->
                        adapter.snapshot().getOrNull(position)?.id?.let { id ->
                            viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = id))
                        }
                    }
                }
            },
        )

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = TimelineLoadStateAdapter { adapter.retry() },
            footer = TimelineLoadStateAdapter { adapter.retry() },
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false

        // Update post timestamps
        val updateTimestampFlow = flow {
            while (true) {
                delay(60000)
                emit(Unit)
            }
        }.onEach {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
        }

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
                        Timber.d(error.toString())
                        val message = getString(
                            error.message,
                            error.throwable.localizedMessage
                                ?: getString(R.string.ui_error_unknown),
                        )
                        val snackbar = Snackbar.make(
                            // Without this the FAB will not move out of the way
                            (activity as ActionButtonActivity).actionButton ?: binding.root,
                            message,
                            Snackbar.LENGTH_INDEFINITE,
                        ).setTextMaxLines(5)
                        error.action?.let { action ->
                            snackbar.setAction(R.string.action_retry) {
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
                            else -> { /* nothing to do */
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
                            else -> { /* nothing to do */ }
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
                            else -> { /* nothing to do */ }
                        }
                    }
                }

                // Update the UI from the loadState
                adapter.loadStateFlow
                    .collect { loadState ->
                        binding.statusView.hide()
                        if (loadState.refresh is LoadState.NotLoading) {
                            if (adapter.itemCount == 0) {
                                binding.statusView.setup(
                                    R.drawable.elephant_friend_empty,
                                    R.string.message_empty,
                                )
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
        val iconColor = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = iconColor
            }
        }
        menu.findItem(R.id.action_edit_notification_filter)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_tune).apply {
                sizeDp = 20
                colorInt = iconColor
            }
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
        clearNotificationsForAccount(requireContext(), viewModel.account)
    }

    override fun onPause() {
        super.onPause()

        // Save the ID of the first notification visible in the list
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position >= 0) {
            adapter.snapshot().getOrNull(position)?.id?.let { id ->
                viewModel.accept(InfallibleUiAction.SaveVisibleId(visibleId = id))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clearNotificationsForAccount(requireContext(), viewModel.account)
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.reply(status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Reblog(reblog, statusViewData))
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Favourite(favourite, statusViewData))
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        viewModel.accept(StatusAction.Bookmark(bookmark, statusViewData))
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val statusViewData = adapter.peek(position)?.statusViewData ?: return
        val poll = statusViewData.actionable.poll ?: return
        viewModel.accept(StatusAction.VoteInPoll(poll, choices, statusViewData))
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.more(status, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.viewMedia(
            attachmentIndex,
            list(status, viewModel.statusDisplayOptions.value.showSensitiveMedia),
            view,
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position)?.statusViewData?.status ?: return
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onOpenReblog(position: Int) {
        val account = adapter.peek(position)?.account!!
        onViewAccount(account.id)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isExpanded = expanded,
        )
        adapter.notifyItemChanged(position)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isShowingContent = isShowing,
        )
        adapter.notifyItemChanged(position)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            isCollapsed = isCollapsed,
        )
        adapter.notifyItemChanged(position)
    }

    override fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        onContentCollapsedChange(isCollapsed, position)
    }

    override fun clearWarningAction(position: Int) {
        val notificationViewData = adapter.snapshot()[position] ?: return
        notificationViewData.statusViewData = notificationViewData.statusViewData?.copy(
            filterAction = Filter.Action.NONE,
        )
        adapter.notifyItemChanged(position)
    }

    private fun clearNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.isVisible = false
        viewModel.accept(FallibleUiAction.ClearNotifications)
    }

    private fun showFilterDialog() {
        FilterDialogFragment(viewModel.uiState.value.activeFilter) { filter ->
            if (viewModel.uiState.value.activeFilter != filter) {
                viewModel.accept(InfallibleUiAction.ApplyFilter(filter))
            }
        }
            .show(parentFragmentManager, "dialogFilter")
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

    public override fun removeItem(position: Int) {
        // Empty -- this fragment doesn't remove items
    }

    override fun onReselect() {
        if (isAdded) {
            layoutManager.scrollToPosition(0)
        }
    }

    companion object {
        fun newInstance() = NotificationsFragment()

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
}
