/*
 * Copyright 2020 Tusky Contributors
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

package app.pachli

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.SHOW_AS_ACTION_NEVER
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewGroupCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.MarginPageTransformer
import app.pachli.appstore.CacheUpdater
import app.pachli.components.notifications.createNotificationChannelsForAccount
import app.pachli.components.notifications.domain.AndroidNotificationsAreEnabledUseCase
import app.pachli.components.notifications.domain.EnableAllNotificationsUseCase
import app.pachli.core.activity.PostLookupFallbackBehavior
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.ListsRepository.Companion.compareByListTitle
import app.pachli.core.data.repository.PachliAccount
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.EmbeddedFontFamily
import app.pachli.core.designsystem.R as DR
import app.pachli.core.eventhub.EventHub
import app.pachli.core.model.Announcement
import app.pachli.core.model.MastodonList
import app.pachli.core.model.Notification
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AboutActivityIntent
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AnnouncementsActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.DraftsActivityIntent
import app.pachli.core.navigation.EditProfileActivityIntent
import app.pachli.core.navigation.FollowedTagsActivityIntent
import app.pachli.core.navigation.IntentRouterActivityIntent
import app.pachli.core.navigation.ListsActivityIntent
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.navigation.MainActivityIntent.Payload
import app.pachli.core.navigation.ManageAccountsActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent.PreferenceScreen
import app.pachli.core.navigation.ScheduledStatusActivityIntent
import app.pachli.core.navigation.SearchActivityIntent
import app.pachli.core.navigation.SuggestionsActivityIntent
import app.pachli.core.navigation.TabPreferenceActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.TrendingActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.preferences.MainNavigationPosition
import app.pachli.core.preferences.TabAlignment
import app.pachli.core.preferences.TabContents
import app.pachli.core.ui.AlignableTabLayoutAlignment
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.extensions.await
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.ActivityMainBinding
import app.pachli.db.DraftsAlert
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.pager.MainPagerAdapter
import app.pachli.updatecheck.UpdateCheck
import app.pachli.usecase.DeveloperToolsUseCase
import app.pachli.util.UpdateShortCutsUseCase
import app.pachli.util.getDimension
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.FixedSizeDrawable
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.materialdrawer.holder.BadgeStyle
import com.mikepenz.materialdrawer.holder.ColorHolder
import com.mikepenz.materialdrawer.holder.ImageHolder
import com.mikepenz.materialdrawer.holder.StringHolder
import com.mikepenz.materialdrawer.iconics.iconicsIcon
import com.mikepenz.materialdrawer.model.AbstractDrawerItem
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.SectionDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import com.mikepenz.materialdrawer.model.interfaces.Typefaceable
import com.mikepenz.materialdrawer.model.interfaces.descriptionRes
import com.mikepenz.materialdrawer.model.interfaces.descriptionText
import com.mikepenz.materialdrawer.model.interfaces.iconRes
import com.mikepenz.materialdrawer.model.interfaces.iconUrl
import com.mikepenz.materialdrawer.model.interfaces.nameRes
import com.mikepenz.materialdrawer.model.interfaces.nameText
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import com.mikepenz.materialdrawer.util.addItemAtPosition
import com.mikepenz.materialdrawer.util.addItems
import com.mikepenz.materialdrawer.util.addItemsAtPosition
import com.mikepenz.materialdrawer.util.getPosition
import com.mikepenz.materialdrawer.util.removeItemByPosition
import com.mikepenz.materialdrawer.util.removeItems
import com.mikepenz.materialdrawer.util.updateBadge
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.helpers.EMOJI_PREFERENCE
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ViewUrlActivity(), ActionButtonActivity, MenuProvider {
    @Inject
    @ApplicationScope
    lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var cacheUpdater: CacheUpdater

    @Inject
    lateinit var draftsAlert: DraftsAlert

    @Inject
    lateinit var updateCheck: UpdateCheck

    @Inject
    lateinit var developerToolsUseCase: DeveloperToolsUseCase

    @Inject
    lateinit var enableAllNotifications: EnableAllNotificationsUseCase

    @Inject
    lateinit var androidNotificationsAreEnabled: AndroidNotificationsAreEnabledUseCase

    @Inject
    lateinit var updateShortCuts: UpdateShortCutsUseCase

    private val viewModel: MainViewModel by viewModels()

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override val actionButton by unsafeLazy { binding.composeButton }

    private lateinit var header: AccountHeaderView

    private var onTabSelectedListener: OnTabSelectedListener? = null

    // We need to know if the emoji pack has been changed
    private var selectedEmojiPack: String? = null

    /** Mediate between binding.viewPager and the chosen tab layout */
    private var tabLayoutMediator: TabLayoutMediator? = null

    /** Adapter for the different timeline tabs */
    private lateinit var tabAdapter: MainPagerAdapter

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                binding.mainDrawerLayout.isOpen -> binding.mainDrawerLayout.close()
                binding.viewPager.currentItem != 0 -> binding.viewPager.currentItem = 0
            }
        }
    }

    /**
     * Drawer items corresponding to the account's Mastodon lists. May be empty if
     * the account has no lists.
     */
    private val listDrawerItems = mutableListOf<PrimaryDrawerItem>()

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

    /** Mutex to protect modifications to the drawer's items. */
    private val drawerMutex = Mutex()

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.mainDrawer.applyWindowInsets(
            left = InsetType.PADDING,
            top = InsetType.PADDING,
            right = InsetType.PADDING,
            bottom = InsetType.PADDING,
        )

        // If the navigation bar is at the bottom then the pager needs additional
        // padding to clear it. Do this before applying edge to edge insets, so
        // the new padding is added to the insets.
        if (sharedPreferencesRepository.mainNavigationPosition == MainNavigationPosition.BOTTOM) {
            with(binding.viewPager) {
                val actionBarSize = getDimension(this@MainActivity, androidx.appcompat.R.attr.actionBarSize)
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + actionBarSize)
            }
        }

        binding.appBar.applyDefaultWindowInsets()
        binding.viewPager.applyDefaultWindowInsets()
        actionButton.applyDefaultWindowInsets()
        binding.mainToolbar.addScrollEffect(FadeChildScrollEffect)
        binding.topNav.addScrollEffect(FadeChildScrollEffect)
        binding.bottomNav.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
            bottom = InsetType.PADDING,
        )

        setContentView(binding.root)

        viewModel.accept(InfallibleUiAction.LoadPachliAccount(pachliAccountId))

        var showNotificationTab = MainActivityIntent.getOpenNotificationTab(intent)

        // check for savedInstanceState in order to not handle intent events more than once
        if (intent != null && savedInstanceState == null) {
            val payload = MainActivityIntent.payload(intent)
            when (payload) {
                is Payload.Notification -> {
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(payload.notificationTag, payload.notificationId)
                    when (payload.notificationType) {
                        Notification.Type.FOLLOW_REQUEST -> {
                            val intent = AccountListActivityIntent(this, intent.pachliAccountId, AccountListActivityIntent.Kind.FOLLOW_REQUESTS)
                            startActivityWithDefaultTransition(intent)
                        }

                        else -> showNotificationTab = true
                    }
                }

                is Payload.OpenAs -> viewUrl(intent.pachliAccountId, payload.url, PostLookupFallbackBehavior.DISPLAY_ERROR)

                Payload.OpenDrafts -> startActivity(DraftsActivityIntent(this, intent.pachliAccountId))

                Payload.Start -> {
                    /* Fall through, normal startup. */
                }
            }
        }

        window.statusBarColor = Color.TRANSPARENT // don't draw a status bar, the DrawerLayout and the MaterialDrawerLayout have their own

        // Determine which of the three toolbars should be the supportActionBar (which hosts
        // the options menu).
        val hideTopToolbar = viewModel.uiState.value.hideTopToolbar
        if (hideTopToolbar) {
            when (viewModel.uiState.value.mainNavigationPosition) {
                MainNavigationPosition.TOP -> setSupportActionBar(binding.topNav)
                MainNavigationPosition.BOTTOM -> setSupportActionBar(binding.bottomNav)
            }
            binding.mainToolbar.hide()
            // There's not enough space in the top/bottom bars to show the title as well.
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } else {
            setSupportActionBar(binding.mainToolbar)
            binding.mainToolbar.show()
        }

        addMenuProvider(this)

        binding.viewPager.reduceSwipeSensitivity()

        // Initialise the tab adapter and set to viewpager. Fragments appear to be leaked if the
        // adapter changes over the life of the viewPager (the adapter, not its contents), so set
        // the initial list of tabs to empty, and set the full list later in setupTabs(). See
        // https://github.com/tuskyapp/Tusky/issues/3251 for details.
        tabAdapter = MainPagerAdapter(emptyList(), this)
        binding.viewPager.adapter = tabAdapter
        // Work around https://issuetracker.google.com/issues/432664597, where the recyclerview
        // might appear to be empty.
        binding.viewPager.offscreenPageLimit = 2

        // Process different parts of the account flow depending on what's changed
        val account = viewModel.pachliAccountFlow.filterNotNull()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                // TODO: Continue to call this, as it sets properties in NotificationConfig
                androidNotificationsAreEnabled(this@MainActivity)
                enableAllNotifications()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // One-off setup independent of the UI state.
                val initialAccount = account.first()
                createNotificationChannelsForAccount(initialAccount.entity, this@MainActivity)

                bindMainDrawer(initialAccount)
                bindMainDrawerItems(initialAccount, savedInstanceState)

                // Process the UI state. This has to happen *after* the main drawer has
                // been configured.
                launch {
                    viewModel.uiState.collect { uiState ->
                        bindMainDrawerSearch(this@MainActivity, initialAccount.id, uiState.hideTopToolbar)
                        bindMainDrawerProfileHeader(uiState)
                        bindMainDrawerScheduledPosts(this@MainActivity, initialAccount.id, uiState.canSchedulePost)
                    }
                }

                launch {
                    viewModel.uiState.distinctUntilChangedBy { it.accounts }.collect {
                        updateShortCuts(it.accounts)
                    }
                }

                // Process changes to the account's header picture.
                launch {
                    account.distinctUntilChangedBy { it.entity.profileHeaderPictureUrl }.collectLatest {
                        header.headerBackground = ImageHolder(it.entity.profileHeaderPictureUrl)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Process changes to the account's lists.
                launch {
                    account.distinctUntilChangedBy { it.lists }.collectLatest { account ->
                        bindMainDrawerLists(account.id, account.lists)
                    }
                }

                // Process changes to the account's profile picture.
                launch {
                    account.distinctUntilChangedBy { it.entity.profilePictureUrl }.collectLatest {
                        bindDrawerAvatar(it.entity.profilePictureUrl, false)
                    }
                }

                // Process changes to the account's tab preferences.
                launch {
                    account.distinctUntilChangedBy { it.entity.tabPreferences }.collectLatest {
                        bindTabs(it.entity, showNotificationTab)
                        showNotificationTab = false
                    }
                }

                // Process changes to account's announcements.
                launch {
                    account.distinctUntilChangedBy { it.announcements }.collectLatest {
                        bindMainDrawerAnnouncements(it.announcements)
                    }
                }
            }
        }

        selectedEmojiPack = sharedPreferencesRepository.getString(EMOJI_PREFERENCE, "")

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (Build.VERSION.SDK_INT >= TIRAMISU && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(POST_NOTIFICATIONS), 1)
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this, true)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)

        menuInflater.inflate(R.menu.activity_main, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = makeIcon(this@MainActivity, GoogleMaterial.Icon.gmd_search, IconicsSize.dp(20))
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super<ViewUrlActivity>.onPrepareMenu(menu)

        menu.findItem(R.id.action_remove_tab).isVisible = tabAdapter.tabs.getOrNull(binding.viewPager.currentItem)?.let {
            it.timeline != Timeline.Home
        } == true

        // If the main toolbar is hidden then there's no space in the top/bottomNav to show
        // the menu items as icons, so forceably disable them
        if (!binding.mainToolbar.isVisible) menu.forEach { it.setShowAsAction(SHOW_AS_ACTION_NEVER) }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return when (menuItem.itemId) {
            R.id.action_search -> {
                startActivity(SearchActivityIntent(this@MainActivity, pachliAccountId))
                true
            }
            R.id.action_remove_tab -> {
                val timeline = tabAdapter.tabs[binding.viewPager.currentItem].timeline
                viewModel.accept(InfallibleUiAction.TabRemoveTimeline(pachliAccountId, timeline))
                true
            }
            R.id.action_tab_preferences -> {
                startActivity(TabPreferenceActivityIntent(this, pachliAccountId))
                true
            }
            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    override fun onResume() {
        super.onResume()
        val currentEmojiPack = sharedPreferencesRepository.getString(EMOJI_PREFERENCE, "")
        if (currentEmojiPack != selectedEmojiPack) {
            Timber.d(
                "onResume: EmojiPack has been changed from %s to %s",
                selectedEmojiPack,
                currentEmojiPack,
            )
            selectedEmojiPack = currentEmojiPack
            recreate()
        }

        lifecycleScope.launch { updateCheck.checkForUpdate(this@MainActivity) }
    }

    override fun onStart() {
        super.onStart()
        // For some reason the navigation drawer is opened when the activity is recreated
        if (binding.mainDrawerLayout.isOpen) {
            binding.mainDrawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (binding.mainDrawerLayout.isOpen) {
                    binding.mainDrawerLayout.close()
                } else {
                    binding.mainDrawerLayout.open()
                }
                return true
            }
            KeyEvent.KEYCODE_SEARCH -> {
                startActivityWithDefaultTransition(
                    SearchActivityIntent(this, pachliAccountId),
                )
                return true
            }
        }
        if (event.isCtrlPressed || event.isShiftPressed) {
            // FIXME: blackberry keyONE raises SHIFT key event even CTRL IS PRESSED
            when (keyCode) {
                KeyEvent.KEYCODE_N -> {
                    // open compose activity by pressing SHIFT + N (or CTRL + N)
                    val composeIntent = ComposeActivityIntent(applicationContext, pachliAccountId)
                    startActivity(composeIntent)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Initialises the main drawer and header properties.
     *
     * See [bindMainDrawerProfileHeader] for setting the header contents.
     */
    private fun bindMainDrawer(pachliAccount: PachliAccount) {
        // Clicking on navigation elements opens the drawer.
        val drawerOpenClickListener = View.OnClickListener { binding.mainDrawerLayout.open() }
        binding.mainToolbar.setNavigationOnClickListener(drawerOpenClickListener)
        binding.topNav.setNavigationOnClickListener(drawerOpenClickListener)
        binding.bottomNav.setNavigationOnClickListener(drawerOpenClickListener)

        // Header should allow user to add new accounts.
        header = AccountHeaderView(this).apply {
            headerBackgroundScaleType = ImageView.ScaleType.CENTER_CROP
            currentHiddenInList = true
            onAccountHeaderListener = { _: View?, profile: IProfile, current: Boolean ->
                onAccountHeaderClick(pachliAccount, profile, current)
                false
            }
            addProfile(
                ProfileSettingDrawerItem().apply {
                    identifier = DRAWER_ITEM_ADD_ACCOUNT
                    nameRes = app.pachli.core.ui.R.string.add_account_name
                    descriptionRes = R.string.add_account_description
                    iconicsIcon = GoogleMaterial.Icon.gmd_add
                },
                0,
            )
            addProfile(
                ProfileSettingDrawerItem().apply {
                    identifier = DRAWER_ITEM_MANAGE_ACCOUNTS
                    nameRes = R.string.manage_accounts_name
                    iconRes = R.drawable.ic_manage_accounts
                },
                1,
            )
            attachToSliderView(binding.mainDrawer)
            dividerBelowHeader = false
            closeDrawerOnProfileListClick = true
        }

        header.currentProfileName.maxLines = 1
        header.currentProfileName.ellipsize = TextUtils.TruncateAt.END

        // Account header background and text colours are not styleable, so set them here
        header.accountHeaderBackground.setBackgroundColor(
            MaterialColors.getColor(header, com.google.android.material.R.attr.colorSecondaryContainer),
        )
        val headerTextColor = MaterialColors.getColor(header, com.google.android.material.R.attr.colorOnSecondaryContainer)
        header.currentProfileName.setTextColor(headerTextColor)
        header.currentProfileEmail.setTextColor(headerTextColor)

        DrawerImageLoader.init(MainDrawerImageLoader(viewModel.uiState.value.animateAvatars))

        binding.mainDrawerLayout.addDrawerListener(
            object : DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

                override fun onDrawerOpened(drawerView: View) {
                    onBackPressedCallback.isEnabled = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    onBackPressedCallback.isEnabled = binding.tabLayout.selectedTabPosition > 0
                }

                override fun onDrawerStateChanged(newState: Int) {}
            },
        )
    }

    /**
     * Binds the "search" menu item in the main drawer.
     *
     * @param context
     * @param pachliAccountId
     * @param showSearchItem True if a "Search" menu item should be added to the list
     * (because the top toolbar is hidden), false if any existing search item should be
     * removed.
     */
    private suspend fun bindMainDrawerSearch(context: Context, pachliAccountId: Long, showSearchItem: Boolean) = drawerMutex.withLock {
        val searchItemPosition = binding.mainDrawer.getPosition(DRAWER_ITEM_SEARCH)
        val showing = searchItemPosition != -1

        // If it's showing state and desired showing state are the same there's nothing
        // to do.
        if (showing == showSearchItem) return

        // Showing and not wanted, remove it.
        if (!showSearchItem) {
            binding.mainDrawer.removeItemByPosition(searchItemPosition)
            return
        }

        // Add a "Search" menu item.
        binding.mainDrawer.addItemAtPosition(
            4,
            primaryDrawerItem {
                identifier = DRAWER_ITEM_SEARCH
                nameRes = R.string.action_search
                iconicsIcon = GoogleMaterial.Icon.gmd_search
                onClick = {
                    startActivityWithDefaultTransition(
                        SearchActivityIntent(context, pachliAccountId),
                    )
                }
            },
        )
        updateMainDrawerTypeface(sharedPreferencesRepository.fontFamily)
    }

    /**
     * Binds the "Scheduled posts" menu item in the main drawer.
     *
     * @param context
     * @param pachliAccountId
     * @param showSchedulePosts True if a "Scheduled posts" menu item should be added
     * to the list, false if any existing item should be removed.
     */
    private suspend fun bindMainDrawerScheduledPosts(context: Context, pachliAccountId: Long, showSchedulePosts: Boolean) = drawerMutex.withLock {
        // Can't use binding.mainDrawer.getPosition() here, as that returns -1 if the
        // account list is open (https://github.com/mikepenz/MaterialDrawer/issues/2826).
        // Instead, pull the position from the adapter for primary items. This is offset
        // by 1 in the adapter that wraps all the items.
        val existingPosition = binding.mainDrawer.itemAdapter.getAdapterPosition(DRAWER_ITEM_SCHEDULED_POSTS)

        val showing = existingPosition != -1
        if (showing == showSchedulePosts) return

        if (!showSchedulePosts) {
            binding.mainDrawer.itemAdapter.removeByIdentifier(DRAWER_ITEM_SCHEDULED_POSTS)
            return
        }

        // Add the "Scheduled posts" item immediately after "Drafts"
        val relativeDraftsPosition = binding.mainDrawer.itemAdapter.getAdapterPosition(DRAWER_ITEM_DRAFTS)
        val globalDraftsPosition = binding.mainDrawer.itemAdapter.getGlobalPosition(relativeDraftsPosition)
        binding.mainDrawer.itemAdapter.add(
            globalDraftsPosition + 1,
            primaryDrawerItem {
                identifier = DRAWER_ITEM_SCHEDULED_POSTS
                nameRes = R.string.action_access_scheduled_posts
                iconRes = R.drawable.ic_access_time
                onClick = {
                    startActivityWithDefaultTransition(
                        ScheduledStatusActivityIntent(context, pachliAccountId),
                    )
                }
            },
        )

        updateMainDrawerTypeface(sharedPreferencesRepository.fontFamily)
    }

    /** Binds [lists] to the "Lists" section in the main drawer. */
    private suspend fun bindMainDrawerLists(pachliAccountId: Long, lists: List<MastodonList>) = drawerMutex.withLock {
        // Can't use binding.mainDrawer.getPosition() here, as that returns -1 if the
        // account list is open (https://github.com/mikepenz/MaterialDrawer/issues/2826).
        // Instead, pull the position from the adapter for primary items. This is offset
        // by 1 in the adapter that wraps all the items.
        val headerPosition = binding.mainDrawer.itemAdapter.getAdapterPosition(DRAWER_ITEM_LISTS) + 1
        if (headerPosition == 0) return@withLock

        binding.mainDrawer.removeItems(*listDrawerItems.toTypedArray())

        listDrawerItems.clear()
        lists.sortedWith(compareByListTitle).forEach { list ->
            listDrawerItems.add(
                primaryDrawerItem {
                    nameText = list.title
                    iconicsIcon = GoogleMaterial.Icon.gmd_list
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.list(
                                this@MainActivity,
                                pachliAccountId,
                                list.listId,
                                list.title,
                            ),
                        )
                    }
                },
            )
        }

        // Insert items after the "Lists" header. Again, use the itemAdapter directly
        // instead of addItemsAtPosition.
        binding.mainDrawer.addItemsAtPosition(headerPosition + 1, *listDrawerItems.toTypedArray())
        updateMainDrawerTypeface(sharedPreferencesRepository.fontFamily)
    }

    /**
     * Binds the normal drawer items.
     *
     * See [bindMainDrawerLists] and [bindMainDrawerSearch].
     */
    private suspend fun bindMainDrawerItems(pachliAccount: PachliAccount, savedInstanceState: Bundle?) = drawerMutex.withLock {
        val pachliAccountId = pachliAccount.id

        binding.mainDrawer.apply {
            itemAdapter.clear()
            tintStatusBar = true
            addItems(
                primaryDrawerItem {
                    nameRes = R.string.title_notifications
                    iconicsIcon = GoogleMaterial.Icon.gmd_notifications
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.notifications(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_local
                    iconRes = R.drawable.ic_local_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.publicLocal(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_federated
                    iconRes = R.drawable.ic_public_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.publicFederated(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_direct_messages
                    iconRes = R.drawable.ic_reblog_direct_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.conversations(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_bookmarks
                    iconicsIcon = GoogleMaterial.Icon.gmd_bookmark
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.bookmarks(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_favourites
                    isSelectable = false
                    iconicsIcon = GoogleMaterial.Icon.gmd_star
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.favourites(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_trending
                    iconicsIcon = GoogleMaterial.Icon.gmd_trending_up
                    onClick = {
                        startActivityWithDefaultTransition(
                            TrendingActivityIntent(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_followed_hashtags
                    iconRes = R.drawable.ic_hashtag
                    onClick = {
                        startActivityWithDefaultTransition(
                            FollowedTagsActivityIntent(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_follow_requests
                    iconicsIcon = GoogleMaterial.Icon.gmd_person_add
                    onClick = {
                        startActivityWithDefaultTransition(
                            AccountListActivityIntent(context, pachliAccountId, AccountListActivityIntent.Kind.FOLLOW_REQUESTS),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_suggestions
                    iconicsIcon = GoogleMaterial.Icon.gmd_explore
                    onClick = {
                        startActivityWithDefaultTransition(SuggestionsActivityIntent(context, pachliAccountId))
                    }
                },
                SectionDrawerItem().apply {
                    identifier = DRAWER_ITEM_LISTS
                    nameRes = app.pachli.feature.lists.R.string.title_lists
                },
                primaryDrawerItem {
                    nameRes = R.string.manage_lists
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        startActivityWithDefaultTransition(
                            ListsActivityIntent(context, pachliAccountId),
                        )
                    }
                },
                DividerDrawerItem(),
                primaryDrawerItem {
                    identifier = DRAWER_ITEM_DRAFTS
                    nameRes = R.string.action_access_drafts
                    iconRes = R.drawable.ic_notebook
                    onClick = {
                        startActivityWithDefaultTransition(
                            DraftsActivityIntent(context, pachliAccountId),
                        )
                    }
                },
                primaryDrawerItem {
                    identifier = DRAWER_ITEM_ANNOUNCEMENTS
                    nameRes = R.string.title_announcements
                    iconRes = R.drawable.ic_bullhorn_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            AnnouncementsActivityIntent(context, pachliAccountId),
                        )
                    }
                    badgeStyle = BadgeStyle().apply {
                        textColor = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, com.google.android.material.R.attr.colorOnPrimary))
                        color = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, com.google.android.material.R.attr.colorPrimary))
                    }
                },
                DividerDrawerItem(),
                secondaryDrawerItem {
                    nameRes = R.string.action_view_account_preferences
                    iconRes = R.drawable.ic_account_settings
                    onClick = {
                        startActivityWithDefaultTransition(
                            PreferencesActivityIntent(context, pachliAccountId, PreferenceScreen.ACCOUNT),
                        )
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_view_preferences
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        startActivityWithDefaultTransition(
                            PreferencesActivityIntent(context, pachliAccountId, PreferenceScreen.GENERAL),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_edit_profile
                    iconicsIcon = GoogleMaterial.Icon.gmd_person
                    onClick = {
                        startActivityWithDefaultTransition(
                            EditProfileActivityIntent(context, pachliAccountId),
                        )
                    }
                },
                secondaryDrawerItem {
                    nameRes = app.pachli.feature.about.R.string.about_title_activity
                    iconicsIcon = GoogleMaterial.Icon.gmd_info
                    onClick = {
                        startActivityWithDefaultTransition(
                            AboutActivityIntent(context),
                        )
                    }
                },
                secondaryDrawerItem {
                    nameRes = app.pachli.core.ui.R.string.action_logout
                    iconRes = R.drawable.ic_logout
                    onClick = { logout(pachliAccount) }
                },
            )

            setSavedInstance(savedInstanceState)
        }

        if (BuildConfig.DEBUG) {
            // Add a "Developer tools" entry. Code that makes it easier to
            // set the app state at runtime belongs here, it will never
            // be exposed to users.
            binding.mainDrawer.addItems(
                DividerDrawerItem(),
                secondaryDrawerItem {
                    nameText = "Developer tools"
                    isEnabled = true
                    iconicsIcon = GoogleMaterial.Icon.gmd_developer_mode
                    onClick = {
                        buildDeveloperToolsDialog().show()
                    }
                },
            )
        }
        updateMainDrawerTypeface(sharedPreferencesRepository.fontFamily)
    }

    private fun buildDeveloperToolsDialog(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("Developer Tools")
            .setItems(
                arrayOf(
                    "Clear home timeline cache",
                    "Remove first 40 statuses",
                ),
            ) { _, which ->
                Timber.d("Developer tools: %d", which)
                when (which) {
                    0 -> {
                        Timber.d("Clearing home timeline cache")
                        lifecycleScope.launch {
                            developerToolsUseCase.clearHomeTimelineCache(pachliAccountId)
                        }
                    }
                    1 -> {
                        Timber.d("Removing most recent 40 statuses")
                        lifecycleScope.launch {
                            developerToolsUseCase.deleteFirstKStatuses(pachliAccountId, 40)
                        }
                    }
                }
            }
            .create()
    }

    /**
     * Sets the correct typeface for everything in the drawer.
     *
     * The drawer library forces the `android:fontFamily` attribute, overriding the value in the
     * theme. Force-ably set the typeface for everything in the drawer if using a non-default font.
     */
    private fun updateMainDrawerTypeface(fontFamily: EmbeddedFontFamily) {
        if (fontFamily == EmbeddedFontFamily.DEFAULT) return

        val typeface = ResourcesCompat.getFont(this, fontFamily.font) ?: return
        for (i in 0..binding.mainDrawer.adapter.itemCount) {
            val item = binding.mainDrawer.adapter.getItem(i)
            if (item !is Typefaceable) continue
            item.typeface = typeface
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(binding.mainDrawer.saveInstanceState(outState))
    }

    /**
     * Binds the [account]'s tab preferences to the UI.
     *
     * Chooses the active tab based on the previously active tab and [selectNotificationTab].
     *
     * @param account
     * @param selectNotificationTab True if the "Notification" tab should be made active.
     */
    private fun bindTabs(account: AccountEntity, selectNotificationTab: Boolean) {
        val activeTabLayout = when (sharedPreferencesRepository.mainNavigationPosition) {
            MainNavigationPosition.TOP -> {
                binding.bottomNav.hide()
                (binding.viewPager.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = 0
                (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).anchorId =
                    R.id.viewPager
                binding.tabLayout
            }

            MainNavigationPosition.BOTTOM -> {
                val actionBarSize = getDimension(this, androidx.appcompat.R.attr.actionBarSize)
                val fabMargin = resources.getDimensionPixelSize(DR.dimen.fabMargin)
                (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = actionBarSize + fabMargin
                binding.topNav.hide()
                binding.bottomTabLayout
            }
        }

        activeTabLayout.alignment = when (viewModel.uiState.value.tabAlignment) {
            TabAlignment.START -> AlignableTabLayoutAlignment.START
            TabAlignment.JUSTIFY_IF_POSSIBLE -> AlignableTabLayoutAlignment.JUSTIFY_IF_POSSIBLE
            TabAlignment.END -> AlignableTabLayoutAlignment.END
        }
        val tabContents = viewModel.uiState.value.tabContents
        activeTabLayout.isInlineLabel = tabContents == TabContents.ICON_TEXT_INLINE

        // Save the previous tab so it can be restored later
        val previousTabIndex = binding.viewPager.currentItem
        val previousTab = tabAdapter.tabs.getOrNull(previousTabIndex)

        val tabs = account.tabPreferences.map { TabViewData.from(account.id, it) }

        // Detach any existing mediator before changing tab contents and attaching a new mediator
        tabLayoutMediator?.detach()

        tabAdapter.tabs = tabs
        tabAdapter.notifyItemRangeChanged(0, tabs.size)

        tabLayoutMediator = TabLayoutMediator(
            activeTabLayout,
            binding.viewPager,
            true,
        ) { tab: TabLayout.Tab, position: Int ->
            if (tabContents != TabContents.TEXT_ONLY) {
                tab.icon = AppCompatResources.getDrawable(this@MainActivity, tabs[position].icon)
            }
            if (tabContents != TabContents.ICON_ONLY) {
                tab.text = tabs[position].title(this@MainActivity)
            }
            tab.contentDescription = tabs[position].title(this@MainActivity)
        }.also { it.attach() }

        // Selected tab is either
        // - Notification tab (if appropriate)
        // - The previously selected tab (if it hasn't been removed)
        //   - Tabs containing lists are compared by list ID, in case the list was renamed
        // - The tab to the left of the previous selected tab (if the previously selected tab
        //   was removed)
        // - Left-most tab
        val position = if (selectNotificationTab) {
            tabs.indexOfFirst { it.timeline is Timeline.Notifications }
        } else {
            previousTab?.let {
                tabs.indexOfFirst {
                    if (it.timeline is Timeline.UserList && previousTab.timeline is Timeline.UserList) {
                        it.timeline.listId == previousTab.timeline.listId
                    } else {
                        it == previousTab
                    }
                }
            }
        }.takeIf { it != -1 } ?: max(previousTabIndex - 1, 0)
        binding.viewPager.setCurrentItem(position, false)

        val pageMargin = resources.getDimensionPixelSize(DR.dimen.tab_page_margin)
        binding.viewPager.setPageTransformer(MarginPageTransformer(pageMargin))

        binding.viewPager.isUserInputEnabled = viewModel.uiState.value.enableTabSwipe

        onTabSelectedListener?.let {
            activeTabLayout.removeOnTabSelectedListener(it)
        }

        onTabSelectedListener = object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onBackPressedCallback.isEnabled = tab.position > 0 || binding.mainDrawerLayout.isOpen

                supportActionBar?.title = tabs[tab.position].title(this@MainActivity)

                refreshComposeButtonState(tabs[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                val fragment = tabAdapter.getFragment(tab.position)
                (fragment as? ReselectableFragment)?.onReselect()

                refreshComposeButtonState(tabs[tab.position])
            }
        }.also {
            activeTabLayout.addOnTabSelectedListener(it)
        }

        supportActionBar?.title = tabs[position].title(this@MainActivity)
        binding.mainToolbar.setOnClickListener {
            (tabAdapter.getFragment(activeTabLayout.selectedTabPosition) as? ReselectableFragment)?.onReselect()
        }

        refreshComposeButtonState(tabs[position])
    }

    private fun refreshComposeButtonState(tabViewData: TabViewData) {
        tabViewData.composeIntent?.let { intent ->
            binding.composeButton.setOnClickListener {
                startActivity(intent(applicationContext, pachliAccountId))
            }
            binding.composeButton.show()
        } ?: binding.composeButton.hide()
    }

    /**
     * Handles clicks on profile avatars in the main drawer header.
     *
     * Either:
     * - Opens the user's account if they clicked on their profile.
     * - Starts LoginActivity to add a new account.
     * - Switch account.
     *
     * @param pachliAccount
     * @param profile
     * @param current True if the clicked avatar is the currently logged in account
     */
    private fun onAccountHeaderClick(pachliAccount: PachliAccount, profile: IProfile, current: Boolean) {
        when {
            current -> startActivityWithDefaultTransition(
                AccountActivityIntent(this, pachliAccount.id, pachliAccount.entity.accountId),
            )

            profile.identifier == DRAWER_ITEM_ADD_ACCOUNT -> startActivityWithDefaultTransition(
                LoginActivityIntent(this, LoginMode.AdditionalLogin),
            )

            profile.identifier == DRAWER_ITEM_MANAGE_ACCOUNTS -> startActivityWithDefaultTransition(
                ManageAccountsActivityIntent(this),
            )

            else -> changeAccountAndRestart(profile.identifier)
        }
    }

    /**
     * Relaunches MainActivity, switched to the account identified by [accountId].
     */
    private fun changeAccountAndRestart(accountId: Long) {
        cacheUpdater.stop()
        val intent = IntentRouterActivityIntent.startMainActivity(this, accountId)
        val options = Bundle().apply { putInt("android.activity.splashScreenStyle", 1) }
        startActivity(intent, options)
        finish()
    }

    private fun logout(pachliAccount: PachliAccount) {
        lifecycleScope.launch {
            val button = AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(app.pachli.core.ui.R.string.title_logout_fmt, pachliAccount.entity.fullName))
                .setMessage(getString(app.pachli.core.ui.R.string.action_logout_confirm, pachliAccount.entity.fullName))
                .create()
                .await(android.R.string.ok, android.R.string.cancel)

            if (button == AlertDialog.BUTTON_POSITIVE) {
                binding.mainDrawerLayout.close()
                binding.appBar.hide()
                binding.viewPager.hide()
                binding.progressBar.show()
                binding.bottomNav.hide()
                binding.composeButton.hide()

                // Don't logout in MainActivity as that will change the active account and
                // trigger operations (e.g., saveVisibleId) in running fragments. Instead,
                // logging out happens in the IntentRouter.
                val intent = IntentRouterActivityIntent.logout(this@MainActivity, pachliAccount.id)
                val options = Bundle().apply { putInt("android.activity.splashScreenStyle", 1) }
                startActivity(intent, options)
                finish()
            }
        }
    }

    /**
     * Binds the user's avatar image to the avatar view in the appropriate toolbar.
     *
     * @param avatarUrl URL for the image to load
     * @param showPlaceholder True if a placeholder image should be shown while loading
     */
    @SuppressLint("CheckResult")
    private fun bindDrawerAvatar(avatarUrl: String, showPlaceholder: Boolean) {
        val hideTopToolbar = viewModel.uiState.value.hideTopToolbar
        val animateAvatars = viewModel.uiState.value.animateAvatars

        val activeToolbar = if (hideTopToolbar) {
            when (sharedPreferencesRepository.mainNavigationPosition) {
                MainNavigationPosition.TOP -> binding.topNav
                MainNavigationPosition.BOTTOM -> binding.bottomNav
            }
        } else {
            binding.mainToolbar
        }

        val navIconSize = resources.getDimensionPixelSize(DR.dimen.avatar_toolbar_nav_icon_size)

        if (animateAvatars) {
            glide.asDrawable().load(avatarUrl).transform(RoundedCorners(resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp)))
                .apply { if (showPlaceholder) placeholder(DR.drawable.avatar_default) }
                .into(
                    object : CustomTarget<Drawable>(navIconSize, navIconSize) {
                        override fun onLoadStarted(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            if (resource is Animatable) resource.start()
                            activeToolbar.navigationIcon = FixedSizeDrawable(resource, navIconSize, navIconSize)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }
                    },
                )
        } else {
            glide.asBitmap().load(avatarUrl).transform(
                RoundedCorners(resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp)),
            )
                .apply { if (showPlaceholder) placeholder(DR.drawable.avatar_default) }
                .into(
                    object : CustomTarget<Bitmap>(navIconSize, navIconSize) {
                        override fun onLoadStarted(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            activeToolbar.navigationIcon = FixedSizeDrawable(
                                resource.toDrawable(resources),
                                navIconSize,
                                navIconSize,
                            )
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }
                    },
                )
        }
    }

    /**
     * Binds the server's announcements to the main drawer.
     *
     * Shows/clears a badge showing the number of unread announcements.
     */
    private suspend fun bindMainDrawerAnnouncements(announcements: List<Announcement>) = drawerMutex.withLock {
        val unread = announcements.count { !it.read }
        binding.mainDrawer.updateBadge(DRAWER_ITEM_ANNOUNCEMENTS, StringHolder(if (unread <= 0) null else unread.toString()))
    }

    /**
     * Sets the profile information in the main drawer header.
     */
    private fun bindMainDrawerProfileHeader(uiState: UiState) {
        val animateEmojis = uiState.animateEmojis
        val profiles: MutableList<IProfile> = uiState.accounts.map { acc ->
            ProfileDrawerItem().apply {
                isSelected = acc.isActive
                nameText = acc.displayName.emojify(glide, acc.emojis, header, animateEmojis)
                iconUrl = acc.profilePictureUrl
                isNameShown = true
                identifier = acc.id
                descriptionText = acc.fullName
            }
        }.toMutableList()

        // Re-use the already existing "Add account" and "Manage account" items.
        for (profile in header.profiles.orEmpty()) {
            if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile)
            }
            if (profile.identifier == DRAWER_ITEM_MANAGE_ACCOUNTS) {
                profiles.add(profile)
            }
        }
        header.clear()
        header.profiles = profiles
        val activeAccount = uiState.accounts.firstOrNull { it.isActive } ?: return
        header.setActiveProfile(activeAccount.id)
        binding.mainToolbar.subtitle = if (uiState.displaySelfUsername) {
            activeAccount.fullName
        } else {
            null
        }
    }

    companion object {
        private const val DRAWER_ITEM_ADD_ACCOUNT = -13L
        private const val DRAWER_ITEM_ANNOUNCEMENTS = 14L

        /** Drawer identifier for the "Lists" section header. */
        private const val DRAWER_ITEM_LISTS = 15L

        /** Drawer identifier for the "Drafts" item. */
        private const val DRAWER_ITEM_DRAFTS = 16L

        /** Drawer identifier for the "Search" item. */
        private const val DRAWER_ITEM_SEARCH = 17L

        /** Drawer identifier for the "Scheduled posts" item. */
        private const val DRAWER_ITEM_SCHEDULED_POSTS = 18L

        /** Drawer identifier for the "Manage accounts" item. */
        private const val DRAWER_ITEM_MANAGE_ACCOUNTS = -14L
    }
}

private inline fun primaryDrawerItem(block: PrimaryDrawerItem.() -> Unit): PrimaryDrawerItem {
    return PrimaryDrawerItem()
        .apply {
            isSelectable = false
            isIconTinted = true
        }
        .apply(block)
}

private inline fun secondaryDrawerItem(block: SecondaryDrawerItem.() -> Unit): SecondaryDrawerItem {
    return SecondaryDrawerItem()
        .apply {
            isSelectable = false
            isIconTinted = true
        }
        .apply(block)
}

private var AbstractDrawerItem<*, *>.onClick: () -> Unit
    get() = throw UnsupportedOperationException()
    set(value) {
        onDrawerItemClickListener = { _, _, _ ->
            value()
            false
        }
    }

/**
 * Load images in to the drawer using the [RequestManager] in [glide].
 */
// Can't pass `RequestManager` as a parameter here; it references the activity, and
// is leaked by MaterialDrawer. Since `MainDrawerImageLoader` is only used in an activity
// it's OK to use `Glide.with(View)` here, as the view's context is also the activity's
// context.
@SuppressLint("GlideWithViewDetector")
class MainDrawerImageLoader(val animateAvatars: Boolean) : AbstractDrawerImageLoader() {
    override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
        val glide = Glide.with(imageView)
        if (animateAvatars) {
            glide.load(uri).placeholder(placeholder).into(imageView)
        } else {
            glide.asBitmap().load(uri).placeholder(placeholder).into(imageView)
        }
    }

    override fun cancel(imageView: ImageView) {
        // Glide may throw an exception if the activity is being destroyed.
        // This can be safely ignored. See https://github.com/bumptech/glide/issues/5528.
        try {
            Glide.with(imageView).clear(imageView)
        } catch (_: IllegalArgumentException) {
            /* do nothing */
        }
    }

    override fun placeholder(ctx: Context, tag: String?): Drawable {
        if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
            return AppCompatResources.getDrawable(ctx, DR.drawable.avatar_default)!!
        }

        return super.placeholder(ctx, tag)
    }
}
