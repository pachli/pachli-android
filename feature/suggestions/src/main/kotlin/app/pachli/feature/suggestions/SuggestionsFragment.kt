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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.PostLookupFallbackBehavior
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.emojify
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.activity.loadAvatar
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.Suggestion
import app.pachli.core.data.model.SuggestionSources
import app.pachli.core.data.model.SuggestionSources.FEATURED
import app.pachli.core.data.model.SuggestionSources.FRIENDS_OF_FRIENDS
import app.pachli.core.data.model.SuggestionSources.MOST_FOLLOWED
import app.pachli.core.data.model.SuggestionSources.MOST_INTERACTIONS
import app.pachli.core.data.model.SuggestionSources.SIMILAR_TO_RECENTLY_FOLLOWED
import app.pachli.core.data.model.SuggestionSources.UNKNOWN
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.network.extensions.getServerErrorMessage
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.setClickableText
import app.pachli.feature.suggestions.databinding.FragmentSuggestionsBinding
import app.pachli.feature.suggestions.databinding.ItemSuggestionBinding
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

// TODO:
//
// - Loading progress bar initially set
// x Best way to pass click from item in the adapter to the fragment to the viewmodel
// x Click through account entry to view profile
// x Follow button
// x Delete suggestion button
// - Swipe left to delete suggestion
// - Swipe right to follow
// - UiAction to viewmodel, with retry
// - debounce actions
// - TODOs in the adapter
// - How much of AccountViewHolder can be reused?
// x swipe/refresh layout
//   x layout
//   x menu items
// - talkbackWasEnabled machinery

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

    private lateinit var suggestionAdapter: SuggestionAdapter

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emits them in to [uiAction]. */
    val accept: (UiAction) -> Unit = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = (context as? BottomSheetActivity) ?: throw IllegalStateException("Fragment must be attached to a BottomSheetActivity")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        suggestionAdapter = SuggestionAdapter(
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
            adapter = suggestionAdapter
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

                launch { viewModel.uiSuccess.collect(::bindUiSuccess) }

                launch { viewModel.uiErrors.collect(::bindUiErrors) }

                // TODO: Very similar to code in ListsActivity
                launch {
                    viewModel.operationCount.collectLatest { binding.progressIndicator.visible(it != 0) }
                }
            }
        }
    }

    private fun bindUiState(uiState: UiState) {
        suggestionAdapter.setAnimateEmojis(uiState.animateEmojis)
        suggestionAdapter.setAnimateAvatars(uiState.animateAvatars)
        suggestionAdapter.setShowBotOverlay(uiState.showBotOverlay)
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
                        suggestionAdapter.submitList(suggestions.suggestions)
                        binding.messageView.hide()
                        binding.recyclerView.show()
                    }
                }
            }
        }
    }

    /**
     * Act on successful UI actions.
     *
     * - Remove suggestions that have been acted on from the list of suggestions. Do not
     *   reload the list as there's no guarantee the new list will be in the same order
     *   or have the same contents, and the user will lose their place.
     */
    private fun bindUiSuccess(uiSuccess: UiSuccess) {
        suggestionAdapter.removeSuggestion(uiSuccess.action.suggestion)
    }

    /**
     * Act on failed UI actions.
     */
    private fun bindUiErrors(uiError: UiError) {
        val message = when (uiError) {
            is UiError.DeleteSuggestion -> getString(
                uiError.stringResource(),
                uiError.action.suggestion.account.displayName,
                uiError.error.throwable.getServerErrorMessage() ?: uiError.error.throwable.localizedMessage ?: getString(app.pachli.core.ui.R.string.ui_error_unknown).unicodeWrap(),
            )

            is UiError.AcceptSuggestion -> getString(
                uiError.stringResource(),
                uiError.action.suggestion.account.displayName,
                uiError.error.throwable.getServerErrorMessage() ?: uiError.error.throwable.localizedMessage ?: getString(app.pachli.core.ui.R.string.ui_error_unknown).unicodeWrap(),
            )
        }
        Timber.d(uiError.error.throwable, message)
        snackbar?.dismiss()
        // TODO: Handle the case where the view might might be null, see similar
        // code in SFragment.
        snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
        uiError.action?.let { action ->
            snackbar!!.setAction(app.pachli.core.ui.R.string.action_retry) {
                viewModel.accept(action)
            }
        }
        snackbar!!.show()
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

    private object SuggestionDiffer : DiffUtil.ItemCallback<Suggestion>() {
        override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem.account == newItem.account
        override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem == newItem
    }

    /**
     * Adapter for [Suggestion].
     */
    // TODO: This is quite similar to AccountAdapter, so if some functionality can be
    // made common. See things like FollowRequestViewHolder.setupWithAccount as well.
    class SuggestionAdapter(
        private var animateEmojis: Boolean,
        private var animateAvatars: Boolean,
        private var showBotOverlay: Boolean,
        private val accept: (UiAction) -> Unit,
    ) : ListAdapter<Suggestion, SuggestionAdapter.SuggestionViewHolder>(
        SuggestionDiffer,
    ) {
        fun setAnimateEmojis(animateEmojis: Boolean) {
            if (this.animateEmojis == animateEmojis) return
            this.animateEmojis = animateEmojis
            notifyItemRangeChanged(0, currentList.size)
        }

        fun setAnimateAvatars(animateAvatars: Boolean) {
            if (this.animateAvatars == animateAvatars) return
            this.animateAvatars = animateAvatars
            notifyItemRangeChanged(0, currentList.size)
        }

        fun setShowBotOverlay(showBotOverlay: Boolean) {
            if (this.showBotOverlay == showBotOverlay) return
            this.showBotOverlay = showBotOverlay
            notifyItemRangeChanged(0, currentList.size)
        }

        /** Removes [suggestion] from the current list */
        fun removeSuggestion(suggestion: Suggestion) {
            submitList(currentList.filterNot { it == suggestion })
        }

        override fun getItemViewType(position: Int) = R.layout.item_suggestion

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SuggestionViewHolder(binding, accept)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            holder.bind(
                currentList[position],
                animateEmojis,
                animateAvatars,
                showBotOverlay,
            )
        }

        class SuggestionViewHolder(
            private val binding: ItemSuggestionBinding,
            private val accept: (UiAction) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var suggestion: Suggestion

            private val avatarRadius: Int

            /**
             * Link listener for [setClickableText] that generates the appropriate
             * navigation actions.
             */
            private val linkListener = object : LinkListener {
                override fun onViewTag(tag: String) = accept(NavigationAction.ViewHashtag(tag))
                override fun onViewAccount(id: String) = accept(NavigationAction.ViewAccount(id))
                override fun onViewUrl(url: String) = accept(NavigationAction.ViewUrl(url))
            }

            init {
                with(binding) {
                    followAccount.setOnClickListener { accept(SuggestionAction.AcceptSuggestion(suggestion)) }
                    deleteSuggestion.setOnClickListener { accept(SuggestionAction.DeleteSuggestion(suggestion)) }
                    accountNote.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }
                    root.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }

                    avatarRadius = avatar.context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.avatar_radius_48dp)
                }
            }

            // TODO: Similar to FollowRequestViewHolder.setupWithAccount
            fun bind(
                suggestion: Suggestion,
                animateEmojis: Boolean,
                animateAvatars: Boolean,
                showBotOverlay: Boolean,
            ) {
                this.suggestion = suggestion
                val account = suggestion.account

                with(binding) {
                    suggestion.sources.firstOrNull()?.let {
                        suggestionReason.text = suggestionReason.context.getString(it.stringResource())
                        suggestionReason.show()
                    } ?: suggestionReason.hide()

                    displayName.text = account.name.unicodeWrap()
                    // TODO: Emojis and animated emojis here

                    val formattedUsername = username.context.getString(app.pachli.core.designsystem.R.string.post_username_format, account.username)
                    username.text = formattedUsername

                    if (account.note.isBlank()) {
                        accountNote.hide()
                    } else {
                        accountNote.show()
                        val emojifiedNote = account.note.parseAsMastodonHtml()
                            .emojify(account.emojis, accountNote, animateEmojis)

                        setClickableText(accountNote, emojifiedNote, emptyList(), null, linkListener)
                    }

                    loadAvatar(account.avatar, avatar, avatarRadius, animateAvatars)
                    avatarBadge.visible(showBotOverlay && account.bot)
                }
            }
        }
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

@StringRes
fun UiError.stringResource() = when (this) {
    is UiError.DeleteSuggestion -> R.string.ui_error_delete_suggestion_fmt
    is UiError.AcceptSuggestion -> R.string.ui_error_follow_account_fmt
}
