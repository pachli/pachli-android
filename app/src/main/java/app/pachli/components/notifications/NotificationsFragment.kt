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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.components.preference.AccountNotificationFiltersPreferencesDialogFragment
import app.pachli.components.timeline.TimelineLoadStateAdapter
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
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
import app.pachli.viewdata.NotificationViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import postPrepend
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

    private val adapter = NotificationsPagingAdapter(
        notificationDiffCallback,
        statusActionListener = this,
        notificationActionListener = this,
        accountActionListener = this,
    )

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
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

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

        (binding.recyclerView.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false

        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(pachliAccountId, binding.recyclerView, this@NotificationsFragment) { pos: Int ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            },
        )

        val saveIdListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == SCROLL_STATE_IDLE) saveVisibleId()
            }
        }
        binding.recyclerView.addOnScrollListener(saveIdListener)

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = TimelineLoadStateAdapter { adapter.retry() },
            footer = TimelineLoadStateAdapter { adapter.retry() },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Wait for the very first page load, then scroll recyclerview
                    // to the refresh key.
                    viewModel.initialRefreshKey.combine(adapter.onPagesUpdatedFlow) { key, _ -> key }
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

                launch { viewModel.pagingData.collectLatest { adapter.submitData(it) } }

                launch { viewModel.uiResult.collect(::bindUiResult) }

                // Collect the uiState. Nothing is done with it, but if you don't collect it then
                // accessing viewModel.uiState.value (e.g., when the filter dialog is created)
                // returns an empty object.
                launch { viewModel.uiState.collect() }

                // Update status display from statusDisplayOptions. If the new options request
                // relative time display collect the flow to periodically update the timestamp in the list gui elements.
                launch {
                    viewModel.statusDisplayOptions.collectLatest {
                        // NOTE this this also triggered (emitted?) on resume.

                        adapter.statusDisplayOptions = it
                        adapter.notifyItemRangeChanged(0, adapter.itemCount, null)

                        if (!it.useAbsoluteTime) {
                            updateTimestampFlow.collect()
                        }
                    }
                }

                // Update the UI from the loadState
                adapter.loadStateFlow.distinctUntilChangedBy { it.refresh }.collect { loadState ->
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
                            /* nothing */
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
            val snackbar = Snackbar.make(
                // Without this the FAB will not move out of the way
                (activity as ActionButtonActivity).actionButton ?: binding.root,
                message,
                Snackbar.LENGTH_INDEFINITE,
            )
            uiError.action?.let { action ->
                snackbar.setAction(app.pachli.core.ui.R.string.action_retry) {
                    viewModel.accept(action)
                }
            }
            snackbar.show()
        }

        uiResult.onSuccess { uiSuccess ->
            // Show successful notification action as brief snackbars, so the
            // user is clear the action has happened.
            if (uiSuccess is NotificationActionSuccess) {
                Snackbar.make(
                    (activity as ActionButtonActivity).actionButton ?: binding.root,
                    getString(uiSuccess.msg),
                    Snackbar.LENGTH_SHORT,
                ).show()

                when (uiSuccess) {
                    // The follow request is no longer valid, refresh the adapter to
                    // remove it.
                    is NotificationActionSuccess.AcceptFollowRequest,
                    is NotificationActionSuccess.RejectFollowRequest,
                    -> refreshAdapterAndScrollToVisibleId()
                }
            }

            when (uiSuccess) {
                is UiSuccess.Block, is UiSuccess.Mute, is UiSuccess.MuteConversation -> refreshAdapterAndScrollToVisibleId()

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
     * Refreshes the adapter, waits for the first page to be updated, and scrolls the
     * recyclerview to the first notification that was visible before the refresh.
     *
     * This ensures the user's position is not lost during adapter refreshes.
     */
    private fun refreshAdapterAndScrollToVisibleId() {
        getFirstVisibleNotification()?.id?.let { id ->
            viewLifecycleOwner.lifecycleScope.launch {
                adapter.onPagesUpdatedFlow.conflate().take(1).collect {
                    binding.recyclerView.scrollToPosition(
                        adapter.snapshot().items.indexOfFirst { it.id == id },
                    )
                }
            }
        }
        adapter.refresh()
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
        refreshAdapterAndScrollToVisibleId()
        clearNotificationsForAccount(requireContext(), pachliAccountId)
    }

    override fun onPause() {
        super.onPause()
        saveVisibleId()
    }

    /**
     * @returns The first visible notification. This is either the first fully visible
     * notification, or the last visible notification if no notification is fully visible.
     * May be null if there are no visible notifications.
     */
    private fun getFirstVisibleNotification() = (
        layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findLastVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
        )?.let { adapter.snapshot().getOrNull(it) }

    /**
     * Saves the ID of the notification returned by [getFirstVisibleNotification].
     */
    private fun saveVisibleId() = getFirstVisibleNotification()?.let {
        viewModel.accept(InfallibleUiAction.SaveVisibleId(pachliAccountId, it.id))
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

    override fun onReply(viewData: NotificationViewData) {
        super.reply(viewData.pachliAccountId, viewData.statusViewData!!.actionable)
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

    override fun onExpandedChange(viewData: NotificationViewData, expanded: Boolean) {
        viewModel.accept(
            InfallibleUiAction.SetExpanded(
                viewData.pachliAccountId,
                viewData.statusViewData!!,
                expanded,
            ),
        )
    }

    override fun onContentHiddenChange(
        viewData: NotificationViewData,
        isShowingContent: Boolean,
    ) {
        viewModel.accept(
            InfallibleUiAction.SetShowingContent(
                viewData.pachliAccountId,
                viewData.statusViewData!!,
                isShowingContent,
            ),
        )
    }

    override fun onContentCollapsedChange(viewData: NotificationViewData, isCollapsed: Boolean) {
        viewModel.accept(
            InfallibleUiAction.SetContentCollapsed(
                viewData.pachliAccountId,
                viewData.statusViewData!!,
                isCollapsed,
            ),
        )
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        requireActivity().startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onNotificationContentCollapsedChange(isCollapsed: Boolean, viewData: NotificationViewData) {
        onContentCollapsedChange(viewData, isCollapsed)
    }

    override fun clearContentFilter(viewData: NotificationViewData) {
        viewModel.accept(InfallibleUiAction.ClearContentFilter(viewData.pachliAccountId, viewData.id))
    }

    override fun clearAccountFilter(viewData: NotificationViewData) {
        viewModel.accept(
            InfallibleUiAction.OverrideAccountFilter(
                viewData.pachliAccountId,
                viewData.id,
                viewData.accountFilterDecision,
            ),
        )
    }

    override fun editAccountNotificationFilter() {
        AccountNotificationFiltersPreferencesDialogFragment.newInstance(pachliAccountId)
            .show(parentFragmentManager, null)
    }

    private fun clearNotifications() {
        binding.swipeRefreshLayout.isRefreshing = false
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
        refreshAdapterAndScrollToVisibleId()
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        refreshAdapterAndScrollToVisibleId()
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
