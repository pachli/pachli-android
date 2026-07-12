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
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.MenuProvider
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.core.activity.RefreshableFragment
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.flatten
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Account
import app.pachli.core.model.Relationship
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.SetContentAsMarkdown
import app.pachli.core.ui.SetContentAsMastodonHtml
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.contentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.makeIcon
import app.pachli.feature.collections.AccountViewHolder.ChangePayload
import app.pachli.feature.collections.ICollectionViewModel.AccountAction
import app.pachli.feature.collections.ICollectionViewModel.CollectionViewData
import app.pachli.feature.collections.ICollectionViewModel.NavigationAction
import app.pachli.feature.collections.ICollectionViewModel.UiAction
import app.pachli.feature.collections.ICollectionViewModel.UiError
import app.pachli.feature.collections.ICollectionViewModel.UiOptions
import app.pachli.feature.collections.ICollectionViewModel.UiSuccess
import app.pachli.feature.collections.databinding.FragmentCollectionBinding
import app.pachli.feature.collections.databinding.ItemAccountInCollectionBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Displays the members of a single collection.
 */
@AndroidEntryPoint
class CollectionFragment :
    Fragment(R.layout.fragment_collection),
    MenuProvider,
    OnRefreshListener,
    RefreshableFragment,
    ReselectableFragment {
    private val binding by viewBinding(FragmentCollectionBinding::bind)

    private val viewModel: CollectionViewModel by activityViewModels()

    private val pachliAccountId by unsafeLazy { requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID) }

    private val collectionId by unsafeLazy { requireArguments().getString(ARG_COLLECTION_ID)!! }

    private lateinit var collectionAdapter: CollectionAccountsAdapter

    private var talkBackWasEnabled = false

    private val iconSize by unsafeLazy { resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.preference_icon_size) }

    /** Flow of actions the user has taken in the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Accepts user actions from UI components and emits them in to [uiAction]. */
    private val accept: (UiAction) -> Unit = { action -> lifecycleScope.launch { uiAction.emit(action) } }

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.applyDefaultWindowInsets()

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

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

        viewModel.accept(UiAction.LoadCollection(pachliAccountId, collectionId))
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiOptions.collectLatest(::bindUiOptions) }

                launch { uiAction.throttleFirst().collect(::bindUiAction) }

                launch { viewModel.collectionViewData.collectLatest(::bindCollectionViewData) }

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

    /** Process user actions. */
    private fun bindUiAction(uiAction: UiAction) {
        when (uiAction) {
            is NavigationAction -> {
                when (uiAction) {
                    is NavigationAction.ViewAccount -> startActivityWithTransition(
                        AccountActivityIntent(requireContext(), pachliAccountId, uiAction.accountId),
                        TransitionKind.SLIDE_FROM_END,
                    )

                    is NavigationAction.ViewHashtag -> startActivityWithTransition(
                        TimelineActivityIntent.hashtag(
                            requireContext(),
                            pachliAccountId,
                            uiAction.hashtag,
                        ),
                        TransitionKind.SLIDE_FROM_END,
                    )

                    is NavigationAction.ViewUrl -> (requireActivity() as? ViewUrlActivity)?.viewUrl(
                        pachliAccountId,
                        uiAction.url,
                    )

                    is NavigationAction.ConfirmCollectionRevoke -> {
                        lifecycleScope.launch {
                            val button = requireActivity()
                                .newConfirmRevokeDialogFragment()
                                .await(childFragmentManager)

                            if (button == AlertDialog.BUTTON_POSITIVE) {
                                viewModel.accept(uiAction.action)
                            }
                        }
                    }

                    is NavigationAction.ConfirmUnfollowAccount -> {
                        lifecycleScope.launch {
                            val button = requireActivity()
                                .newConfirmUnfollowAccountDialogFragment()
                                .await(childFragmentManager)

                            if (button == AlertDialog.BUTTON_POSITIVE) {
                                viewModel.accept(uiAction.action)
                            }
                        }
                    }
                }
            }

            else -> viewModel.accept(uiAction)
        }
    }

    private fun bindCollectionViewData(result: Result<Loadable<CollectionViewData>, UiError.GetCollection>) {
        binding.swipeRefreshLayout.isRefreshing = false
        Timber.d("bindCollectionViewData: $result")

        result.onFailure {
            binding.messageView.show()
            binding.recyclerView.hide()
            binding.messageView.setup(it) { viewModel.accept(UiAction.LoadCollection(pachliAccountId, collectionId)) }
        }

        result.onSuccess {
            val collectionViewData = it.getOrNull() ?: return

            collectionAdapter.submitList(collectionViewData.accounts)
            binding.messageView.hide()
            binding.recyclerView.show()
        }
    }

    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
        uiResult.onFailure { uiError ->
            val message = uiError.fmt(requireContext())
            snackbar?.dismiss()
            try {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).apply {
                    setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.accept(uiError.action) }
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
        menuInflater.inflate(R.menu.fragment_collection, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = makeIcon(requireContext(), GoogleMaterial.Icon.gmd_refresh, iconSize)
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
        viewModel.accept(UiAction.Reload(pachliAccountId, collectionId))
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

/**
 * Manage a list of accounts from a collection, with actions the user
 * can perform on the accounts (follow, etc).
 */
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

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int, payloads: List<Any?>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        payloads.flatten().filterIsInstance<ChangePayload>().forEach { payload ->
            when (payload) {
                is ChangePayload.IsEnabled -> holder.bindIsEnabled(payload.isEnabled)
                is ChangePayload.PrimaryAction -> holder.bindControls(payload.primaryAction)
            }
        }
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
 * @property pachliAccountId
 * @property collectionId ID of the collection the account is in.
 * @property account The account.
 * @property relationship The current user's relationship to the account.
 * Null if fetching the relationship data failed.
 * @property isEnabled If false the user should not be able to interact with
 * the account (e.g., because there is an active network operation going on
 * that affects this account).
 * @property isSelf True if this is the user's account.
 * @property primaryAction If non-null, the primary [UiAction] for this
 * item. If null this item has no action.
 */
internal data class AccountViewData(
    val pachliAccountId: Long,
    val collectionId: String,
    val account: Account,
    val relationship: Relationship?,
    val isEnabled: Boolean,
    val isSelf: Boolean,
    val primaryAction: AccountAction?,
)

/** Displays a single account in a collection. */
internal class AccountViewHolder(
    internal val binding: ItemAccountInCollectionBinding,
    private val glide: RequestManager,
    private val setContent: SetContent,
    private val accept: (UiAction) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private lateinit var viewData: AccountViewData

    private val avatarRadius: Int

    internal sealed interface ChangePayload {
        data class IsEnabled(val isEnabled: Boolean) : ChangePayload
        data class PrimaryAction(val primaryAction: AccountAction?) : ChangePayload
    }

    /**
     * Link listener for [setClickableText] that generates the appropriate
     * navigation actions.
     */
    private val linkListener = object : LinkListener {
        override fun onViewTag(tag: String) = accept(NavigationAction.ViewHashtag(tag))
        override fun onViewAccount(accountId: String) = accept(NavigationAction.ViewAccount(accountId))
        override fun onViewUrl(url: String) = accept(NavigationAction.ViewUrl(url))
    }

    private var primaryAction: AccountAction? = null

    init {
        with(binding) {
            accountNote.setOnClickListener { accept(NavigationAction.ViewAccount(viewData.account.serverId)) }
            root.setOnClickListener { accept(NavigationAction.ViewAccount(viewData.account.serverId)) }

            avatarRadius = avatar.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)

            actionButton.setOnClickListener {
                val primaryAction = this@AccountViewHolder.primaryAction ?: return@setOnClickListener

                when (primaryAction) {
                    is AccountAction.CancelFollowRequest -> TODO()
                    is AccountAction.UnfollowAccount -> accept(NavigationAction.ConfirmUnfollowAccount(primaryAction))
                    is AccountAction.Revoke -> accept(NavigationAction.ConfirmCollectionRevoke(primaryAction))
                    else -> accept(primaryAction)
                }
            }
        }
    }

    internal fun bind(viewData: AccountViewData, animateEmojis: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean, showPronouns: Boolean, linksToUnderline: Set<LinksToUnderline>) {
        this.viewData = viewData
        val account = viewData.account

        with(binding) {
            username.text = username.context.getString(DR.string.post_username_format, account.username)

            bindAvatar(viewData, animateAvatars)
            bindRoles(viewData)
            bindDisplayName(viewData, animateEmojis)
            bindNote(viewData, animateEmojis, linksToUnderline)
            bindShowBotOverlay(viewData, showBotOverlay)
            bindShowPronouns(viewData, showPronouns)
            bindPostStatistics(viewData)
            bindIsEnabled(viewData.isEnabled)
            bindControls(viewData.primaryAction)

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

    /** Enables or disables all views depending on [isEnabled]. */
    internal fun bindIsEnabled(isEnabled: Boolean) = with(binding) {
        (root as? ViewGroup)?.children?.forEach { it.isEnabled = isEnabled }
        root.isEnabled = isEnabled
    }

    /** Binds the avatar image, respecting [animateAvatars]. */
    private fun bindAvatar(viewData: AccountViewData, animateAvatars: Boolean) = with(binding) {
        loadAvatar(glide, viewData.account.avatar, avatar, avatarRadius, animateAvatars)
    }

    /** Binds the account's [roles][Account.roles]. */
    private fun bindRoles(viewData: AccountViewData) = with(binding) {
        roleChipGroup.setRoles(viewData.account.roles)
    }

    /**
     * Binds the account's [name][Account.name] respecting
     * [animateEmojis].
     */
    private fun bindDisplayName(viewData: AccountViewData, animateEmojis: Boolean) = with(binding) {
        val account = viewData.account
        displayName.text = account.name.unicodeWrap().emojify(glide, account.emojis, itemView, animateEmojis)
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
                emojis = account.emojis,
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

    internal fun bindControls(primaryAction: AccountAction?) {
        this.primaryAction = primaryAction

        if (primaryAction == null) {
            // Hide controls, show error message
            binding.actionButton.hide()
            return
        }

        binding.actionButton.text = primaryAction.text(binding.actionButton.context)
        binding.actionButton.setIconResource(primaryAction.getDrawableRes())
        binding.actionButton.show()
    }

    /**
     * @return Text to use on the action button, derived from [AccountAction].
     */
    private fun AccountAction.text(context: Context) = when (this) {
        is AccountAction.UnblockAccount -> context.getString(app.pachli.core.ui.R.string.action_unblock)
        is AccountAction.BlockDomain -> context.getString(
            app.pachli.core.ui.R.string.action_unmute_domain,
            this.account.domain,
        )
        is AccountAction.CancelFollowRequest -> context.getString(
            app.pachli.core.ui.R.string.state_follow_requested,
        )
        is AccountAction.FollowAccount -> context.getString(app.pachli.core.ui.R.string.action_follow_account)
        is AccountAction.Revoke -> context.getString(app.pachli.core.ui.R.string.action_collection_remove_self)
        is AccountAction.UnfollowAccount -> context.getString(app.pachli.core.ui.R.string.action_unfollow)
        is AccountAction.UnmuteAccount -> context.getString(app.pachli.core.ui.R.string.action_unmute)
    }

    /**
     * @return Drawable resource to use on the action button, derived from
     * [AccountAction]. Returns 0 if there is no icon associated with the
     * action.
     */
    @DrawableRes
    private fun AccountAction.getDrawableRes() = when (this) {
        is AccountAction.CancelFollowRequest -> 0
        is AccountAction.FollowAccount -> app.pachli.core.ui.R.drawable.ic_person_add_24dp
        is AccountAction.Revoke -> app.pachli.core.ui.R.drawable.outline_delete_24
        is AccountAction.UnblockAccount -> 0
        is AccountAction.BlockDomain -> 0
        is AccountAction.UnfollowAccount -> app.pachli.core.ui.R.drawable.ic_person_remove_24dp
        is AccountAction.UnmuteAccount -> app.pachli.core.ui.R.drawable.ic_unmute_24dp
    }
}

private object AccountInCollectionViewDataDiffer : DiffUtil.ItemCallback<AccountViewData>() {
    override fun areItemsTheSame(oldItem: AccountViewData, newItem: AccountViewData) = oldItem.account.serverId == newItem.account.serverId
    override fun areContentsTheSame(oldItem: AccountViewData, newItem: AccountViewData) = oldItem == newItem

    override fun getChangePayload(oldItem: AccountViewData, newItem: AccountViewData): Any? {
        return buildList {
            if (oldItem.isEnabled != newItem.isEnabled) add(ChangePayload.IsEnabled(newItem.isEnabled))
            if (oldItem.primaryAction != newItem.primaryAction) add(ChangePayload.PrimaryAction(newItem.primaryAction))

            add(super.getChangePayload(oldItem, newItem))
        }
    }
}
