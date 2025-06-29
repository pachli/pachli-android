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
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.components.viewthread.edits.ViewEditsFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.model.AttachmentBlurDecision
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.SetMarkdownContent
import app.pachli.core.ui.SetMastodonHtmlContent
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentViewThreadBinding
import app.pachli.fragment.SFragment
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.ListStatusAccessibilityDelegate
import com.bumptech.glide.Glide
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewThreadFragment :
    SFragment<StatusViewData>(),
    OnRefreshListener,
    StatusActionListener<StatusViewData>,
    MenuProvider {

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val viewModel: ViewThreadViewModel by viewModels()

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var adapter: ThreadAdapter
    private val thisThreadsStatusId by lazy { requireArguments().getString(ARG_ID)!! }

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

        val setStatusContent = if (viewModel.statusDisplayOptions.value.renderMarkdown) {
            SetMarkdownContent(requireContext())
        } else {
            SetMastodonHtmlContent
        }

        adapter = ThreadAdapter(Glide.with(this), viewModel.statusDisplayOptions.value, this, setStatusContent, openUrl)
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

        with(binding.recyclerView) {
            applyDefaultWindowInsets()
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            setAccessibilityDelegateCompat(
                ListStatusAccessibilityDelegate(
                    pachliAccountId,
                    binding.recyclerView,
                    this@ViewThreadFragment,
                    openUrl,
                ) { index -> this@ViewThreadFragment.adapter.currentList.getOrNull(index) },
            )
            addItemDecoration(
                MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
            )
            addItemDecoration(ConversationLineItemDecoration(requireContext()))
            adapter = this@ViewThreadFragment.adapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiResult.collect(::bindUiResult) }

                launch { viewModel.errors.collectLatest(::bindError) }

                launch {
                    val useAbsoluteTime = sharedPreferencesRepository.useAbsoluteTime
                    while (!useAbsoluteTime) {
                        delay(1.minutes)
                        adapter.notifyItemRangeChanged(
                            0,
                            adapter.itemCount,
                            listOf(StatusBaseViewHolder.Key.KEY_CREATED),
                        )
                    }
                }
            }
        }

        viewModel.loadThread(thisThreadsStatusId)
    }

    private fun bindUiResult(uiState: Result<ThreadUiState, ThreadError>) {
        uiState.onFailure { error ->
            binding.initialProgressBar.hide()
            binding.threadProgressBar.hide()

            revealButtonState = RevealButtonState.NO_BUTTON
            binding.swipeRefreshLayout.isRefreshing = false

            binding.recyclerView.hide()
            binding.statusView.show()

            binding.statusView.setup(error) { viewModel.retry(thisThreadsStatusId) }
        }

        uiState.onSuccess { uiState ->
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
                        return
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

                is ThreadUiState.Loaded -> {
                    if (uiState.statusViewData.none { viewData -> viewData.isDetailed }) {
                        // no detailed statuses available, e.g. because author is blocked
                        activity?.finish()
                        return
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

    private fun bindError(error: PachliError) {
        try {
            val context = view?.context ?: return
            val msg = error.fmt(context)
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                .setAction(app.pachli.core.ui.R.string.action_retry) {
                    viewModel.retry(thisThreadsStatusId)
                }
                .show()
        } catch (_: IllegalArgumentException) {
            // On rare occasions this code is running before the fragment's
            // view is connected to the parent. This causes Snackbar.make()
            // to crash.  See https://issuetracker.google.com/issues/228215869.
            // For now, swallow the exception.
        }
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
                openUrl(requireArguments().getString(ARG_URL)!!)
                true
            }
            R.id.action_refresh -> {
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun onTranslate(viewData: StatusViewData) {
        viewModel.translate(viewData)
    }

    override fun onTranslateUndo(viewData: StatusViewData) {
        viewModel.translateUndo(viewData)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_view_thread)
    }

    override fun onRefresh() {
        viewModel.refresh(thisThreadsStatusId)
    }

    override fun onReply(viewData: StatusViewData) {
        super.reply(viewData.pachliAccountId, viewData.actionable)
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
        if (thisThreadsStatusId == status.actionableId) {
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
            openUrl(url)
            return
        }
        super.onViewUrl(url)
    }

    override fun onOpenReblog(status: Status) {
        // there are no reblogs in threads
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onExpandedChange(viewData: StatusViewData, expanded: Boolean) {
        viewModel.changeExpanded(expanded, viewData)
    }

    override fun onAttachmentBlurDecisionChange(viewData: StatusViewData, newDecision: AttachmentBlurDecision) {
        viewModel.changeAttachmentBlurDecision(viewData, newDecision)
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

    override fun clearContentFilter(viewData: StatusViewData) {
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
