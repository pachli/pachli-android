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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

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
import app.pachli.StatusListActivity
import app.pachli.ViewMediaActivity
import app.pachli.components.account.AccountActivity
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.components.report.adapter.AdapterHandler
import app.pachli.components.report.adapter.StatusesAdapter
import app.pachli.databinding.FragmentReportStatusesBinding
import app.pachli.core.database.AccountManager
import app.pachli.entity.Attachment
import app.pachli.entity.Status
import app.pachli.util.viewBinding
import app.pachli.util.visible
import app.pachli.viewdata.AttachmentViewData
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    override fun showMedia(v: View?, status: Status?, idx: Int) {
        status?.actionableStatus?.let { actionable ->
            when (actionable.attachments[idx].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments, idx)
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
        lifecycleScope.launch {
            val statusDisplayOptions = viewModel.statusDisplayOptions.value

            adapter = StatusesAdapter(
                statusDisplayOptions,
                viewModel.statusViewState,
                this@ReportStatusesFragment,
            )

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
            snackbarErrorRetry?.setAction(R.string.action_retry) {
                adapter.retry()
            }
            snackbarErrorRetry?.show()
        }
    }

    private fun handleClicks() {
        binding.buttonCancel.setOnClickListener {
            viewModel.navigateTo(Screen.Back)
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

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) = startActivity(StatusListActivity.newHashtagIntent(requireContext(), tag))

    override fun onViewUrl(url: String) = viewModel.checkClickedUrl(url)

    companion object {
        fun newInstance() = ReportStatusesFragment()
    }
}
