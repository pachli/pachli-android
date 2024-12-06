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
import androidx.appcompat.widget.PopupMenu
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
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.appstore.EventHub
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationsFragment :
    SFragment<ConversationViewData>(),
    OnRefreshListener,
    StatusActionListener<ConversationViewData>,
    ReselectableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var statusDisplayOptionsRepository: StatusDisplayOptionsRepository

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val viewModel: ConversationsViewModel by viewModels()

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var adapter: ConversationAdapter

    override var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            val statusDisplayOptions = statusDisplayOptionsRepository.flow.value

            adapter = ConversationAdapter(pachliAccountId, statusDisplayOptions, this@ConversationsFragment)

            setupRecyclerView()

            initSwipeToRefresh()

            adapter.addLoadStateListener { loadState ->
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
                                        Utils.dpToPx(requireContext(), -30),
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversationFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val useAbsoluteTime = sharedPreferencesRepository.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)
                while (!useAbsoluteTime) {
                    adapter.notifyItemRangeChanged(
                        0,
                        adapter.itemCount,
                        listOf(StatusBaseViewHolder.Key.KEY_CREATED),
                    )
                    delay(1.minutes)
                }
            }
        }

        lifecycleScope.launch {
            sharedPreferencesRepository.changes.filterNotNull().collect { onPreferenceChanged(it) }
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
            ListStatusAccessibilityDelegate(pachliAccountId, binding.recyclerView, this) { pos ->
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

    override fun onReblog(viewData: ConversationViewData, reblog: Boolean) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(viewData: ConversationViewData, favourite: Boolean) {
        viewModel.favourite(favourite, viewData.lastStatus.actionableId)
    }

    override fun onBookmark(viewData: ConversationViewData, bookmark: Boolean) {
        viewModel.bookmark(bookmark, viewData.lastStatus.actionableId)
    }

    override fun onMore(view: View, viewData: ConversationViewData) {
        val status = viewData.lastStatus.status

        val popup = PopupMenu(requireContext(), view)
        popup.inflate(R.menu.conversation_more)

        if (status.muted == true) {
            popup.menu.removeItem(R.id.status_mute_conversation)
        } else {
            popup.menu.removeItem(R.id.status_unmute_conversation)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.status_mute_conversation -> viewModel.muteConversation(true, viewData.lastStatus.id)
                R.id.status_unmute_conversation -> viewModel.muteConversation(false, viewData.lastStatus.id)
                R.id.conversation_delete -> deleteConversation(viewData)
            }
            true
        }
        popup.show()
    }

    override fun onViewMedia(viewData: ConversationViewData, attachmentIndex: Int, view: View?) {
        viewMedia(
            viewData.lastStatus.actionable.account.username,
            attachmentIndex,
            AttachmentViewData.list(viewData.lastStatus.status),
            view,
        )
    }

    override fun onViewThread(status: Status) {
        viewThread(status.id, status.url)
    }

    override fun onOpenReblog(status: Status) {
        // there are no reblogs in conversations
    }

    override fun onExpandedChange(pachliAccountId: Long, viewData: ConversationViewData, expanded: Boolean) {
        viewModel.expandHiddenStatus(pachliAccountId, expanded, viewData.lastStatus.id)
    }

    override fun onContentHiddenChange(pachliAccountId: Long, viewData: ConversationViewData, isShowingContent: Boolean) {
        viewModel.showContent(pachliAccountId, isShowingContent, viewData.lastStatus.id)
    }

    override fun onContentCollapsedChange(pachliAccountId: Long, viewData: ConversationViewData, isCollapsed: Boolean) {
        viewModel.collapseLongStatus(pachliAccountId, isCollapsed, viewData.lastStatus.id)
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivityIntent(requireContext(), pachliAccountId, id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag)
        startActivity(intent)
    }

    override fun removeItem(viewData: ConversationViewData) {
        // not needed
    }

    override fun onReply(pachliAccountId: Long, viewData: ConversationViewData) {
        reply(pachliAccountId, viewData.lastStatus.actionable)
    }

    override fun onVoteInPoll(viewData: ConversationViewData, poll: Poll, choices: List<Int>) {
        viewModel.voteInPoll(choices, viewData.lastStatus.actionableId, poll.id)
    }

    override fun clearContentFilter(pachliAccountId: Long, viewData: ConversationViewData) {
    }

    // Filters don't apply in conversations
    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {}

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    private fun deleteConversation(conversation: ConversationViewData) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.dialog_delete_conversation_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.remove(conversation)
            }
            .show()
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
