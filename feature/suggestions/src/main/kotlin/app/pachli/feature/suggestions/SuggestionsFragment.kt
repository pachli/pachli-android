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
import androidx.annotation.StringRes
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
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
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
import app.pachli.core.ui.extensions.getErrorString
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

// TODO:
//
// - Swipe left to delete suggestion
// - Swipe right to follow
// - TODOs in the adapter
// - How much of AccountViewHolder can be reused?
// - talkbackWasEnabled machinery
// - Write a document that talks about this

/**
 * Notes on standard ways to do things.
 *
 * ## If there's a [SwipeRefreshLayout]
 *
 * - Fragment implements [OnRefreshListener] and [RefreshableFragment]
 * - OnRefreshListener.onRefresh() performs the work of refreshing the content (e.g.,
 *   updating the UI, sending the refresh request to the viewmodel)
 * - RefreshableFragment.refreshContent() exists so the swiperefreshlayout.isRefreshing
 *   can be set to true for a refresh triggered by a menu
 * - The fragment/activity must implement [MenuProvider], and provide a menu with a
 *   "Refresh" option for accessibility
 */

@AndroidEntryPoint
class SuggestionsFragment :
    Fragment(R.layout.fragment_suggestions),
    MenuProvider,
    OnRefreshListener,
    RefreshableFragment {
    private val viewModel: SuggestionsViewModel by viewModels()

    private val binding by viewBinding(FragmentSuggestionsBinding::bind)

    private lateinit var bottomSheetActivity: BottomSheetActivity

    private lateinit var suggestionsAdapter: SuggestionsAdapter

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
            animateEmojis = viewModel.uiState.value.animateEmojis,
            animateAvatars = viewModel.uiState.value.animateAvatars,
            showBotOverlay = viewModel.uiState.value.showBotOverlay,
            accept = accept,
        )

        with(binding.swipeRefreshLayout) {
            isEnabled = true
            setOnRefreshListener(this@SuggestionsFragment)
            setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        }

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(view.context)
            adapter = suggestionsAdapter
            addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
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
                    viewModel.operationCount.collectLatest { binding.progressIndicator.visible(it != 0) }
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

            else -> viewModel.accept(uiAction)
        }
    }

    // TODO: Copied from ListsActivity, should maybe be in core.ui as a Snackbar extension
    // See also ComposeActivity.displayTransientMessage
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).show()
    }

    private fun bindSuggestions(result: Result<Suggestions, GetSuggestionsError>) {
        binding.swipeRefreshLayout.isRefreshing = false

        result.onFailure {
            binding.messageView.show()
            binding.recyclerView.hide()

            // TODO: binding.swipeRefreshLayout.isRefreshing = false

//            if (it is NetworkError) {
//                binding.messageView.setup(BackgroundMessage.Network()) { viewModel.refresh() }
//            } else {
//                binding.messageView.setup(BackgroundMessage.GenericError()) { viewModel.refresh() }
//            }
        }

        result.onSuccess { suggestions ->
            when (suggestions) {
                Suggestions.Loading -> { /* nothing to do */ }
                is Suggestions.Loaded -> {
                    if (suggestions.suggestions.isEmpty()) {
                        binding.messageView.show()
                        binding.messageView.setup(BackgroundMessage.Empty())
                    } else {
                        suggestionsAdapter.submitList(suggestions.suggestions)
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

internal fun UiError.fmt(context: Context) = when (this) {
    is UiError.AcceptSuggestion -> context.getString(
        R.string.ui_error_delete_suggestion_fmt,
        action.suggestion.account.displayName,
        error.throwable.getErrorString(context),
    )
    is UiError.DeleteSuggestion -> context.getString(
        R.string.ui_error_follow_account_fmt,
        action.suggestion.account.displayName,
        error.throwable.getErrorString(context),
    )
}
