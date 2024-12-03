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
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.trending.viewmodel.InfallibleUiAction
import app.pachli.components.trending.viewmodel.LoadState
import app.pachli.components.trending.viewmodel.TrendingLinksViewModel
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.ServerOperation
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.network.model.PreviewCard
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.FragmentTrendingLinksBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.view.PreviewCardView.Target
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.z4kn4fein.semver.constraints.toConstraint
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

@AndroidEntryPoint
class TrendingLinksFragment :
    Fragment(R.layout.fragment_trending_links),
    OnRefreshListener,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    private val viewModel: TrendingLinksViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<TrendingLinksViewModel.Factory> { factory ->
                factory.create(requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID))
            }
        },
    )

    private val binding by viewBinding(FragmentTrendingLinksBinding::bind)

    private lateinit var trendingLinksAdapter: TrendingLinksAdapter

    private var talkBackWasEnabled = false

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.recyclerView.layoutManager = getLayoutManager(
            requireContext().resources.getInteger(DR.integer.trending_column_count),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        (activity as? ActionButtonActivity)?.actionButton?.let { actionButton ->
            actionButton.show()

            val actionButtonScrollListener = ActionButtonScrollListener(actionButton)
            binding.recyclerView.addOnScrollListener(actionButtonScrollListener)

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.showFabWhileScrolling.collect { showFabWhileScrolling ->
                        actionButtonScrollListener.showActionButtonWhileScrolling = showFabWhileScrolling
                    }
                }
            }
        }

        trendingLinksAdapter = TrendingLinksAdapter(
            viewModel.statusDisplayOptions.value,
            false,
            ::onOpenLink,
        )

        setupSwipeRefreshLayout()
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pachliAccountFlow.distinctUntilChangedBy { it.server }.collect { account ->
                        trendingLinksAdapter.showTimelineLink = account.server.can(
                            ServerOperation.ORG_JOINMASTODON_TIMELINES_LINK,
                            ">=1.0.0".toConstraint(),
                        )
                    }
                }

                launch {
                    viewModel.loadState.collect {
                        when (it) {
                            LoadState.Loading -> bindLoading()
                            is LoadState.Success -> bindSuccess(it)
                            is LoadState.Error -> bindError(it)
                        }
                    }
                }
                launch {
                    viewModel.statusDisplayOptions.collectLatest {
                        trendingLinksAdapter.statusDisplayOptions = it
                    }
                }
            }
        }

        viewModel.accept(InfallibleUiAction.Reload)
    }

    private fun bindLoading() {
        if (!binding.swipeRefreshLayout.isRefreshing) {
            binding.progressBar.show()
        } else {
            binding.progressBar.hide()
        }
    }

    private fun bindSuccess(loadState: LoadState.Success) {
        trendingLinksAdapter.submitList(loadState.data)
        binding.progressBar.hide()
        binding.swipeRefreshLayout.isRefreshing = false
        if (loadState.data.isEmpty()) {
            binding.messageView.setup(BackgroundMessage.Empty())
            binding.messageView.show()
        } else {
            binding.messageView.hide()
            binding.recyclerView.show()
        }
    }

    private fun bindError(loadState: LoadState.Error) {
        binding.progressBar.hide()
        binding.swipeRefreshLayout.isRefreshing = false
        binding.recyclerView.hide()
        if (trendingLinksAdapter.itemCount != 0) {
            val snackbar = Snackbar.make(
                binding.root,
                loadState.throwable.message ?: "Error",
                Snackbar.LENGTH_INDEFINITE,
            )

            if (loadState.throwable !is HttpException || loadState.throwable.code() != 404) {
                snackbar.setAction("Retry") { viewModel.accept(InfallibleUiAction.Reload) }
            }
            snackbar.show()
        } else {
            if (loadState.throwable !is HttpException || loadState.throwable.code() != 404) {
                binding.messageView.setup(loadState.throwable) {
                    viewModel.accept(InfallibleUiAction.Reload)
                }
            } else {
                binding.messageView.setup(loadState.throwable)
            }
            binding.messageView.show()
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            layoutManager = getLayoutManager(requireContext().resources.getInteger(DR.integer.trending_column_count))
            setHasFixedSize(true)
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            adapter = trendingLinksAdapter
            setAccessibilityDelegateCompat(TrendingLinksAccessibilityDelegate(this, ::onOpenLink))
        }
    }

    private fun getLayoutManager(columnCount: Int) = GridLayoutManager(context, columnCount)

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_trending_links, menu)
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
                refreshContent()
                true
            }

            else -> false
        }
    }

    override fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    override fun onRefresh() = viewModel.accept(InfallibleUiAction.Reload)

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    private fun onOpenLink(card: PreviewCard, target: Target) {
        when (target) {
            Target.CARD -> requireContext().openLink(card.url)
            Target.IMAGE -> requireContext().openLink(card.url)
            Target.BYLINE -> card.authors?.firstOrNull()?.account?.id?.let {
                startActivity(AccountActivityIntent(requireContext(), pachliAccountId, it))
            }

            Target.LINK_TIMELINE -> {
                val intent = TimelineActivityIntent.link(
                    requireContext(),
                    pachliAccountId,
                    card.url,
                )
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Timber.d("talkback was enabled: %s, now %s", wasEnabled, talkBackWasEnabled)
        if (talkBackWasEnabled && !wasEnabled) {
            trendingLinksAdapter.notifyItemRangeChanged(0, trendingLinksAdapter.itemCount)
        }

        (requireActivity() as? AppBarLayoutHost)?.appBarLayout?.setLiftOnScrollTargetView(binding.recyclerView)
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long): TrendingLinksFragment {
            val fragment = TrendingLinksFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
