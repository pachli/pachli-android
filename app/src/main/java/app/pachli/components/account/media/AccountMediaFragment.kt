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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.network.model.Attachment
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.databinding.FragmentTimelineBinding
import app.pachli.interfaces.RefreshableFragment
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private val viewModel: AccountMediaViewModel by viewModels()

    private lateinit var adapter: AccountMediaGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.accountId = arguments?.getString(ACCOUNT_ID_ARG)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val useBlurhash = sharedPreferencesRepository.getBoolean(PrefKeys.USE_BLURHASH, true)

        adapter = AccountMediaGridAdapter(
            useBlurhash = useBlurhash,
            context = view.context,
            onAttachmentClickListener = ::onAttachmentClick,
        )

        val columnCount = view.context.resources.getInteger(DR.integer.profile_media_column_count)
        val imageSpacing = view.context.resources.getDimensionPixelSize(DR.dimen.profile_media_spacing)

        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, imageSpacing, 0))

        binding.recyclerView.layoutManager = GridLayoutManager(view.context, columnCount)
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
                            binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
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
                val intent = ViewMediaActivityIntent(requireContext(), attachmentsFromSameStatus, currentIndex)
                if (activity != null) {
                    val url = selected.attachment.url
                    ViewCompat.setTransitionName(view, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), view, url)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
                context?.openLink(selected.attachment.url)
            }
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
        fun newInstance(accountId: String): AccountMediaFragment {
            val fragment = AccountMediaFragment()
            val args = Bundle(1)
            args.putString(ACCOUNT_ID_ARG, accountId)
            fragment.arguments = args
            return fragment
        }

        private const val ACCOUNT_ID_ARG = "account_id"
    }
}
