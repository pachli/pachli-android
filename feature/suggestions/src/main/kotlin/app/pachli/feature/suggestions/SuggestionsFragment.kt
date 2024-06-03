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
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import dagger.hilt.android.AndroidEntryPoint
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
// - swipe/refresh layout
//   x layout
//   - menu items
//

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
class SuggestionsFragment : Fragment(R.layout.fragment_suggestions), OnRefreshListener, RefreshableFragment {
    private val viewModel: SuggestionsViewModel by viewModels()

    private val binding by viewBinding(FragmentSuggestionsBinding::bind)

    private lateinit var bottomSheetActivity: BottomSheetActivity

    // Best practice:
    // Adapter takes a function that receives events, rather than something implementing an
    // interface that has those functions. This makes testing the adapter easier, as test
    // functions can be sent.
    //
    // Action contains the data it needs to operate, not the adapter position. Sidesteps any
    // possible race conditions, or confusion over which adapter method should be used.
    private lateinit var suggestionsAdapter: SuggestionsAdapter

    // TODO: Should this emit in to a flow (the same structure as the viewmodel
    // equivalent)? This would allow e.g., debouncing here rather than in the
    // viewmodel, which might be a good idea.
    val accept: (UiAction) -> Unit = { action ->
        Timber.d("action: %s", action)
        when (action) {
            is NavigationAction -> {
                when (action) {
                    is NavigationAction.ViewAccount -> requireActivity().startActivityWithTransition(AccountActivityIntent(requireContext(), action.accountId), TransitionKind.SLIDE_FROM_END)
                    is NavigationAction.ViewHashtag -> requireActivity().startActivityWithTransition(TimelineActivityIntent.hashtag(requireContext(), action.hashtag), TransitionKind.SLIDE_FROM_END)
                    is NavigationAction.ViewUrl -> bottomSheetActivity.viewUrl(action.url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
                }
            }
            else -> viewModel.accept(action)
        }
    }

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = (context as? BottomSheetActivity) ?: throw IllegalStateException("Fragment must be attached to a BottomSheetActivity")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val animateEmojis = false
        val animateAvatars = false
        val showBotOverlay = true

        suggestionsAdapter = SuggestionsAdapter(
            animateEmojis = animateEmojis,
            animateAvatars = animateAvatars,
            showBotOverlay = showBotOverlay,
            accept = accept,
        )

        binding.swipeRefreshLayout.isEnabled = true
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(view.context)
            adapter = suggestionsAdapter
            addItemDecoration(
                MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
            )
            setHasFixedSize(true)
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.suggestions.collectLatest(::bindSuggestions)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiSuccess.collect {
                    when (it) {
                        is UiSuccess.DeleteSuggestion -> viewModel.accept(GetSuggestions)
                        is UiSuccess.FollowAccount -> viewModel.accept(GetSuggestions)
                    }
                }
            }
        }

        // TODO: Very similar to code in ListsActivity, TimelineFragment,
        // NotificationsFragment. Time to look at how to make this more general.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiErrors.collect { error ->
                    // TODO: The repeated "error.error" here is a problem, fix that.
                    val message = when (error) {
                        is UiError.DeleteSuggestion -> getString(
                            error.stringResource(),
                            error.action.suggestion.account.displayName,
                            error.error.throwable.getServerErrorMessage() ?: error.error.throwable.localizedMessage ?: getString(app.pachli.core.ui.R.string.ui_error_unknown).unicodeWrap(),
                        )

                        is UiError.FollowAccount -> getString(
                            error.stringResource(),
                            error.action.account.displayName,
                            error.error.throwable.getServerErrorMessage() ?: error.error.throwable.localizedMessage ?: getString(app.pachli.core.ui.R.string.ui_error_unknown).unicodeWrap(),
                        )
                    }
                    Timber.d(error.error.throwable, message)
                    snackbar?.dismiss()
                    // TODO: Handle the case where the view might might be null, see similar
                    // code in SFragment.
                    snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    error.action?.let { action ->
                        snackbar!!.setAction(app.pachli.core.ui.R.string.action_retry) {
                            viewModel.accept(action)
                        }
                    }
                    snackbar!!.show()
                }
            }
        }

        // TODO: Very similar to code in ListsActivity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.operationCount.collectLatest {
                    if (it == 0) {
                        binding.progressIndicator.hide()
                    } else {
                        binding.progressIndicator.show()
                    }
                }
            }
        }

        // TODO: Think about this some more. Specifically, should the viewmodel
        // start the fetch as soon as `suggestions` is collected, or should it
        // wait until this line (commented out) explicitly triggers a fetch?
        //
        // Fetch on collection:
        // - slightly less work for the fragment to do (no need to make
        //   explicit request)
        // - maybe fractionally faster to start the network request?
        //
        // Explicit fetch:
        // - code in fragment might be clearer
        // - code in viewmodel might be clearer
//        viewModel.accept(GetSuggestions)
    }

    // TODO: Copied from ListsActivity, should maybe be in core.ui as a Snackbar extension
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).show()
    }

    private fun bindSuggestions(result: Result<List<Suggestion>, GetSuggestionsError>) {
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
            if (suggestions.isEmpty()) {
                binding.messageView.show()
                binding.messageView.setup(BackgroundMessage.Empty())
            } else {
                suggestionsAdapter.submitList(suggestions)
                binding.messageView.hide()
                binding.recyclerView.show()
            }
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

    // TODO: This is quite similar to AccountAdapter, so if some functionality can be
    // made common. See things like FollowRequestViewHolder.setupWithAccount as well.
    class SuggestionsAdapter(
        private var animateEmojis: Boolean,
        private var animateAvatars: Boolean,
        private var showBotOverlay: Boolean,
        private val accept: (UiAction) -> Unit,
    ) : ListAdapter<Suggestion, SuggestionsAdapter.SuggestionsViewHolder>(
        SuggestionDiffer,
    ) {
        fun setAnimateEmojis(animateEmojis: Boolean) {
            this.animateEmojis = animateEmojis
            notifyItemRangeChanged(1, currentList.size)
        }

        fun setAnimateAvatars(animateAvatars: Boolean) {
            this.animateAvatars = animateAvatars
            notifyItemRangeChanged(1, currentList.size)
        }

        fun setShowBotOverlay(showBotOverlay: Boolean) {
            this.showBotOverlay = showBotOverlay
            notifyItemRangeChanged(1, currentList.size)
        }

        override fun getItemViewType(position: Int) = if (position == 0) R.layout.item_heading else R.layout.item_suggestion

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionsViewHolder {
            val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SuggestionsViewHolder(binding, accept)
        }

        override fun onBindViewHolder(holder: SuggestionsViewHolder, position: Int) {
            holder.bind(
                currentList[position],
                animateEmojis,
                animateAvatars,
                showBotOverlay,
            )
        }

        class SuggestionsViewHolder(
            private val binding: ItemSuggestionBinding,
            private val accept: (UiAction) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var suggestion: Suggestion

            /**
             * Link listener for [setClickableText] that generates the appropriate
             * navigation actions.
             */
            private val linkListener = object : LinkListener {
                override fun onViewTag(tag: String) = accept(NavigationAction.ViewHashtag(tag))
                override fun onViewAccount(id: String) = accept(NavigationAction.ViewAccount(id))
                override fun onViewUrl(url: String) = accept(NavigationAction.ViewUrl(url))
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

                    val avatarRadius = avatar.context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.avatar_radius_48dp)
                    loadAvatar(account.avatar, avatar, avatarRadius, animateAvatars)
                    avatarBadge.visible(showBotOverlay && account.bot)

                    // TODO: These should call methods in the adapter, and pass the position
                    // not the suggestion. The adapter should convert the position to the suggestion
                    // and call the fragment callback with the suggestion.
                    // This allows these calls to move to an init{} block in the ViewHolder instead
                    // of re-setting the click listeners each time.
                    // See https://proandroiddev.com/recyclerview-antipatterns-8af3feeeccc7
                    //
                    // What if... the adapter exposed a flow of UiAction the fragment routed
                    // straight to the ViewModel? Then no callbacks.
                    //
                    // Not all actions need to go to the viewmodel though. Anything that's navigational:
                    // - View account
                    // - View link
                    // - View tag
                    // should be handled by the fragment to fire off the correct intent.
                    //
                    // So... pipeline of flows? VH emits actions to a flow provided by the fragment.
                    // Fragment collects those, and either acts on them (navigational) or forwards
                    // to the ViewModel accept flow?
                    acceptButton.setOnClickListener { accept(SuggestionAction.FollowAccount(suggestion.account)) }
                    rejectButton.setOnClickListener { accept(SuggestionAction.DeleteSuggestion(suggestion)) }
                    accountNote.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }
                    root.setOnClickListener { accept(NavigationAction.ViewAccount(suggestion.account.id)) }
                }
            }
        }
    }
}

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
    is UiError.FollowAccount -> R.string.ui_error_follow_account_fmt
}
