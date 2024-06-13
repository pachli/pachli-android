/*
 * Copyright 2024 Pachli Association
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

package app.pachli.feature.suggestions

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.PostLookupFallbackBehavior
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.model.SuggestionSources
import app.pachli.core.data.model.SuggestionSources.FEATURED
import app.pachli.core.data.model.SuggestionSources.FRIENDS_OF_FRIENDS
import app.pachli.core.data.model.SuggestionSources.MOST_FOLLOWED
import app.pachli.core.data.model.SuggestionSources.MOST_INTERACTIONS
import app.pachli.core.data.model.SuggestionSources.SIMILAR_TO_RECENTLY_FOLLOWED
import app.pachli.core.data.model.SuggestionSources.UNKNOWN
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.ui.BackgroundMessage
import app.pachli.feature.suggestions.UiAction.GetSuggestions
import app.pachli.feature.suggestions.UiAction.NavigationAction
import app.pachli.feature.suggestions.databinding.FragmentSuggestionsBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SuggestionsFragment :
    Fragment(R.layout.fragment_suggestions),
    MenuProvider,
    OnRefreshListener,
    RefreshableFragment,
    ReselectableFragment {
    private val viewModel: SuggestionsViewModel by viewModels()

    private val binding by viewBinding(FragmentSuggestionsBinding::bind)

    private lateinit var bottomSheetActivity: BottomSheetActivity

    private lateinit var suggestionsAdapter: SuggestionsAdapter

    private var talkBackWasEnabled = false

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emits them in to [uiAction]. */
    private val accept: (UiAction) -> Unit = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = (context as? BottomSheetActivity) ?: throw IllegalStateException("Fragment must be attached to a BottomSheetActivity")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        suggestionsAdapter = SuggestionsAdapter(
            animateAvatars = viewModel.uiState.value.animateAvatars,
            animateEmojis = viewModel.uiState.value.animateEmojis,
            showBotOverlay = viewModel.uiState.value.showBotOverlay,
            accept = accept,
        )

        with(binding.swipeRefreshLayout) {
            isEnabled = true
            setOnRefreshListener(this@SuggestionsFragment)
            setColorSchemeColors(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        }

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionsAdapter
            addItemDecoration(MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
        }

        bind()
    }

    /** Binds data to the UI */
    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiState.collectLatest(::bindUiState) }

                launch { uiAction.throttleFirst().collect(::bindUiAction) }

                launch { viewModel.suggestions.collectLatest(::bindSuggestions) }

                launch { viewModel.uiResult.collect(::bindUiResult) }

                // TODO: Very similar to code in ListsActivity
                launch {
                    viewModel.operationCount.collectLatest {
                        if (it == 0) binding.progressIndicator.hide() else binding.progressIndicator.show()
                    }
                }
            }
        }
    }

    private fun bindUiState(uiState: UiState) {
        suggestionsAdapter.setAnimateEmojis(uiState.animateEmojis)
        suggestionsAdapter.setAnimateAvatars(uiState.animateAvatars)
        suggestionsAdapter.setShowBotOverlay(uiState.showBotOverlay)
    }

    /**
     * Process actions from the UI.
     */
    private fun bindUiAction(uiAction: UiAction) {
        when (uiAction) {
            is NavigationAction -> {
                when (uiAction) {
                    is NavigationAction.ViewAccount -> requireActivity().startActivityWithTransition(AccountActivityIntent(requireContext(), uiAction.accountId), TransitionKind.SLIDE_FROM_END)
                    is NavigationAction.ViewHashtag -> requireActivity().startActivityWithTransition(TimelineActivityIntent.hashtag(requireContext(), uiAction.hashtag), TransitionKind.SLIDE_FROM_END)
                    is NavigationAction.ViewUrl -> bottomSheetActivity.viewUrl(uiAction.url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
                }
            }
            else -> {
                viewModel.accept(uiAction)
            }
        }
    }

    private fun bindSuggestions(result: Result<Suggestions, GetSuggestionsError>) {
        binding.swipeRefreshLayout.isRefreshing = false

        Timber.d("bindSuggestions: %s", result)
        result.onFailure {
            binding.messageView.show()
            binding.recyclerView.hide()

            binding.messageView.setup(it) { viewModel.accept(GetSuggestions) }
        }

        result.onSuccess {
            when (it) {
                Suggestions.Loading -> { /* nothing to do */ }
                is Suggestions.Loaded -> {
                    if (it.suggestions.isEmpty()) {
                        binding.messageView.show()
                        binding.messageView.setup(BackgroundMessage.Empty())
                    } else {
                        suggestionsAdapter.submitList(it.suggestions)
                        binding.messageView.hide()
                        binding.recyclerView.show()
                    }
                }
            }
        }
    }

    /** Act on the result of UI actions */
    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
        uiResult.onFailure { uiError ->
            val message = uiError.fmt(requireContext())
            snackbar?.dismiss()
            try {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).apply {
                    uiError.action.let { uiAction -> setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.accept(uiAction) } }
                    show()
                    snackbar = this
                }
            } catch (_: IllegalArgumentException) {
                // On rare occasions this code is running before the fragment's
                // view is connected to the parent. This causes Snackbar.make()
                // to crash.  See https://issuetracker.google.com/issues/228215869.
                // For now, swallow the exception.
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_suggestions, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            // TODO: This should use makeIcon, which should be moved somewhere common
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

    override fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    override fun onRefresh() {
        snackbar?.dismiss()
        viewModel.accept(GetSuggestions)
    }

    override fun onReselect() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onResume() {
        super.onResume()

        val a11yManager = ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)
        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        if (talkBackWasEnabled && !wasEnabled) {
            suggestionsAdapter.notifyItemRangeChanged(0, suggestionsAdapter.itemCount)
        }
    }

    companion object {
        fun newInstance() = SuggestionsFragment()
    }
}

/**
 * @return A string resource for the given [SuggestionSources], suitable for use
 * as the reason this suggestion is present.
 */
@StringRes
fun SuggestionSources.stringResource() = when (this) {
    FEATURED -> R.string.sources_featured
    MOST_FOLLOWED -> R.string.sources_most_followed
    MOST_INTERACTIONS -> R.string.sources_most_interactions
    SIMILAR_TO_RECENTLY_FOLLOWED -> R.string.sources_similar_to_recently_followed
    FRIENDS_OF_FRIENDS -> R.string.sources_friends_of_friends
    UNKNOWN -> R.string.sources_unknown
}
