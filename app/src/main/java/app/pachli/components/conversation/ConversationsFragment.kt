/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.conversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.adapter.StatusViewDataDiffCallback
import app.pachli.components.preference.accountfilters.AccountConversationFiltersPreferenceDialogFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.ConversationViewData
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.eventhub.EventHub
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.IStatus
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.SetMarkdownContent
import app.pachli.core.ui.SetMastodonHtmlContent
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.util.ListStatusAccessibilityDelegate
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Actions taken from the broader UI (which can include actions triggered by the
 * per-Conversation UI if not specific to that conversation).
 */
sealed interface UiAction {
    /**
     * Called when the user clicks "Edit filter" from a status filtered
     * by the account.
     */
    data class EditAccountFilter(val pachliAccountId: Long) : UiAction
}

/**
 * Actions taken from an individual [app.pachli.core.data.model.ConversationViewData].
 */
internal sealed interface ConversationAction : UiAction {
    /**
     * Called when the user clicks "Show anyway" to see a status filtered
     * by the account.
     */
    data class OverrideAccountFilter(
        val pachliAccountId: Long,
        val conversationId: String,
        val accountFilterDecision: AccountFilterDecision,
    ) : ConversationAction

    /** Clear the content filter. */
    data class ClearContentFilter(
        val pachliAccountId: Long,
        val statusId: String,
    ) : ConversationAction
}

@AndroidEntryPoint
class ConversationsFragment :
    SFragment<ConversationViewData>(),
    OnRefreshListener,
    ReselectableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val viewModel: ConversationsViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ConversationsViewModel.Factory> { factory ->
                factory.create(requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID))
            }
        },
    )

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emit them in to [uiAction]. */
    private val accept: (UiAction) -> Unit = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    private lateinit var adapter: ConversationAdapter

    override var pachliAccountId by Delegates.notNull<Long>()

    private val glide by unsafeLazy { Glide.with(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.applyDefaultWindowInsets()

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            val statusDisplayOptions = statusDisplayOptionsRepository.flow.value

            val setStatusContent = if (statusDisplayOptions.renderMarkdown) {
                SetMarkdownContent(requireContext())
            } else {
                SetMastodonHtmlContent
            }

            adapter = ConversationAdapter(glide, statusDisplayOptions, setStatusContent, this@ConversationsFragment, accept)

            setupRecyclerView()

            initSwipeToRefresh()

            adapter.addLoadStateListener { loadState ->
                Timber.d("loadState: $loadState")

                if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                binding.statusView.hide()

                if (adapter.itemCount == 0) {
                    when (loadState.refresh) {
                        is LoadState.NotLoading -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                                binding.statusView.show()
                                binding.statusView.setup(BackgroundMessage.Empty())
                            }
                        }

                        is LoadState.Error -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            binding.statusView.show()
                            binding.statusView.setup((loadState.refresh as LoadState.Error).error) {
                                refreshContent()
                            }
                        }

                        is LoadState.Loading -> { /* nothing to do */ }
                    }
                }
            }

            adapter.registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        if (positionStart == 0 && adapter.itemCount != itemCount) {
                            binding.recyclerView.post {
                                if (getView() != null) {
                                    binding.recyclerView.scrollBy(
                                        0,
                                        dpToPx(-30f, requireContext().resources.displayMetrics).toInt(),
                                    )
                                }
                            }
                        }
                    }
                },
            )

            (activity as? ActionButtonActivity)?.actionButton?.let { actionButton ->
                actionButton.show()

                val actionButtonScrollListener = ActionButtonScrollListener(actionButton)
                binding.recyclerView.addOnScrollListener(actionButtonScrollListener)

                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        viewModel.showFabWhileScrolling.collect {
                            actionButtonScrollListener.showActionButtonWhileScrolling = it
                        }
                    }
                }
            }
        }

        bind()
    }

    /** Binds data to the UI. */
    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { uiAction.throttleFirst().collect(::bindUiAction) }

                launch { viewModel.conversationFlow.collectLatest { adapter.submitData(it) } }

                launch {
                    val useAbsoluteTime = sharedPreferencesRepository.useAbsoluteTime
                    while (!useAbsoluteTime) {
                        delay(1.minutes)
                        adapter.notifyItemRangeChanged(
                            0,
                            adapter.itemCount,
                            listOf(StatusViewDataDiffCallback.Payload.CREATED),
                        )
                    }
                }

                launch { sharedPreferencesRepository.changes.filterNotNull().collect { onPreferenceChanged(it) } }
            }
        }
    }

    /** Process user actions. */
    private fun bindUiAction(uiAction: UiAction) {
        when (uiAction) {
            is ConversationAction.OverrideAccountFilter -> viewModel.accept(uiAction)
            is UiAction.EditAccountFilter -> AccountConversationFiltersPreferenceDialogFragment.newInstance(pachliAccountId)
                .show(parentFragmentManager, null)

            is ConversationAction.ClearContentFilter -> {
                // Do nothing. This action isn't sent from FilterConversationStatusViewHolder,
                // the listener callback `clearContentFilter` (see later in this file) is
                // called instead.
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                refreshContent()
                true
            }
            else -> false
        }
    }

    private fun setupRecyclerView() {
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
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.adapter = adapter.withLoadStateFooter(ConversationLoadStateAdapter(adapter::retry))
    }

    private fun initSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    /** Refresh the displayed content, as if the user had swiped on the SwipeRefreshLayout */
    private fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    /**
     * Listener for the user swiping on the SwipeRefreshLayout. The SwipeRefreshLayout has
     * handled displaying the animated spinner.
     */
    override fun onRefresh() {
        binding.statusView.hide()
        adapter.refresh()
    }

    override fun onReblog(viewData: IStatusViewData, reblog: Boolean) {
        // its impossible to reblog private messages
    }

    // Quoting conversations is not supported.
    override fun onQuote(viewData: IStatusViewData) = Unit

    override fun onFavourite(viewData: IStatusViewData, favourite: Boolean) {
        viewModel.favourite(favourite, viewData.actionableId)
    }

    override fun onBookmark(viewData: IStatusViewData, bookmark: Boolean) {
        viewModel.bookmark(bookmark, viewData.actionableId)
    }

    override fun onPrepareMoreMenu(menu: Menu, viewData: IStatusViewData) {
        menu.findItem(R.id.conversation_delete).isVisible = true
    }

    override fun onMoreMenuItemClick(item: MenuItem, viewData: IStatusViewData): Boolean {
        return when (item.itemId) {
            R.id.conversation_delete -> {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.dialog_delete_conversation_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.remove(viewData as ConversationViewData)
                    }
                    .show()
                true
            }

            else -> super.onMoreMenuItemClick(item, viewData)
        }
    }

    override fun onViewAttachment(view: View?, viewData: IStatusViewData, attachmentIndex: Int) {
        viewMedia(
            viewData.actionable.account.username,
            attachmentIndex,
            AttachmentViewData.list(viewData.status),
            view,
        )
    }

    override fun onViewThread(status: Status) {
        viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onOpenReblog(status: IStatus) {
        // there are no reblogs in conversations
    }

    override fun onExpandedChange(viewData: IStatusViewData, expanded: Boolean) {
        viewModel.expandHiddenStatus(viewData.pachliAccountId, expanded, viewData.actionableId)
    }

    override fun onAttachmentDisplayActionChange(viewData: IStatusViewData, newAction: AttachmentDisplayAction) {
        viewModel.changeAttachmentDisplayAction(viewData.pachliAccountId, viewData.actionableId, newAction)
    }

    override fun onContentCollapsedChange(viewData: IStatusViewData, isCollapsed: Boolean) {
        viewModel.collapseLongStatus(viewData.pachliAccountId, isCollapsed, viewData.actionableId)
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivityIntent(requireContext(), pachliAccountId, id)
        startActivityWithDefaultTransition(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    override fun removeItem(viewData: IStatusViewData) {
        // not needed
    }

    override fun onReply(viewData: IStatusViewData) {
        reply(viewData.pachliAccountId, viewData.actionable)
    }

    override fun onVoteInPoll(viewData: IStatusViewData, poll: Poll, choices: List<Int>) {
        viewModel.voteInPoll(choices, viewData.actionableId, poll.id)
    }

    override fun clearContentFilter(viewData: IStatusViewData) {
        if (viewData !is ConversationViewData) return

        viewModel.accept(
            ConversationAction.ClearContentFilter(
                viewData.pachliAccountId,
                viewData.conversationId,
            ),
        )
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    fun onConversationDelete(viewData: ConversationViewData) {
        if (viewData !is ConversationViewData) return

        AlertDialog.Builder(requireContext())
            .setMessage(R.string.dialog_delete_conversation_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.remove(viewData)
            }
            .show()
    }

    override fun onTranslate(viewData: IStatusViewData) {
        viewModel.translate(viewData)
    }

    override fun onTranslateUndo(viewData: IStatusViewData) {
        viewModel.translateUndo(viewData)
    }

    private fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): ConversationsFragment {
            return ConversationsFragment().apply {
                arguments = Bundle(1).apply {
                    putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
                }
            }
        }
    }
}
