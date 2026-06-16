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
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.ViewGroupCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.formatNumber
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.ICollectionsRepository
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.model.Account
import app.pachli.core.model.ICollection
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.PronounDisplay
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.OperationCounter
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.nameContentDescription
import app.pachli.core.ui.loadAvatar
import app.pachli.feature.collections.ICollectionViewModel.CollectionViewData
import app.pachli.feature.collections.ICollectionViewModel.UiAction
import app.pachli.feature.collections.ICollectionViewModel.UiError
import app.pachli.feature.collections.ICollectionViewModel.UiSuccess
import app.pachli.feature.collections.databinding.ActivityCollectionBinding
import app.pachli.feature.collections.databinding.ItemAccountBinding
import com.bumptech.glide.RequestManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Displays the details for a single [app.pachli.core.model.Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionActivity : BaseActivity() {
    private val binding by viewBinding(ActivityCollectionBinding::inflate)

    private val viewModel by viewModels<CollectionViewModel>()

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)

        setContentView(binding.root)

        viewModel.accept(
            UiAction.GetCollection(
                pachliAccountId,
                CollectionActivityIntent.getCollection(intent).serverId,
            ),
        )
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
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

internal data class AccountViewData(
    val account: Account,
)

/** Displays an account in a collection. */
internal class AccountViewHolder(
    internal val binding: ItemAccountBinding,
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
            avatarRadius = avatar.context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.avatar_radius_48dp)
        }
    }

    fun bind(viewData: AccountViewData, animateEmojis: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean, showPronouns: Boolean, linksToUnderline: kotlin.collections.Set<app.pachli.core.preferences.LinksToUnderline>) {
        val account = viewData.account

        with(binding) {
            username.text = username.context.getString(app.pachli.core.designsystem.R.string.post_username_format, account.username)

            bindAvatar(viewData, animateAvatars)
            bindDisplayName(viewData, animateEmojis)
            bindNote(viewData, animateEmojis, linksToUnderline)
            bindShowBotOverlay(viewData, showBotOverlay)
            bindShowPronouns(viewData, showPronouns)
            bindPostStatistics(viewData)
//            bindIsEnabled(viewData.isEnabled)

            // Build an accessible content description.
            root.contentDescription = root.context.getString(
                app.pachli.core.ui.R.string.account_content_description_fmt,
                account.nameContentDescription(root.context),
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
        loadAvatar(glide, viewData.account.avatar, avatar, avatarRadius, animateAvatars)
    }

    /**
     * Binds the account's [name][app.pachli.core.model.Account.name] respecting
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
     * Binds the account's [note][app.pachli.core.model.Account.note] respecting
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
                val then = account.createdAt!!.toInstant()
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

    sealed interface UiSuccess {
        val action: CollectionAction
    }

    data class CollectionViewData(
        val collection: ICollection,
        val accounts: List<Account>,
    )

    sealed interface UiError : PachliError {
        @JvmInline
        value class GetCollection(private val error: ICollectionsRepository.Error.GetCollection) : UiError, PachliError by error
    }

    val accept: (UiAction) -> Unit

    val uiResult: Flow<Result<UiSuccess, UiError>>

    val operationCount: Flow<Int>

    val collectionViewData: StateFlow<Result<Loadable<CollectionViewData>, UiError.GetCollection>>

    val uiOptions: Flow<UiOptions>
}

@HiltViewModel
internal class CollectionViewModel @Inject constructor(
    private val collectionsRepository: ICollectionsRepository,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel(), ICollectionViewModel {
    private val uiAction = MutableSharedFlow<UiAction>()
    override val accept: (UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    override val uiResult = _uiResult.receiveAsFlow()

    private val operationCounter = OperationCounter()
    override val operationCount = operationCounter.count

    private val reload = MutableSharedFlow<Unit>(replay = 1)

    override val uiOptions = stateFlow(viewModelScope, UiOptions.from(statusDisplayOptionsRepository.flow.value)) {
        statusDisplayOptionsRepository.flow.map { UiOptions.from(it) }
            .flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    private val _collectionViewData = MutableStateFlow<Result<Loadable<CollectionViewData>, UiError.GetCollection>>(Ok(Loadable.Loading))
    override val collectionViewData = _collectionViewData.asStateFlow()

    init {
        viewModelScope.launch {
            uiAction.filterIsInstance<UiAction.GetCollection>().collect {
                launch { onGetCollection(it) }
            }
        }
    }

    private suspend fun onGetCollection(action: UiAction.GetCollection) {
        operationCounter {
            _collectionViewData.value = Ok(Loadable.Loading)
            collectionsRepository.getCollection(action.pachliAccountId, action.collectionId)
                .mapEither(
                    {
                        CollectionViewData(
                            collection = it.first,
                            accounts = it.second!!,
                        )
                    },
                    { UiError.GetCollection(it) },
                )
                .onSuccess { _collectionViewData.value = Ok(Loadable.Loaded(it)) }
                .onFailure { _collectionViewData.value = Err(it) }
        }
    }
}
