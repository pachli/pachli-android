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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

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
import app.pachli.R
import app.pachli.StatusListActivity
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.appstore.EventHub
import app.pachli.components.account.AccountActivity
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.ReselectableFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.settings.PrefKeys
import app.pachli.util.SharedPreferencesRepository
import app.pachli.util.StatusDisplayOptionsRepository
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.viewBinding
import app.pachli.util.visible
import app.pachli.viewdata.AttachmentViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@AndroidEntryPoint
class ConversationsFragment :
    SFragment(),
    StatusActionListener,
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

    private var showFabWhileScrolling = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            val statusDisplayOptions = statusDisplayOptionsRepository.flow.value

            adapter = ConversationAdapter(statusDisplayOptions, this@ConversationsFragment)

            setupRecyclerView()

            initSwipeToRefresh()

            adapter.addLoadStateListener { loadState ->
                if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                binding.statusView.hide()
                binding.progressBar.hide()

                if (adapter.itemCount == 0) {
                    when (loadState.refresh) {
                        is LoadState.NotLoading -> {
                            if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                                binding.statusView.show()
                                binding.statusView.setup(
                                    R.drawable.elephant_friend_empty,
                                    R.string.message_empty,
                                    null,
                                )
                            }
                        }

                        is LoadState.Error -> {
                            binding.statusView.show()
                            binding.statusView.setup((loadState.refresh as LoadState.Error).error) { refreshContent() }
                        }

                        is LoadState.Loading -> {
                            binding.progressBar.show()
                        }
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

            showFabWhileScrolling = !sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false)
            binding.recyclerView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    val actionButton = (activity as? ActionButtonActivity)?.actionButton

                    override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                        actionButton?.visible(showFabWhileScrolling || dy == 0)
                    }
                },
            )
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
                    delay(1.toDuration(DurationUnit.MINUTES))
                }
            }
        }

        lifecycleScope.launch {
            sharedPreferencesRepository.changes.collect { onPreferenceChanged(it) }
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
                binding.swipeRefreshLayout.isRefreshing = true
                refreshContent()
                true
            }
            else -> false
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.adapter = adapter.withLoadStateFooter(ConversationLoadStateAdapter(adapter::retry))
    }

    private fun refreshContent() {
        adapter.refresh()
    }

    private fun initSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener { refreshContent() }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewModel.favourite(favourite, conversation)
        }
    }

    override fun onBookmark(favourite: Boolean, position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewModel.bookmark(favourite, conversation)
        }
    }

    override fun onMore(view: View, position: Int) {
        adapter.peek(position)?.let { conversation ->

            val popup = PopupMenu(requireContext(), view)
            popup.inflate(R.menu.conversation_more)

            if (conversation.lastStatus.status.muted == true) {
                popup.menu.removeItem(R.id.status_mute_conversation)
            } else {
                popup.menu.removeItem(R.id.status_unmute_conversation)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.status_mute_conversation -> viewModel.muteConversation(conversation)
                    R.id.status_unmute_conversation -> viewModel.muteConversation(conversation)
                    R.id.conversation_delete -> deleteConversation(conversation)
                }
                true
            }
            popup.show()
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        adapter.peek(position)?.let { conversation ->
            viewMedia(attachmentIndex, AttachmentViewData.list(conversation.lastStatus.status), view)
        }
    }

    override fun onViewThread(position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewThread(conversation.lastStatus.id, conversation.lastStatus.status.url)
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in conversations
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewModel.expandHiddenStatus(expanded, conversation)
        }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewModel.showContent(isShowing, conversation)
        }
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        adapter.peek(position)?.let { conversation ->
            viewModel.collapseLongStatus(isCollapsed, conversation)
        }
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivity.getIntent(requireContext(), id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivity.newHashtagIntent(requireContext(), tag)
        startActivity(intent)
    }

    override fun removeItem(position: Int) {
        // not needed
    }

    override fun onReply(position: Int) {
        adapter.peek(position)?.let { conversation ->
            reply(conversation.lastStatus.status)
        }
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        adapter.peek(position)?.let { conversation ->
            viewModel.voteInPoll(choices, conversation)
        }
    }

    override fun clearWarningAction(position: Int) {
    }

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
            PrefKeys.FAB_HIDE -> {
                showFabWhileScrolling = sharedPreferencesRepository.getBoolean(PrefKeys.FAB_HIDE, false)
            }
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
        fun newInstance() = ConversationsFragment()
    }
}
