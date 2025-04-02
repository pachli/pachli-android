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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.components.report.adapter.AdapterHandler
import app.pachli.components.report.adapter.StatusesAdapter
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Status
import app.pachli.core.ui.SetMarkdownContent
import app.pachli.core.ui.SetMastodonHtmlContent
import app.pachli.databinding.FragmentReportStatusesBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Show a list of statuses from the account the user is reporting. Allow
 * the user to choose one or more of these to include in the report.
 */
@AndroidEntryPoint
class ReportStatusesFragment :
    Fragment(R.layout.fragment_report_statuses),
    OnRefreshListener,
    MenuProvider,
    AdapterHandler {

    @Inject
    lateinit var accountManager: AccountManager

    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportStatusesBinding::bind)

    private lateinit var adapter: StatusesAdapter

    private var snackbarErrorRetry: Snackbar? = null

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)

        val setStatusContent = if (viewModel.statusDisplayOptions.value.renderMarkdown) {
            SetMarkdownContent(requireContext())
        } else {
            SetMastodonHtmlContent
        }

        adapter = StatusesAdapter(
            setStatusContent,
            viewModel.statusDisplayOptions.value,
            viewModel.statusViewState,
            this@ReportStatusesFragment,
        )
    }

    override fun showMedia(v: View?, status: Status?, idx: Int) {
        status?.actionableStatus?.let { actionable ->
            when (actionable.attachments[idx].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivityIntent(
                        requireContext(),
                        pachliAccountId,
                        actionable.account.username,
                        attachments,
                        idx,
                    )
                    if (v != null) {
                        val url = actionable.attachments[idx].url
                        ViewCompat.setTransitionName(v, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), v, url)
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }
                Attachment.Type.UNKNOWN -> {
                }
            }
        }
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
        viewLifecycleOwner.lifecycleScope.launch {
            binding.recyclerView.addItemDecoration(
                MaterialDividerItemDecoration(
                    requireContext(),
                    MaterialDividerItemDecoration.VERTICAL,
                ),
            )
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerView.adapter = adapter
            (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations =
                false

            adapter.addLoadStateListener { loadState ->
                if (loadState.refresh is LoadState.Error ||
                    loadState.append is LoadState.Error ||
                    loadState.prepend is LoadState.Error
                ) {
                    showError()
                }

                binding.progressBarBottom.visible(loadState.append == LoadState.Loading)
                binding.progressBarTop.visible(loadState.prepend == LoadState.Loading)
                binding.progressBarLoading.visible(loadState.refresh == LoadState.Loading && !binding.swipeRefreshLayout.isRefreshing)

                if (loadState.refresh != LoadState.Loading) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }

            viewModel.statusesFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
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

    override fun onViewAccount(id: String) = startActivity(
        AccountActivityIntent(requireContext(), pachliAccountId, id),
    )

    override fun onViewTag(tag: String) = startActivity(
        TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag),
    )

    override fun onViewUrl(url: String) = viewModel.checkClickedUrl(url)

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
