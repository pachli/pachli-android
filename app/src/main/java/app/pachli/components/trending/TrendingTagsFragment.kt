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

package app.pachli.components.trending

import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.trending.viewmodel.TrendingTagsViewModel
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.StatusListActivityIntent
import app.pachli.databinding.FragmentTrendingTagsBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.interfaces.ReselectableFragment
import app.pachli.viewdata.TrendingViewData
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TrendingTagsFragment :
    Fragment(R.layout.fragment_trending_tags),
    OnRefreshListener,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    private val viewModel: TrendingTagsViewModel by viewModels()

    private val binding by viewBinding(FragmentTrendingTagsBinding::bind)

    private val adapter = TrendingTagsAdapter(::onViewTag)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount =
            requireContext().resources.getInteger(DR.integer.trending_column_count)
        setupLayoutManager(columnCount)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setupSwipeRefreshLayout()
        setupRecyclerView()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { trendingState ->
                processViewState(trendingState)
            }
        }

        (activity as? ActionButtonActivity)?.actionButton?.hide()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun setupLayoutManager(columnCount: Int) {
        binding.recyclerView.layoutManager = GridLayoutManager(context, columnCount).apply {
            spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        TrendingTagsAdapter.VIEW_TYPE_HEADER -> columnCount
                        TrendingTagsAdapter.VIEW_TYPE_TAG -> 1
                        else -> -1
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val columnCount =
            requireContext().resources.getInteger(DR.integer.trending_column_count)
        setupLayoutManager(columnCount)

        binding.recyclerView.setHasFixedSize(true)

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_trending_tags, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt =
                    MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
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

    override fun onRefresh() {
        viewModel.invalidate(true)
    }

    fun onViewTag(tag: String) {
        (requireActivity() as BaseActivity).startActivityWithSlideInAnimation(
            StatusListActivityIntent.hashtag(
                requireContext(),
                tag,
            ),
        )
    }

    private fun processViewState(uiState: TrendingTagsViewModel.TrendingTagsUiState) {
        Timber.d(uiState.loadingState.name)
        when (uiState.loadingState) {
            TrendingTagsViewModel.LoadingState.INITIAL -> clearLoadingState()
            TrendingTagsViewModel.LoadingState.LOADING -> applyLoadingState()
            TrendingTagsViewModel.LoadingState.REFRESHING -> applyRefreshingState()
            TrendingTagsViewModel.LoadingState.LOADED -> applyLoadedState(uiState.trendingViewData)
            TrendingTagsViewModel.LoadingState.ERROR_NETWORK -> networkError()
            TrendingTagsViewModel.LoadingState.ERROR_OTHER -> otherError()
        }
    }

    private fun applyLoadedState(viewData: List<TrendingViewData>) {
        clearLoadingState()

        adapter.submitList(viewData)

        if (viewData.isEmpty()) {
            binding.recyclerView.hide()
            binding.messageView.show()
            binding.messageView.setup(
                R.drawable.elephant_friend_empty,
                R.string.message_empty,
                null,
            )
        } else {
            binding.recyclerView.show()
            binding.messageView.hide()
        }
        binding.progressBar.hide()
    }

    private fun applyRefreshingState() {
        binding.swipeRefreshLayout.isRefreshing = true
    }

    private fun applyLoadingState() {
        binding.recyclerView.hide()
        binding.messageView.hide()
        binding.progressBar.show()
    }

    private fun clearLoadingState() {
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progressBar.hide()
        binding.messageView.hide()
    }

    private fun networkError() {
        binding.recyclerView.hide()
        binding.messageView.show()
        binding.progressBar.hide()

        binding.swipeRefreshLayout.isRefreshing = false
        binding.messageView.setup(
            R.drawable.errorphant_offline,
            R.string.error_network,
        ) { refreshContent() }
    }

    private fun otherError() {
        binding.recyclerView.hide()
        binding.messageView.show()
        binding.progressBar.hide()

        binding.swipeRefreshLayout.isRefreshing = false
        binding.messageView.setup(
            R.drawable.errorphant_error,
            R.string.error_generic,
        ) { refreshContent() }
    }

    private fun actionButtonPresent(): Boolean {
        return activity is ActionButtonActivity
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Timber.d("talkback was enabled: %s, now %s", wasEnabled, talkBackWasEnabled)
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        if (actionButtonPresent()) {
            val composeButton = (activity as ActionButtonActivity).actionButton
            composeButton?.hide()
        }

        (requireActivity() as? AppBarLayoutHost)?.appBarLayout?.setLiftOnScrollTargetView(binding.recyclerView)
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun refreshContent() {
        onRefresh()
    }

    companion object {
        fun newInstance() = TrendingTagsFragment()
    }
}
