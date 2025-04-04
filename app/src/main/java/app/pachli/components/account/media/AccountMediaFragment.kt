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

package app.pachli.components.account.media

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.network.model.Attachment
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.FragmentTimelineBinding
import com.google.android.material.color.MaterialColors
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
 * Fragment with multiple columns of media previews for the specified account.
 */
@AndroidEntryPoint
class AccountMediaFragment :
    Fragment(R.layout.fragment_timeline),
    OnRefreshListener,
    RefreshableFragment,
    MenuProvider {

    @Inject
    lateinit var openUrl: OpenUrlUseCase

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private val viewModel: AccountMediaViewModel by viewModels()

    private lateinit var adapter: AccountMediaGridAdapter

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
        viewModel.accountId = arguments?.getString(ARG_ACCOUNT_ID)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        adapter = AccountMediaGridAdapter(
            context = view.context,
            statusDisplayOptions = viewModel.statusDisplayOptions.value,
            onAttachmentClickListener = ::onAttachmentClick,
        )

        val columnCount = view.context.resources.getInteger(DR.integer.profile_media_column_count)
        val layoutManager = StaggeredGridLayoutManager(columnCount, VERTICAL)
        layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.isEnabled = false
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.statusView.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.media.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            binding.statusView.hide()

            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(BackgroundMessage.Empty())
                        }
                    }
                    is LoadState.Error -> {
                        binding.statusView.show()
                        binding.statusView.setup((loadState.refresh as LoadState.Error).error) {
                            refreshContent()
                        }
                    }
                    is LoadState.Loading -> { /* nothing to do */ }
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_account_media, menu)
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

    private fun onAttachmentClick(selected: AttachmentViewData, view: View) {
        if (!selected.isRevealed) {
            viewModel.revealAttachment(selected)
            return
        }
        val attachmentsFromSameStatus = viewModel.attachmentData.filter { attachmentViewData ->
            attachmentViewData.statusId == selected.statusId
        }
        val currentIndex = attachmentsFromSameStatus.indexOf(selected)

        when (selected.attachment.type) {
            Attachment.Type.IMAGE,
            Attachment.Type.GIFV,
            Attachment.Type.VIDEO,
            Attachment.Type.AUDIO,
            -> {
                val intent = ViewMediaActivityIntent(requireContext(), pachliAccountId, selected.username, attachmentsFromSameStatus, currentIndex)
                if (activity != null) {
                    val url = selected.attachment.url
                    ViewCompat.setTransitionName(view, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), view, url)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> openUrl(selected.attachment.url)
        }
    }

    /** Refresh the displayed content, as if the user had swiped on the SwipeRefreshLayout */
    override fun refreshContent() {
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

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
        private const val ARG_ACCOUNT_ID = "app.pachli.ARG_ACCOUNT_ID"

        fun newInstance(pachliAccountId: Long, accountId: String): AccountMediaFragment {
            val fragment = AccountMediaFragment()
            fragment.arguments = Bundle(2).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
                putString(ARG_ACCOUNT_ID, accountId)
            }
            return fragment
        }
    }
}
