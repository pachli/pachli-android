/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.components.report.adapter.ReportStatusActionListener
import app.pachli.components.report.adapter.ReportStatusesAdapter
import app.pachli.components.timeline.viewmodel.InfallibleUiAction
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.model.StatusViewDataQ
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.IStatus
import app.pachli.core.model.Poll
import app.pachli.core.model.Status
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.ui.SetMarkdownContent
import app.pachli.core.ui.SetMastodonHtmlContent
import app.pachli.databinding.FragmentReportStatusesBinding
import app.pachli.fragment.SFragment
import app.pachli.util.ListStatusAccessibilityDelegate
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * Show a list of statuses from the account the user is reporting. Allow
 * the user to choose one or more of these to include in the report.
 */
@AndroidEntryPoint
class ReportStatusesFragment :
    SFragment<StatusViewDataQ>(),
    OnRefreshListener,
    MenuProvider,
    ReportStatusActionListener {
    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportStatusesBinding::bind)

    private lateinit var adapter: ReportStatusesAdapter

    private var snackbarErrorRetry: Snackbar? = null

    override var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)

        val setStatusContent = if (viewModel.statusDisplayOptions.value.renderMarkdown) {
            SetMarkdownContent(requireContext())
        } else {
            SetMastodonHtmlContent
        }

        adapter = ReportStatusesAdapter(
            Glide.with(this),
            setStatusContent,
            viewModel.statusDisplayOptions.value,
            this@ReportStatusesFragment,
        )

        viewModel.accept(InfallibleUiAction.LoadPachliAccount(pachliAccountId))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_report_statuses, container, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        handleClicks()
        initStatusesView()
        setupSwipeRefreshLayout()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_report_statuses, menu)
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
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun onRefresh() {
        snackbarErrorRetry?.dismiss()
        adapter.refresh()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.swipeRefreshLayout.setOnRefreshListener(this)
    }

    private fun initStatusesView() {
        with(binding.recyclerView) {
            addItemDecoration(
                MaterialDividerItemDecoration(
                    requireContext(),
                    MaterialDividerItemDecoration.VERTICAL,
                ),
            )
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ReportStatusesFragment.adapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            setAccessibilityDelegateCompat(
                ListStatusAccessibilityDelegate(
                    pachliAccountId,
                    this,
                    this@ReportStatusesFragment,
                    openUrl,
                ) { index -> this@ReportStatusesFragment.adapter.snapshot().getOrNull(index) },
            )
        }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error ||
                loadState.append is LoadState.Error ||
                loadState.prepend is LoadState.Error
            ) {
                showError()
            }

            // Show only centre progress bar if refreshing. Use the top/bottom progress
            // bars for prepend/append outside of a refresh.
            val refreshing = loadState.refresh == LoadState.Loading && !binding.swipeRefreshLayout.isRefreshing
            binding.progressBarLoading.visible(refreshing)
            binding.progressBarBottom.visible(loadState.append == LoadState.Loading && !refreshing)
            binding.progressBarTop.visible(loadState.prepend == LoadState.Loading && !refreshing)

            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        // Set the initial position in the list to the reported status.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.initialRefreshStatusId.combine(adapter.onPagesUpdatedFlow) { statusId, _ -> Pair(statusId, adapter.snapshot()) }
                        .map { (statusId, snapshot) -> snapshot.indexOfFirst { it?.statusId == statusId } }
                        .filter { it != -1 }
                        .take(1)
                        .collect { binding.recyclerView.scrollToPosition(it) }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.statuses.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }
    }

    private fun showError() {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make(binding.swipeRefreshLayout, R.string.failed_fetch_posts, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(app.pachli.core.ui.R.string.action_retry) {
                adapter.retry()
            }
            snackbarErrorRetry?.show()
        }
    }

    private fun handleClicks() {
        binding.buttonCancel.setOnClickListener {
            viewModel.navigateBack()
        }

        binding.buttonContinue.setOnClickListener {
            viewModel.navigateTo(Screen.Note)
        }
    }

    override fun setStatusChecked(status: Status, isChecked: Boolean) {
        viewModel.setStatusChecked(status, isChecked)
    }

    override fun isStatusChecked(id: String): Boolean {
        return viewModel.isStatusChecked(id)
    }

    override fun onViewAttachment(view: View?, viewData: StatusViewDataQ, attachmentIndex: Int) {
        super.viewMedia(
            viewData.actionable.account.username,
            attachmentIndex,
            AttachmentViewData.list(viewData.actionable),
            view,
        )
    }

    override fun onViewThread(status: Status) {
        super.viewThread(status.actionableId, status.actionableStatus.url)
    }

    override fun onExpandedChange(viewData: StatusViewDataQ, expanded: Boolean) {
        viewModel.onChangeExpanded(expanded, viewData)
    }

    override fun onAttachmentDisplayActionChange(viewData: StatusViewDataQ, newAction: AttachmentDisplayAction) {
        viewModel.onChangeAttachmentDisplayAction(viewData, newAction)
    }

    override fun onContentCollapsedChange(viewData: StatusViewDataQ, isCollapsed: Boolean) {
        viewModel.onContentCollapsed(isCollapsed, viewData)
    }

    override fun clearContentFilter(viewData: StatusViewDataQ) {
        viewModel.clearWarning(viewData)
    }

    override fun onEditFilterById(pachliAccountId: Long, filterId: String) {
        startActivityWithTransition(
            EditContentFilterActivityIntent.edit(requireContext(), pachliAccountId, filterId),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    override fun onViewAccount(id: String) = startActivityWithDefaultTransition(
        AccountActivityIntent(requireContext(), pachliAccountId, id),
    )

    override fun onViewTag(tag: String) = startActivityWithDefaultTransition(
        TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag),
    )

    override fun onViewUrl(url: String) = viewModel.checkClickedUrl(url)

    override fun removeItem(viewData: StatusViewDataQ) = Unit
    override fun onReply(viewData: StatusViewDataQ) = Unit
    override fun onReblog(viewData: StatusViewDataQ, reblog: Boolean) = Unit
    override fun onFavourite(viewData: StatusViewDataQ, favourite: Boolean) = Unit
    override fun onBookmark(viewData: StatusViewDataQ, bookmark: Boolean) = Unit
    override fun onMore(view: View, viewData: StatusViewDataQ) = Unit
    override fun onOpenReblog(status: IStatus) = Unit
    override fun onVoteInPoll(viewData: StatusViewDataQ, poll: Poll, choices: List<Int>) = Unit
    override fun onTranslate(viewData: StatusViewDataQ) = Unit
    override fun onTranslateUndo(viewData: StatusViewDataQ) = Unit

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): ReportStatusesFragment {
            val fragment = ReportStatusesFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
