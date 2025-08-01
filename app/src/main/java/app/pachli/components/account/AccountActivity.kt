/* Copyright 2018 Conny Duck
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

package app.pachli.components.account

import android.animation.ArgbEvaluator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.MarginPageTransformer
import app.pachli.R
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Account
import app.pachli.core.model.Relationship
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.EditProfileActivityIntent
import app.pachli.core.navigation.ReportActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.ClipboardUseCase
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.RoleChip
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.core.ui.getDomain
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.setClickableText
import app.pachli.databinding.ActivityAccountBinding
import app.pachli.db.DraftsAlert
import app.pachli.feature.lists.ListsForAccountFragment
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Success
import app.pachli.view.showMuteAccountDialog
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Show a single account's profile details.
 */
@AndroidEntryPoint
class AccountActivity :
    ViewUrlActivity(),
    ActionButtonActivity,
    MenuProvider,
    LinkListener {
    @Inject
    lateinit var draftsAlert: DraftsAlert

    @Inject
    lateinit var clipboard: ClipboardUseCase

    private val viewModel: AccountViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AccountViewModel.Factory> { factory ->
                factory.create(intent.pachliAccountId)
            }
        },
    )

    private val binding by viewBinding(ActivityAccountBinding::inflate)

    override val actionButton: FloatingActionButton?
        get() {
            return if (!blocking) {
                binding.accountFloatingActionButton
            } else {
                null
            }
        }

    private lateinit var accountFieldAdapter: AccountFieldAdapter

    private var followState: FollowState = FollowState.NOT_FOLLOWING
    private var blocking: Boolean = false
    private var muting: Boolean = false
    private var blockingDomain: Boolean = false
    private var showingReblogs: Boolean = false
    private var subscribing: Boolean = false
    private var loadedAccount: Account? = null

    private val animateAvatar by unsafeLazy { sharedPreferencesRepository.animateAvatars }
    private val animateEmojis by unsafeLazy { sharedPreferencesRepository.animateEmojis }

    // fields for scroll animation
    private var hideFab: Boolean = false
    private var oldOffset: Int = 0

    @ColorInt
    private var toolbarColor: Int = 0

    @ColorInt
    private var statusBarColorTransparent: Int = 0

    @ColorInt
    private var statusBarColorOpaque: Int = 0

    private var avatarSize: Float = 0f

    @Px
    private var titleVisibleHeight: Int = 0
    private lateinit var domain: String

    private enum class FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED,
    }

    private lateinit var adapter: AccountPagerAdapter

    private var noteWatcher: TextWatcher? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.accountFragmentViewPager.currentItem = 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        // Apply left/right insets to the main layout, but don't consume, so children
        // can set the top/bottom.
        binding.accountCoordinatorLayout.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
            consume = false,
        )
        // CollapsingToolbarLayout consumes all insets without passing them on to
        // children, which means accountToolbar never sees them. Override that behaviour
        // with a custom inset listener that does nothing but does not consume them.
        binding.collapsingToolbar.applyWindowInsets(consume = false)
        binding.accountToolbar.applyWindowInsets(top = InsetType.PADDING)
        binding.accountFragmentViewPager.applyDefaultWindowInsets()
        binding.accountFloatingActionButton.applyDefaultWindowInsets()

        // Normal swipe spinner startOffset is negative so it slides down from the view
        // above it. This puts it above the systembar and it finishes too high up the
        // screen. Move it down so it starts at the top edge, scale it so it appears to
        // grow instead of appearing out of thin air, and keep the final resting place at
        // the same relative offset to the start.
        val absoluteOffset = binding.swipeRefreshLayout.progressViewEndOffset - binding.swipeRefreshLayout.progressViewStartOffset
        binding.swipeRefreshLayout.setProgressViewOffset(true, 0, absoluteOffset)

        loadResources()
        setContentView(binding.root)
        addMenuProvider(this)

        // Obtain information to fill out the profile.
        viewModel.setAccountInfo(AccountActivityIntent.getAccountId(intent))

        hideFab = sharedPreferencesRepository.hideFabWhenScrolling

        setupToolbar()
        setupTabs()
        setupAccountViews()
        setupRefreshLayout()
        subscribeObservables()

        if (viewModel.isSelf) {
            updateButtons()
            binding.saveNoteInfo.hide()
        } else {
            binding.saveNoteInfo.visibility = View.INVISIBLE
        }

        onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    /**
     * Load colors and dimensions from resources
     */
    private fun loadResources() {
        toolbarColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        statusBarColorTransparent = getColor(DR.color.transparent_statusbar_background)
        statusBarColorOpaque = toolbarColor
        avatarSize = resources.getDimension(DR.dimen.account_activity_avatar_size)
        titleVisibleHeight = resources.getDimensionPixelSize(DR.dimen.account_activity_scroll_title_visible_height)
    }

    /**
     * Setup account widgets visibility and actions
     */
    private fun setupAccountViews() {
        // Initialise the default UI states.
        binding.accountFloatingActionButton.hide()
        binding.accountFollowButton.hide()
        binding.accountMuteButton.hide()
        binding.accountFollowsYouChip.hide()

        // setup the RecyclerView for the account fields
        accountFieldAdapter = AccountFieldAdapter(glide, this, animateEmojis)
        binding.accountFieldList.isNestedScrollingEnabled = false
        binding.accountFieldList.layoutManager = LinearLayoutManager(this)
        binding.accountFieldList.adapter = accountFieldAdapter

        val accountListClickListener = { v: View ->
            val kind = when (v.id) {
                R.id.accountFollowers -> AccountListActivityIntent.Kind.FOLLOWERS
                R.id.accountFollowing -> AccountListActivityIntent.Kind.FOLLOWS
                else -> throw AssertionError()
            }
            val accountListIntent = AccountListActivityIntent(this, intent.pachliAccountId, kind, viewModel.accountId)
            startActivityWithDefaultTransition(accountListIntent)
        }
        binding.accountFollowers.setOnClickListener(accountListClickListener)
        binding.accountFollowing.setOnClickListener(accountListClickListener)

        binding.accountStatuses.setOnClickListener {
            // Make nice ripple effect on tab
            binding.accountTabLayout.getTabAt(0)!!.select()
            val poorTabView = (binding.accountTabLayout.getChildAt(0) as ViewGroup).getChildAt(0)
            poorTabView.isPressed = true
            binding.accountTabLayout.postDelayed({ poorTabView.isPressed = false }, 300)
        }

        // If wellbeing mode is enabled, follow stats and posts count should be hidden
        val wellbeingEnabled = sharedPreferencesRepository.hideStatsInProfile

        if (wellbeingEnabled) {
            binding.accountStatuses.hide()
            binding.accountFollowers.hide()
            binding.accountFollowing.hide()
        }
    }

    /**
     * Init timeline tabs
     */
    private fun setupTabs() {
        // Setup the tabs and timeline pager.
        adapter = AccountPagerAdapter(this, intent.pachliAccountId, viewModel.accountId)

        binding.accountFragmentViewPager.reduceSwipeSensitivity()
        binding.accountFragmentViewPager.adapter = adapter
        binding.accountFragmentViewPager.offscreenPageLimit = 2

        val pageTitles = arrayOf(getString(R.string.title_posts), getString(R.string.title_posts_with_replies), getString(R.string.title_posts_pinned), getString(R.string.title_media))

        TabLayoutMediator(binding.accountTabLayout, binding.accountFragmentViewPager) { tab, position ->
            tab.text = pageTitles[position]
        }.attach()

        val pageMargin = resources.getDimensionPixelSize(DR.dimen.tab_page_margin)
        binding.accountFragmentViewPager.setPageTransformer(MarginPageTransformer(pageMargin))

        binding.accountFragmentViewPager.isUserInputEnabled = sharedPreferencesRepository.enableTabSwipe

        binding.accountTabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(tab: TabLayout.Tab?) {
                    tab?.position?.let { position ->
                        (adapter.getFragment(position) as? ReselectableFragment)?.onReselect()
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.position ?: return
                    onBackPressedCallback.isEnabled = tab.position > 0
                }
            },
        )
    }

    /**
     * Sets the initial state of the toolbar, and adds an
     * [AppBarLayout.OnOffsetChangedListener] to adjust the views as the user
     * scrolls.
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.accountToolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        val appBarElevation = resources.getDimension(DR.dimen.actionbar_elevation)

        /** The background for the toolbar when fully shown. */
        val toolbarBackground = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)

        // Toolbar starts out transparent, and becomes more opaque as the user scrolls.
        binding.accountToolbar.setBackgroundColor(Color.TRANSPARENT)

        // The text on the toolbar starts transparent and becomes more opaque as the
        // user scrolls.
        //
        // This is more difficult than it should be -- MaterialToolbar and parent classes
        // don't expose the TextViews that contain the title and subtitle, and creates
        // them on demand. So first set a title and subtitle to create the views, then
        // find them in the MaterialToolbar's child views.
        supportActionBar?.setDisplayShowTitleEnabled(true)
        val (tvTitle, tvSubtitle) = with(binding.accountToolbar) {
            title = " " // Must be non-empty, otherwise the toolbar ignores the value
            subtitle = " " // Must be non-empty, otherwise the toolbar ignores the value
            val textViews = children.filter { it is TextView }.toList()
            Pair(textViews.getOrNull(0), textViews.getOrNull(1))
        }
        tvTitle?.alpha = 0f
        tvSubtitle?.alpha = 0f

        // Provide a non-transparent background to the navigation and overflow icons to ensure
        // they remain visible over whatever the profile background image might be.
        val backgroundCircle = AppCompatResources.getDrawable(this, R.drawable.background_circle)!!
        backgroundCircle.alpha = 210 // Any lower than this and the backgrounds interfere
        binding.accountToolbar.navigationIcon = LayerDrawable(
            arrayOf(
                backgroundCircle,
                binding.accountToolbar.navigationIcon,
            ),
        )
        binding.accountToolbar.overflowIcon = LayerDrawable(
            arrayOf(
                backgroundCircle,
                binding.accountToolbar.overflowIcon,
            ),
        )

        binding.accountHeaderInfoContainer.background = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)

        binding.accountAvatarImageView.background = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation).apply {
            fillColor = ColorStateList.valueOf(toolbarColor)
            elevation = appBarElevation
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(resources.getDimension(DR.dimen.account_avatar_background_radius))
                .build()
        }

        // Adjust the appbar views as the user scrolls.
        binding.accountAppBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (verticalOffset == oldOffset) return@addOnOffsetChangedListener

            // Hide the FAB on scrolling down, show again on scroll up.
            actionButton?.let {
                if (hideFab && verticalOffset > oldOffset) it.show()
                if (hideFab && verticalOffset < oldOffset) it.hide()
            }

            oldOffset = verticalOffset

            val pctScroll = (abs(verticalOffset) / titleVisibleHeight.toFloat()).coerceAtMost(1f)

            // Adjust the toolbar title and subtitle, making them more visible relative
            // to how the user scrolls.
            tvTitle?.alpha = pctScroll
            tvSubtitle?.alpha = pctScroll

            // Adjust the avatar image.
            // - Scale proportionate to how far the user scrolls.
            // - Fade proportionate to how far the user scrolls.
            // - Only allow clicks if fully visible.
            binding.accountAvatarImageView.scaleX = 1 - pctScroll
            binding.accountAvatarImageView.scaleY = 1 - pctScroll
            binding.accountAvatarImageView.alpha = 1 - pctScroll
            binding.accountAvatarImageView.isClickable = verticalOffset == 0

            // Fade the account info proportionate to how far the user scrolls.
            binding.accountDisplayNameTextView.alpha = 1 - pctScroll
            binding.accountUsernameTextView.alpha = 1 - pctScroll
            binding.accountLockedImageView.alpha = 1 - pctScroll
            binding.accountFollowsYouChip.alpha = 1 - pctScroll
            binding.accountBadgeContainer.alpha = 1 - pctScroll

            // Determine the toolbar colour; starts transparent, and becomes more
            // visible proportionate to how far the user scrolls.
            binding.accountToolbar.setBackgroundColor(
                argbEvaluator.evaluate(
                    pctScroll,
                    Color.TRANSPARENT,
                    toolbarBackground.resolvedTintColor,
                ) as Int,
            )

            // Only allow swipe-to-refresh if fully visible.
            binding.swipeRefreshLayout.isEnabled = verticalOffset == 0
        }
    }

    /**
     * Subscribe to data loaded at the view model
     */
    private fun subscribeObservables() {
        viewModel.accountData.observe(this) {
            when (it) {
                is Success -> onAccountChanged(it.data)
                is Error -> {
                    Snackbar.make(binding.accountCoordinatorLayout, app.pachli.core.ui.R.string.error_generic, Snackbar.LENGTH_LONG)
                        .setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.refresh() }
                        .show()
                }
                is Loading -> { }
            }
        }
        viewModel.relationshipData.observe(this) {
            val relation = it?.data
            if (relation != null) {
                onRelationshipChanged(relation)
            }

            if (it is Error) {
                Snackbar.make(binding.accountCoordinatorLayout, app.pachli.core.ui.R.string.error_generic, Snackbar.LENGTH_LONG)
                    .setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.refresh() }
                    .show()
            }
        }
        viewModel.noteSaved.observe(this) {
            binding.saveNoteInfo.visible(it, View.INVISIBLE)
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this, true)
    }

    private fun onRefresh() {
        viewModel.refresh()
        adapter.refreshContent()
    }

    /**
     * Setup swipe to refresh layout
     */
    private fun setupRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener { onRefresh() }
        viewModel.isRefreshing.observe(
            this,
        ) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing == true
        }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun onAccountChanged(account: Account?) {
        loadedAccount = account ?: return

        val usernameFormatted = getString(DR.string.post_username_format, account.username)
        binding.accountUsernameTextView.text = usernameFormatted
        binding.accountDisplayNameTextView.text = account.name.emojify(glide, account.emojis, binding.accountDisplayNameTextView, animateEmojis)

        // Long press on username to copy it to clipboard
        for (view in listOf(binding.accountUsernameTextView, binding.accountDisplayNameTextView)) {
            view.setOnLongClickListener {
                loadedAccount?.let { loadedAccount ->
                    val fullUsername = getFullUsername(loadedAccount)
                    clipboard.copyTextTo(fullUsername, R.string.account_username_copied)
                }
                true
            }
        }

        val emojifiedNote = account.note.parseAsMastodonHtml().emojify(glide, account.emojis, binding.accountNoteTextView, animateEmojis)
        setClickableText(binding.accountNoteTextView, emojifiedNote, emptyList(), null, this)

        accountFieldAdapter.fields = account.fields.orEmpty()
        accountFieldAdapter.emojis = account.emojis.orEmpty()
        accountFieldAdapter.notifyDataSetChanged()

        binding.accountLockedImageView.visible(account.locked)

        updateAccountAvatar()
        updateToolbar()
        updateBadges()
        updateMovedAccount()
        updateRemoteAccount()
        updateAccountJoinedDate()
        updateAccountStats()
        invalidateOptionsMenu()

        binding.accountMuteButton.setOnClickListener {
            viewModel.unmuteAccount()
            updateMuteButton()
        }
    }

    private fun updateAccountJoinedDate() {
        loadedAccount?.let { account ->
            try {
                account.createdAt?.let { createdAt ->
                    binding.accountDateJoined.text = resources.getString(
                        R.string.account_date_joined,
                        SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(createdAt),
                    )
                    binding.accountDateJoined.show()
                } ?: binding.accountDateJoined.hide()
            } catch (_: ParseException) {
                binding.accountDateJoined.hide()
            }
        }
    }

    /**
     * Load account's avatar and header image
     */
    private fun updateAccountAvatar() {
        loadedAccount?.let { account ->

            loadAvatar(
                glide,
                account.avatar,
                binding.accountAvatarImageView,
                resources.getDimensionPixelSize(DR.dimen.avatar_radius_94dp),
                animateAvatar,
            )

            glide.asBitmap()
                .load(account.header)
                .centerCrop()
                .into(binding.accountHeaderImageView)

            binding.accountAvatarImageView.setOnClickListener { view ->
                viewImage(view, account.username, account.avatar)
            }
            binding.accountHeaderImageView.setOnClickListener { view ->
                viewImage(view, account.username, account.header)
            }
        }
    }

    private fun viewImage(view: View, owningUsername: String, uri: String) {
        ViewCompat.setTransitionName(view, uri)
        startActivity(
            ViewMediaActivityIntent(view.context, intent.pachliAccountId, owningUsername, uri),
            ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, uri).toBundle(),
        )
    }

    /**
     * Update toolbar views for loaded account
     */
    private fun updateToolbar() {
        loadedAccount?.let { account ->
            supportActionBar?.title = account.name.emojify(glide, account.emojis, binding.accountToolbar, animateEmojis)
            supportActionBar?.subtitle = String.format(getString(DR.string.post_username_format), account.username)
        }
    }

    /**
     * Update moved account info
     */
    private fun updateMovedAccount() {
        loadedAccount?.moved?.let { movedAccount ->

            binding.accountMovedView.show()

            binding.accountMovedView.setOnClickListener {
                onViewAccount(movedAccount.id)
            }

            binding.accountMovedDisplayName.text = movedAccount.name
            binding.accountMovedUsername.text = getString(DR.string.post_username_format, movedAccount.username)

            val avatarRadius = resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)

            loadAvatar(glide, movedAccount.avatar, binding.accountMovedAvatar, avatarRadius, animateAvatar)

            binding.accountMovedText.text = getString(R.string.account_moved_description, movedAccount.name)
        }
    }

    /**
     * Check is account remote and update info if so
     */
    private fun updateRemoteAccount() {
        loadedAccount?.let { account ->
            if (account.isRemote()) {
                binding.accountRemoveView.show()
                binding.accountRemoveView.setOnClickListener {
                    openLink(account.url)
                }
            }
        }
    }

    /**
     * Update account stat info
     */
    private fun updateAccountStats() {
        loadedAccount?.let { account ->
            val numberFormat = NumberFormat.getNumberInstance()
            binding.accountFollowersTextView.text = numberFormat.format(account.followersCount)
            binding.accountFollowingTextView.text = numberFormat.format(account.followingCount)
            binding.accountStatusesTextView.text = numberFormat.format(account.statusesCount)

            binding.accountFloatingActionButton.setOnClickListener { mention() }

            binding.accountFollowButton.setOnClickListener {
                if (viewModel.isSelf) {
                    val intent = EditProfileActivityIntent(this@AccountActivity, intent.pachliAccountId)
                    startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
                    return@setOnClickListener
                }

                if (blocking) {
                    viewModel.changeBlockState()
                    return@setOnClickListener
                }

                when (followState) {
                    FollowState.NOT_FOLLOWING -> {
                        viewModel.changeFollowState()
                    }
                    FollowState.REQUESTED -> {
                        showFollowRequestPendingDialog()
                    }
                    FollowState.FOLLOWING -> {
                        showUnfollowWarningDialog()
                    }
                }
                updateFollowButton()
                updateSubscribeButton()
            }
        }
    }

    private fun onRelationshipChanged(relation: Relationship) {
        followState = when {
            relation.following -> FollowState.FOLLOWING
            relation.requested -> FollowState.REQUESTED
            else -> FollowState.NOT_FOLLOWING
        }
        blocking = relation.blocking
        muting = relation.muting
        blockingDomain = relation.blockingDomain
        showingReblogs = relation.showingReblogs

        // If wellbeing mode is enabled, "follows you" text should not be visible
        val wellbeingEnabled = sharedPreferencesRepository.hideStatsInProfile

        binding.accountFollowsYouChip.visible(relation.followedBy && !wellbeingEnabled)

        // because subscribing is Pleroma extension, enable it __only__ when we have non-null subscribing field
        // it's also now supported in Mastodon 3.3.0rc but called notifying and use different API call
        if (!viewModel.isSelf &&
            followState == FollowState.FOLLOWING &&
            (relation.subscribing != null || relation.notifying != null)
        ) {
            binding.accountSubscribeButton.show()
            binding.accountSubscribeButton.setOnClickListener {
                viewModel.changeSubscribingState()
            }
            if (relation.notifying != null) {
                subscribing = relation.notifying!!
            } else if (relation.subscribing != null) {
                subscribing = relation.subscribing!!
            }
        }

        // remove the listener so it doesn't fire on non-user changes
        binding.accountNoteTextInputLayout.editText?.removeTextChangedListener(noteWatcher)

        binding.accountNoteTextInputLayout.visible(relation.note != null)
        binding.accountNoteTextInputLayout.editText?.setText(relation.note)

        noteWatcher = binding.accountNoteTextInputLayout.editText?.doAfterTextChanged { s ->
            viewModel.noteChanged(s.toString())
        }

        updateButtons()
    }

    private fun updateFollowButton() {
        if (viewModel.isSelf) {
            binding.accountFollowButton.setText(R.string.action_edit_own_profile)
            return
        }
        if (blocking) {
            binding.accountFollowButton.setText(R.string.action_unblock)
            return
        }
        when (followState) {
            FollowState.NOT_FOLLOWING -> {
                binding.accountFollowButton.setText(R.string.action_follow)
            }
            FollowState.REQUESTED -> {
                binding.accountFollowButton.setText(R.string.state_follow_requested)
            }
            FollowState.FOLLOWING -> {
                binding.accountFollowButton.setText(R.string.action_unfollow)
            }
        }
    }

    private fun updateMuteButton() {
        if (muting) {
            binding.accountMuteButton.setIconResource(R.drawable.ic_unmute_24dp)
        } else {
            binding.accountMuteButton.hide()
        }
    }

    private fun updateSubscribeButton() {
        if (followState != FollowState.FOLLOWING) {
            binding.accountSubscribeButton.hide()
        }

        if (subscribing) {
            binding.accountSubscribeButton.setIconResource(R.drawable.ic_notifications_active_24dp)
            binding.accountSubscribeButton.contentDescription = getString(R.string.action_unsubscribe_account)
        } else {
            binding.accountSubscribeButton.setIconResource(R.drawable.ic_notifications_24dp)
            binding.accountSubscribeButton.contentDescription = getString(R.string.action_subscribe_account)
        }
    }

    private fun updateButtons() {
        invalidateOptionsMenu()

        if (loadedAccount?.moved == null) {
            binding.accountFollowButton.show()
            updateFollowButton()
            updateSubscribeButton()

            if (blocking) {
                binding.accountFloatingActionButton.hide()
                binding.accountMuteButton.hide()
            } else {
                binding.accountFloatingActionButton.show()
                binding.accountMuteButton.visible(muting)
                updateMuteButton()
            }
        } else {
            binding.accountFloatingActionButton.hide()
            binding.accountFollowButton.hide()
            binding.accountMuteButton.hide()
            binding.accountSubscribeButton.hide()
        }
    }

    private fun updateBadges() {
        binding.accountBadgeContainer.removeAllViews()

        if (loadedAccount?.bot == true) {
            val badgeView = getBadge(R.drawable.ic_bot_24dp).apply {
                text = getString(DR.string.profile_badge_bot_text)
            }
            binding.accountBadgeContainer.addView(badgeView)
        }

        // Display badges for any roles. Per the API spec this should only include
        // roles with a true `highlighted` property, but the web UI doesn't do that,
        // so follow suit for the moment, https://github.com/mastodon/mastodon/issues/28327
        loadedAccount?.roles?.forEach { role ->
            val badgeView = getBadge(app.pachli.core.ui.R.drawable.profile_role_badge).apply {
                this.role = role.name
                this.domain = viewModel.domain
            }

            binding.accountBadgeContainer.addView(badgeView)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.account_toolbar, menu)

        val openAsItem = menu.findItem(R.id.action_open_as)
        val title = openAsText
        if (title == null) {
            openAsItem.isVisible = false
        } else {
            openAsItem.title = title
        }

        if (!viewModel.isSelf) {
            val block = menu.findItem(R.id.action_block)
            block.title = if (blocking) {
                getString(R.string.action_unblock)
            } else {
                getString(R.string.action_block)
            }

            val mute = menu.findItem(R.id.action_mute)
            mute.title = if (muting) {
                getString(R.string.action_unmute)
            } else {
                getString(R.string.action_mute)
            }

            loadedAccount?.let { loadedAccount ->
                val muteDomain = menu.findItem(R.id.action_mute_domain)
                domain = getDomain(loadedAccount.url)
                when {
                    // If we can't get the domain, there's no way we can mute it anyway...
                    // If the account is from our own domain, muting it is no-op
                    domain.isEmpty() || viewModel.isFromOwnDomain -> {
                        menu.removeItem(R.id.action_mute_domain)
                    }
                    blockingDomain -> {
                        muteDomain.title = getString(R.string.action_unmute_domain, domain)
                    }
                    else -> {
                        muteDomain.title = getString(R.string.action_mute_domain, domain)
                    }
                }
            }

            if (followState == FollowState.FOLLOWING) {
                val showReblogs = menu.findItem(R.id.action_show_reblogs)
                showReblogs.title = if (showingReblogs) {
                    getString(R.string.action_hide_reblogs)
                } else {
                    getString(R.string.action_show_reblogs)
                }
            } else {
                menu.removeItem(R.id.action_show_reblogs)
            }
        } else {
            // It shouldn't be possible to block, mute or report yourself.
            menu.removeItem(R.id.action_block)
            menu.removeItem(R.id.action_mute)
            menu.removeItem(R.id.action_mute_domain)
            menu.removeItem(R.id.action_show_reblogs)
            menu.removeItem(R.id.action_report)
        }

        if (!viewModel.isSelf && followState != FollowState.FOLLOWING) {
            menu.removeItem(R.id.action_add_or_remove_from_list)
        }

        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@AccountActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.collapsingToolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    private fun showFollowRequestPendingDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_message_cancel_follow_request)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnfollowWarningDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_unfollow_warning)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleBlockDomain(instance: String) {
        if (blockingDomain) {
            viewModel.unblockDomain(instance)
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.mute_domain_warning, instance))
                .setPositiveButton(getString(R.string.mute_domain_warning_dialog_ok)) { _, _ -> viewModel.blockDomain(instance) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun toggleBlock() {
        if (viewModel.relationshipData.value?.data?.blocking != true) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.dialog_block_warning, loadedAccount?.username))
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeBlockState() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            viewModel.changeBlockState()
        }
    }

    private fun toggleMute() {
        if (viewModel.relationshipData.value?.data?.muting != true) {
            loadedAccount?.let {
                showMuteAccountDialog(
                    this,
                    it.username,
                ) { notifications, duration ->
                    viewModel.muteAccount(notifications, duration)
                }
            }
        } else {
            viewModel.unmuteAccount()
        }
    }

    private fun mention() {
        loadedAccount?.let {
            val options = if (viewModel.isSelf) {
                ComposeOptions(kind = ComposeOptions.ComposeKind.NEW)
            } else {
                ComposeOptions(
                    mentionedUsernames = setOf(it.username),
                    kind = ComposeOptions.ComposeKind.NEW,
                )
            }
            val intent = ComposeActivityIntent(this, intent.pachliAccountId, options)
            startActivity(intent)
        }
    }

    override fun onViewTag(tag: String) {
        val intent = TimelineActivityIntent.hashtag(this, intent.pachliAccountId, tag)
        startActivityWithDefaultTransition(intent)
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivityIntent(this, intent.pachliAccountId, id)
        startActivityWithDefaultTransition(intent)
    }

    override fun onViewUrl(url: String) {
        viewUrl(intent.pachliAccountId, url)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        super.onMenuItemSelected(item)
        when (item.itemId) {
            R.id.action_open_in_web -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    openLink(loadedAccount.url)
                }
                return true
            }
            R.id.action_open_as -> {
                loadedAccount?.let { loadedAccount ->
                    lifecycleScope.launch {
                        chooseAccount(item.title, false)?.let { account ->
                            openAsAccount(loadedAccount.url, account)
                        }
                    }
                }
            }
            R.id.action_share_account_link -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    val url = loadedAccount.url
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, url)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_account_link_to)))
                }
                return true
            }
            R.id.action_share_account_username -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    val fullUsername = getFullUsername(loadedAccount)
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, fullUsername)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_account_username_to)))
                }
                return true
            }
            R.id.action_block -> {
                toggleBlock()
                return true
            }
            R.id.action_mute -> {
                toggleMute()
                return true
            }
            R.id.action_add_or_remove_from_list -> {
                ListsForAccountFragment.newInstance(intent.pachliAccountId, viewModel.accountId).show(supportFragmentManager, null)
                return true
            }
            R.id.action_mute_domain -> {
                toggleBlockDomain(domain)
                return true
            }
            R.id.action_show_reblogs -> {
                viewModel.changeShowReblogsState()
                return true
            }
            R.id.action_refresh_account -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                return true
            }
            R.id.action_report -> {
                loadedAccount?.let { loadedAccount ->
                    startActivity(ReportActivityIntent(this, intent.pachliAccountId, viewModel.accountId, loadedAccount.username))
                }
                return true
            }
        }
        return false
    }

    private fun getFullUsername(account: Account): String {
        return if (account.isRemote()) {
            "@" + account.username
        } else {
            val localUsername = account.localUsername
            // Note: !! here will crash if this pane is ever shown to a logged-out user. With AccountActivity this is believed to be impossible.
            val domain = accountManager.activeAccount!!.domain
            "@$localUsername@$domain"
        }
    }

    private fun getBadge(@DrawableRes icon: Int): RoleChip {
        return RoleChip(this).apply {
            setChipIconResource(icon)
            setBackgroundResource(R.drawable.profile_badge_background)
            chipStrokeColor = ColorStateList.valueOf(MaterialColors.getColor(this, android.R.attr.textColorTertiary))
            chipBackgroundColor = ColorStateList.valueOf(0)
        }
    }

    companion object {
        private val argbEvaluator = ArgbEvaluator()
    }
}
