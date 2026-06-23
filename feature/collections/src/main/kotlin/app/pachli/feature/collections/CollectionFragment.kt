/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.collections

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ICollectionsRepository
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.RelationshipsRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.data.repository.mapLoaded
import app.pachli.core.designsystem.R
import app.pachli.core.domain.accounts.FollowAccountUseCase
import app.pachli.core.model.Account
import app.pachli.core.model.Collection
import app.pachli.core.model.ICollection
import app.pachli.core.model.Relationship
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.OperationCounter
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.SetContentAsMarkdown
import app.pachli.core.ui.SetContentAsMastodonHtml
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.contentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.feature.collections.ICollectionViewModel.AccountAction
import app.pachli.feature.collections.ICollectionViewModel.CollectionViewData
import app.pachli.feature.collections.ICollectionViewModel.UiAction
import app.pachli.feature.collections.ICollectionViewModel.UiError
import app.pachli.feature.collections.ICollectionViewModel.UiSuccess
import app.pachli.feature.collections.databinding.FragmentCollectionBinding
import app.pachli.feature.collections.databinding.ItemAccountInCollectionBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Displays the details for a single [Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionFragment :
    Fragment(app.pachli.feature.collections.R.layout.fragment_collection),
    SwipeRefreshLayout.OnRefreshListener,
    ReselectableFragment {
    private val binding by viewBinding(FragmentCollectionBinding::bind)

    private val viewModel: CollectionViewModel by activityViewModels()

    private val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

    private val collectionId by unsafeLazy { requireArguments().getString(ARG_COLLECTION_ID)!! }

    private lateinit var collectionAdapter: CollectionAccountsAdapter

    private var talkBackWasEnabled = false

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emits them in to [uiAction]. */
    private val accept: (UiAction) -> Unit = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.applyDefaultWindowInsets()

        val setContent = if (viewModel.uiOptions.value.renderMarkdown) {
            SetContentAsMarkdown(requireContext())
        } else {
            SetContentAsMastodonHtml
        }

        collectionAdapter = CollectionAccountsAdapter(
            glide = Glide.with(this),
            setContent = setContent,
            animateAvatars = viewModel.uiOptions.value.animateAvatars,
            animateEmojis = viewModel.uiOptions.value.animateEmojis,
            showBotOverlay = viewModel.uiOptions.value.showBotOverlay,
            showPronouns = viewModel.uiOptions.value.showPronouns,
            linksToUnderline = viewModel.uiOptions.value.linksToUnderline,
            accept = accept,
        )

        with(binding.swipeRefreshLayout) {
            isEnabled = true
            setOnRefreshListener(this@CollectionFragment)
            setColorSchemeColors(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        }

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = collectionAdapter
            addItemDecoration(MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL))
            setHasFixedSize(true)

//            setAccessibilityDelegateCompat(SuggestionAccessibilityDelegate(this, accept))
        }

        bind()

        viewModel.accept(UiAction.GetCollection(pachliAccountId, collectionId))
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiOptions.collectLatest(::bindUiOptions) }

                launch { uiAction.throttleFirst().collect(::bindUiAction) }

                launch { viewModel.collectionViewData.collectLatest(::bindCollection) }

                launch { viewModel.uiResult.collect(::bindUiResult) }

                launch {
                    viewModel.operationCount.collectLatest {
                        if (it == 0) binding.progressIndicator.hide() else binding.progressIndicator.show()
                    }
                }
            }
        }
    }

    private fun bindUiOptions(uiOptions: UiOptions) {}

    private fun bindUiAction(uiAction: UiAction) {}

    private fun bindCollection(result: Result<Loadable<CollectionViewData>, UiError.GetCollection>) {
        binding.swipeRefreshLayout.isRefreshing = false

        result.onFailure {
            binding.messageView.show()
            binding.recyclerView.hide()
            binding.messageView.setup(it) { viewModel.accept(UiAction.GetCollection(pachliAccountId, collectionId)) }
        }

        result.onSuccess {
            it.getOrNull()?.let { collectionViewData ->
                // Update toolbar
                // - Owner avatar
                // - Collection name
                // - Collection owner handle

                // Update description, above the list
                // Update hashtag, above the list

                // Control to-reorder list?

                collectionAdapter.submitList(collectionViewData.accounts)
                binding.messageView.hide()
                binding.recyclerView.show()
            }
        }
    }

    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {}

    private fun bindUiResult() {}

    override fun onRefresh() {
        snackbar?.dismiss()
        viewModel.accept(UiAction.GetCollection(pachliAccountId, collectionId))
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
            collectionAdapter.notifyItemRangeChanged(0, collectionAdapter.itemCount)
        }
    }

    companion object {
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
        private const val ARG_COLLECTION_ID = "app.pachli.ARG_COLLECTION_ID"

        fun newInstance(pachliAccountId: Long, collectionId: String): CollectionFragment {
            return CollectionFragment().apply {
                arguments = Bundle(2).apply {
                    putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
                    putString(ARG_COLLECTION_ID, collectionId)
                }
            }
        }

        fun fragmentTag(pachliAccountId: Long, collectionId: String) = "CollectionFragment:$pachliAccountId:$collectionId"
    }
}

// TODO: Very similar to SuggestionsAdapter
internal class CollectionAccountsAdapter(
    private val glide: RequestManager,
    private val setContent: SetContent,
    private var animateAvatars: Boolean,
    private var animateEmojis: Boolean,
    private var showBotOverlay: Boolean,
    private var showPronouns: Boolean,
    private var linksToUnderline: Set<LinksToUnderline>,
    private val accept: (UiAction) -> Unit,
) : ListAdapter<AccountViewData, AccountViewHolder>(AccountInCollectionViewDataDiffer) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountInCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(binding, glide, setContent, accept)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(
            currentList[position],
            animateEmojis,
            animateAvatars,
            showBotOverlay,
            showPronouns,
            linksToUnderline,
        )
    }
}

/**
 * Data to show an account in a collection.
 *
 * @property account The account.
 * @property relationship Relationship to the current user. Null if fetching
 * the relationship failed.
 * @property isEnabled If false the user should not be able to interact with
 * the account (e.g., because there is an active network operation going on
 * that affects this account).
 * @property isSelf True if this is the user's account.
 */
internal data class AccountViewData(
    val account: Account,
    val relationship: Relationship?,
    val isEnabled: Boolean,
    val isSelf: Boolean,
)

/** Displays an account in a collection. */
internal class AccountViewHolder(
    internal val binding: ItemAccountInCollectionBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val accept: (UiAction) -> Unit,

) : RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius: Int

    /**
     * Link listener for [setClickableText] that generates the appropriate
     * navigation actions.
     */
    private val linkListener = object : LinkListener {
        override fun onViewTag(tag: String) = accept(ICollectionViewModel.NavigationAction.ViewHashtag(tag))
        override fun onViewAccount(id: String) = accept(ICollectionViewModel.NavigationAction.ViewAccount(id))
        override fun onViewUrl(url: String) = accept(ICollectionViewModel.NavigationAction.ViewUrl(url))
    }

    init {
        with(binding) {
            avatarRadius = avatar.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        }
    }

    fun bind(viewData: AccountViewData, animateEmojis: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean, showPronouns: Boolean, linksToUnderline: Set<LinksToUnderline>) {
        val account = viewData.account

        Timber.d("relationship: ${viewData.relationship}")

        with(binding) {
            username.text = username.context.getString(R.string.post_username_format, account.username)

            bindAvatar(viewData, animateAvatars)
            bindRoles(viewData)
            bindDisplayName(viewData, animateEmojis)
            bindNote(viewData, animateEmojis, linksToUnderline)
            bindShowBotOverlay(viewData, showBotOverlay)
            bindShowPronouns(viewData, showPronouns)
            bindPostStatistics(viewData)
//            bindIsEnabled(viewData.isEnabled)

            // Bind the controls
            //
            // - Follow button
            //   - Show if not following the account
            // - "Remove me" button
            //   - Show if this is me
            bindControls(viewData)

            // Build an accessible content description.
            root.contentDescription = root.context.getString(
                app.pachli.core.ui.R.string.account_content_description_fmt,
                account.contentDescription(root.context),
                followerCount.text,
                followsCount.text,
                statusesCount.text,
                accountNote.text,
            )

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            root.accessibilityDelegate = null
        }
    }

    /** Binds the avatar image, respecting [animateAvatars]. */
    private fun bindAvatar(viewData: AccountViewData, animateAvatars: Boolean) = with(binding) {
        loadAvatar(
            glide,
            viewData.account.avatar,
            avatar,
            avatarRadius,
            animateAvatars,
        )
    }

    /** Binds the account's [roles][app.pachli.core.model.Account.roles]. */
    private fun bindRoles(viewData: AccountViewData) = with(binding) {
        roleChipGroup.setRoles(viewData.account.roles)
    }

    /**
     * Binds the account's [name][Account.name] respecting
     * [animateEmojis].
     */
    private fun bindDisplayName(viewData: AccountViewData, animateEmojis: Boolean) = with(binding) {
        val account = viewData.account
        displayName.text = account.name.unicodeWrap().emojify(
            glide,
            account.emojis,
            itemView,
            animateEmojis,
        )
    }

    /**
     * Binds the account's [note][Account.note] respecting
     * [animateEmojis] and [linksToUnderline].
     */
    private fun bindNote(viewData: AccountViewData, animateEmojis: Boolean, linksToUnderline: Set<LinksToUnderline>) = with(binding) {
        val account = viewData.account

        if (account.note.isBlank()) {
            @SuppressLint("SetTextI18n")
            accountNote.text = ""
            accountNote.hide()
        } else {
            setContent(
                glide = glide,
                textView = accountNote,
                content = account.note,
                emojis = account.emojis.orEmpty(),
                animateEmojis = animateEmojis,
                removeQuoteInline = false,
                linksToUnderline = linksToUnderline,
                linkListener = linkListener,
            )

            accountNote.show()
        }
    }

    /**
     * Display's the bot overlay on the avatar image (if appropriate), respecting
     * [showBotOverlay].
     */
    private fun bindShowBotOverlay(viewData: AccountViewData, showBotOverlay: Boolean) = with(binding) {
        avatarBadge.visible(viewData.account.bot && showBotOverlay)
    }

    private fun bindShowPronouns(viewData: AccountViewData, showPronouns: Boolean) = with(binding) {
        if (showPronouns) accountPronouns.text = viewData.account.pronouns
        accountPronouns.visible(showPronouns && viewData.account.pronouns?.isBlank() == false)
    }

    /** Bind's the account's post statistics. */
    private fun bindPostStatistics(viewData: AccountViewData) = with(binding) {
        val account = viewData.account

        // Strings all have embedded HTML `<b>...</b>` to render different sections in bold
        // without needing to compute spannable widths from arbitrary content. The `<b>` in
        // the resource strings must have the leading `<` escaped as `&lt;`.

        followerCount.text = HtmlCompat.fromHtml(
            followerCount.context.getString(
                app.pachli.core.ui.R.string.follower_count_fmt,
                formatNumber(account.followersCount.toLong(), 1000),
            ),
            FROM_HTML_MODE_LEGACY,
        )

        followsCount.text = HtmlCompat.fromHtml(
            followsCount.context.getString(
                app.pachli.core.ui.R.string.follows_count_fmt,
                formatNumber(account.followingCount.toLong(), 1000),
            ),
            FROM_HTML_MODE_LEGACY,
        )

        // statusesCount can be displayed as either:
        //
        // 1. A count of posts (if the account has no creation date).
        // 2. (1) + a breakdown of posts per week (if there is no "last post" date).
        // 3. (1) + (2) + when the account last posted.
        statusesCount.apply {
            if (account.createdAt == null) {
                text = HtmlCompat.fromHtml(
                    context.getString(
                        app.pachli.core.ui.R.string.statuses_count_fmt,
                        formatNumber(account.statusesCount.toLong(), 1000),
                    ),
                    FROM_HTML_MODE_LEGACY,
                )
            } else {
                val then = account.createdAt!!
                val now = Instant.now()
                val elapsed = Duration.between(then, now).toDays() / 7.0

                if (account.lastStatusAt == null) {
                    text = HtmlCompat.fromHtml(
                        context.getString(
                            app.pachli.core.ui.R.string.statuses_count_per_week_fmt,
                            formatNumber(account.statusesCount.toLong(), 1000),
                            (account.statusesCount / elapsed).roundToInt(),
                        ),
                        FROM_HTML_MODE_LEGACY,
                    )
                } else {
                    text = HtmlCompat.fromHtml(
                        context.getString(
                            app.pachli.core.ui.R.string.statuses_count_per_week_last_fmt,
                            formatNumber(account.statusesCount.toLong(), 1000),
                            (account.statusesCount / elapsed).roundToInt(),
                            DateUtils.getRelativeTimeSpanString(
                                account.lastStatusAt!!.time,
                                now.toEpochMilli(),
                                DateUtils.DAY_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ),
                        ),
                        FROM_HTML_MODE_LEGACY,
                    )
                }
            }
        }
    }

    private fun bindControls(viewData: AccountViewData) {
        val relationship = viewData.relationship

        if (relationship == null) {
            // Hide controls, show error message
            binding.actionButton.hide()
            // TODO: Show error message
            return
        }

        binding.actionButton.show()

        if (relationship.blocking) {
            // TODO: Move string action_unblock
            binding.actionButton.setText(app.pachli.core.ui.R.string.action_unblock)
            // TODO: Set click handler
            return
        }

        if (relationship.blockingDomain) {
            // TODO: This should show the actual domain, which needs to be
            // accessible from ITimelineAccount
            binding.actionButton.setText("Unblock domin")
            // TODO: Set click handler
            return
        }

        // TODO: If this is us, button should be "Remove me"
        if (viewData.isSelf) {
            // TODO: String name should be action_collection_remove_self
            binding.actionButton.setText(app.pachli.core.ui.R.string.action_collection_remove_self)
            // TODO: Set click handler
            return
        }

        when (relationship.followState) {
            Relationship.FollowState.NOT_FOLLOWING -> {
                // TODO: Move string action_follow
                binding.actionButton.setText(app.pachli.core.ui.R.string.action_follow_account)
                // TODO: Set click handler
            }

            Relationship.FollowState.FOLLOWING -> {
                // TODO: Move string action_unfollow
                binding.actionButton.setText(app.pachli.core.ui.R.string.action_unfollow_account)
                // TODO: Set click handler
            }

            Relationship.FollowState.REQUESTED -> {
                // TODO: Move string state_follow_requested
                binding.actionButton.setText(app.pachli.core.ui.R.string.state_follow_requested)
                // TODO: Set click handler
            }
        }

        // Relationship options
        // - following, true/false
        // - followedBy, true/false (shows as chip in AccountActivity)
        // - blocking, true/false
        // - blockedBy, true/false
        // - muting, true/false
        // - requested (pending follow request), true/false
        // - domainBlocking, true/false

        // Possible buttons to show
        // - Follow
        // - Unfollow
        // - Request follow
        // - Cancel follow request
        // - Block
        // - Unblock
        // - Mute
        // - Unmute

        // Should be similar logic in AccountActivity
        // AccountActivity.updateFollowButton
    }
}

private object AccountInCollectionViewDataDiffer : DiffUtil.ItemCallback<AccountViewData>() {
    override fun areItemsTheSame(oldItem: AccountViewData, newItem: AccountViewData) = oldItem.account.id == newItem.account.id

    override fun areContentsTheSame(oldItem: AccountViewData, newItem: AccountViewData) = oldItem == newItem
}

internal data class UiOptions(
    val animateEmojis: Boolean = false,
    val animateAvatars: Boolean = false,
    val showBotOverlay: Boolean = false,
    val showPronouns: Boolean = false,
    val linksToUnderline: Set<LinksToUnderline> = emptySet(),
    val renderMarkdown: Boolean = false,
) {
    companion object {
        fun from(statusDisplayOptions: StatusDisplayOptions) = UiOptions(
            animateEmojis = statusDisplayOptions.animateEmojis,
            animateAvatars = statusDisplayOptions.animateAvatars,
            showBotOverlay = statusDisplayOptions.showBotOverlay,
            showPronouns = statusDisplayOptions.pronounDisplay == PronounDisplay.EVERYWHERE,
            linksToUnderline = statusDisplayOptions.linksToUnderline,
            renderMarkdown = statusDisplayOptions.renderMarkdown,
        )
    }
}

internal interface ICollectionViewModel {
    sealed interface UiAction {
        data class GetCollection(
            val pachliAccountId: Long,
            val collectionId: String,
        ) : UiAction
    }

    sealed interface NavigationAction : UiAction {
        data class ViewAccount(val accountId: String) : NavigationAction
        data class ViewHashtag(val hashtag: String) : NavigationAction
        data class ViewUrl(val url: String) : NavigationAction
    }

    sealed interface CollectionAction : UiAction

    sealed interface AccountAction : CollectionAction {
        val pachliAccountId: Long
        val account: Account

        data class FollowAccount(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction
    }

    sealed interface UiSuccess {
        val action: CollectionAction

        data class FollowAccount(override val action: AccountAction.FollowAccount) : UiSuccess

        companion object {
            fun from(action: AccountAction) = when (action) {
                is AccountAction.FollowAccount -> FollowAccount(action)
            }
        }
    }

    /**
     * @property isMember True if the user's account is a member of this collection.
     */
    data class CollectionViewData(
        val collection: ICollection,
        val owner: AccountViewData?,
        val accounts: List<AccountViewData>,
        val isMember: Boolean,
    )

    sealed class UiError(
        @StringRes override val resourceId: Int,
        open val action: AccountAction,
        override val cause: PachliError,
        override val formatArgs: Array<out String>? = arrayOf(action.account.name),
    ) : PachliError {
        @JvmInline
        value class GetCollection(private val error: ICollectionsRepository.Error.GetCollection) : PachliError by error

        data class FollowAccount(
            override val action: AccountAction.FollowAccount,
            override val cause: PachliError,
        ) : UiError(app.pachli.core.ui.R.string.ui_error_follow_account_fmt, action, cause)

        companion object {
            fun make(error: UiError, action: AccountAction) = when (action) {
                is AccountAction.FollowAccount -> FollowAccount(action, error)
            }
        }
    }

    val accept: (UiAction) -> Unit

    val uiResult: Flow<Result<UiSuccess, UiError>>

    val operationCount: Flow<Int>

    val collectionViewData: StateFlow<Result<Loadable<CollectionViewData>, UiError.GetCollection>>

    val uiOptions: Flow<UiOptions>
}

@HiltViewModel
internal class CollectionViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val collectionsRepository: ICollectionsRepository,
    private val relationshipsRepository: RelationshipsRepository,
    private val followAccountUseCase: FollowAccountUseCase,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel(), ICollectionViewModel {
    private val uiAction = MutableSharedFlow<UiAction>()
    override val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    override val uiResult = _uiResult.receiveAsFlow()

    private val operationCounter = OperationCounter()
    override val operationCount = operationCounter.count

    private val pachliAccountId = MutableSharedFlow<Long>(replay = 1)
    private val pachliAccount = pachliAccountId.distinctUntilChanged().flatMapLatest {
        accountManager.getPachliAccountFlow(it).filterNotNull()
    }

    override val uiOptions = stateFlow(viewModelScope, UiOptions.from(statusDisplayOptionsRepository.flow.value)) {
        statusDisplayOptionsRepository.flow.map { UiOptions.from(it) }
            .flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    private val collection = MutableStateFlow<Result<Loadable<Pair<Collection, List<Account>>>, ICollectionsRepository.Error.GetCollection>>(Ok(Loadable.Loading))

    /**
     * Map from [account IDs][app.pachli.core.model.ITimelineAccount.id] to the
     * [Relationship] to that account.
     */
    private val relationships = MutableStateFlow<Map<String, Relationship>>(emptyMap())

    /**
     * IDs of accounts the user can't interact with (e.g., because they have active
     * network operations).
     */
    private val disabledAccountIds = MutableStateFlow<Set<String>>(emptySet())

    // Combines the pachliAccount, the most recent collection data, relationships, and
    // disabled accounts to produce the CollectionViewData.
    override val collectionViewData = stateFlow(viewModelScope, Ok(Loadable.Loading)) {
        combine(pachliAccount.distinctUntilChanged(), collection, relationships, disabledAccountIds) { pachliAccount, collectionResult, relationships, disabledAccountIds ->
            collectionResult.map {
                it.mapLoaded { (collection, accounts) ->
                    val owner = accounts.firstOrNull()
                    val members = accounts.drop(1)
                    CollectionViewData(
                        collection = collection,
                        owner = owner?.let {
                            AccountViewData(
                                account = owner,
                                relationship = relationships[owner.id],
                                isEnabled = !disabledAccountIds.contains(it.id),
                                isSelf = owner.id == pachliAccount.accountId,
                            )
                        },
                        accounts = members.map {
                            AccountViewData(
                                account = it,
                                relationship = relationships[it.id],
                                isEnabled = !disabledAccountIds.contains(it.id),
                                isSelf = it.id == pachliAccount.accountId,
                            )
                        },
                        isMember = members.any { it.id == pachliAccount.accountId },
                    )
                }
            }.mapError { UiError.GetCollection(it) }
        }.flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    init {
        viewModelScope.launch {
//            uiAction.filterIsInstance<UiAction.GetCollection>().collect {
//                launch { onGetCollection(it) }
//            }

            uiAction.filterIsInstance<UiAction.GetCollection>().collect(::onGetCollection)
            uiAction.filterIsInstance<AccountAction>().collect(::onAccountAction)
        }
    }

    private suspend fun onGetCollection(action: UiAction.GetCollection) {
        operationCounter {
            pachliAccountId.emit(action.pachliAccountId)

            collection.emit(Ok(Loadable.Loading))
            collectionsRepository.getCollection(action.pachliAccountId, action.collectionId)
                .onSuccess { (_, accounts) ->
                    relationshipsRepository.getRelationships(
                        action.pachliAccountId,
                        accounts.map { it.id },
                    ).onSuccess { relationships ->
                        this.relationships.update { relationships.associateBy { it.id } }
                    }
                }
            collection.emit(
                collectionsRepository.getCollection(action.pachliAccountId, action.collectionId)
                    .map { Loadable.Loaded(it) },
            )
        }
    }

    private suspend fun onAccountAction(action: AccountAction) {
        disabledAccountIds.update { it + action.account.id }

        val result = when (action) {
            is AccountAction.FollowAccount -> onFollowAccount(action)
        }.onSuccess { relationship ->
            relationships.update { it + (action.account.id to relationship) }
        }.mapEither(
            { UiSuccess.from(action) },
            { UiError.make(it, action) },
        )

        _uiResult.send(result)
        disabledAccountIds.update { it - action.account.id }
    }

    private suspend fun onFollowAccount(action: AccountAction.FollowAccount) = operationCounter {
        followAccountUseCase(action.pachliAccountId, action.account.id)
            .mapError { UiError.FollowAccount(action, it) }
    }
}
