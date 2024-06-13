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
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.MarginPageTransformer
import app.pachli.appstore.AnnouncementReadEvent
import app.pachli.appstore.CacheUpdater
import app.pachli.appstore.EventHub
import app.pachli.appstore.MainTabsChangedEvent
import app.pachli.appstore.ProfileEditedEvent
import app.pachli.components.compose.ComposeActivity.Companion.canHandleMimeType
import app.pachli.components.notifications.androidNotificationsAreEnabled
import app.pachli.components.notifications.createNotificationChannelsForAccount
import app.pachli.components.notifications.disableAllNotifications
import app.pachli.components.notifications.enablePushNotificationsWithFallback
import app.pachli.components.notifications.showMigrationNoticeIfNecessary
import app.pachli.core.activity.AccountSelectionListener
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.PostLookupFallbackBehavior
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.emojify
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Lists
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.data.repository.ListsRepository.Companion.compareByListTitle
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.EmbeddedFontFamily
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.AboutActivityIntent
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AnnouncementsActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.DraftsActivityIntent
import app.pachli.core.navigation.EditProfileActivityIntent
import app.pachli.core.navigation.FollowedTagsActivityIntent
import app.pachli.core.navigation.ListActivityIntent
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent.PreferenceScreen
import app.pachli.core.navigation.ScheduledStatusActivityIntent
import app.pachli.core.navigation.SearchActivityIntent
import app.pachli.core.navigation.TabPreferenceActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.TrendingActivityIntent
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Notification
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.ActivityMainBinding
import app.pachli.db.DraftsAlert
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.pager.MainPagerAdapter
import app.pachli.updatecheck.UpdateCheck
import app.pachli.usecase.DeveloperToolsUseCase
import app.pachli.usecase.LogoutUsecase
import app.pachli.util.getDimension
import app.pachli.util.updateShortcut
import at.connyduck.calladapter.networkresult.fold
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.FixedSizeDrawable
import com.bumptech.glide.request.transition.Transition
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.materialdrawer.holder.BadgeStyle
import com.mikepenz.materialdrawer.holder.ColorHolder
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
import com.mikepenz.materialdrawer.util.addItems
import com.mikepenz.materialdrawer.util.addItemsAtPosition
import com.mikepenz.materialdrawer.util.updateBadge
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.helpers.EMOJI_PREFERENCE
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : BottomSheetActivity(), ActionButtonActivity, MenuProvider {
    @Inject
    @ApplicationScope
    lateinit var externalScope: CoroutineScope

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var cacheUpdater: CacheUpdater

    @Inject
    lateinit var logoutUsecase: LogoutUsecase

    @Inject
    lateinit var draftsAlert: DraftsAlert

    @Inject
    lateinit var updateCheck: UpdateCheck

    @Inject lateinit var listsRepository: ListsRepository

    @Inject
    lateinit var developerToolsUseCase: DeveloperToolsUseCase

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override val actionButton by unsafeLazy { binding.composeButton }

    private lateinit var header: AccountHeaderView

    private var onTabSelectedListener: OnTabSelectedListener? = null

    private var unreadAnnouncementsCount = 0

    private lateinit var glide: RequestManager

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

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeAccount = accountManager.activeAccount
            ?: return // will be redirected to LoginActivity by BaseActivity

        var showNotificationTab = false

        // check for savedInstanceState in order to not handle intent events more than once
        if (intent != null && savedInstanceState == null) {
            val notificationId = MainActivityIntent.getNotificationId(intent)
            if (notificationId != -1) {
                // opened from a notification action, cancel the notification
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(MainActivityIntent.getNotificationTag(intent), notificationId)
            }

            /** there are two possibilities the accountId can be passed to MainActivity:
             * - from our code as Long Intent Extra PACHLI_ACCOUNT_ID
             * - from share shortcuts as String 'android.intent.extra.shortcut.ID'
             */
            var pachliAccountId = MainActivityIntent.getPachliAccountId(intent)
            if (pachliAccountId == -1L) {
                val accountIdString = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
                if (accountIdString != null) {
                    pachliAccountId = accountIdString.toLong()
                }
            }
            val accountRequested = pachliAccountId != -1L
            if (accountRequested && pachliAccountId != activeAccount.id) {
                accountManager.setActiveAccount(pachliAccountId)
            }

            val openDrafts = MainActivityIntent.getOpenDrafts(intent)

            if (canHandleMimeType(intent.type) || MainActivityIntent.hasComposeOptions(intent)) {
                // Sharing to Tusky from an external app
                if (accountRequested) {
                    // The correct account is already active
                    forwardToComposeActivity(intent)
                } else {
                    // No account was provided, show the chooser
                    showAccountChooserDialog(
                        getString(R.string.action_share_as),
                        true,
                        object : AccountSelectionListener {
                            override fun onAccountSelected(account: AccountEntity) {
                                val requestedId = account.id
                                if (requestedId == activeAccount.id) {
                                    // The correct account is already active
                                    forwardToComposeActivity(intent)
                                } else {
                                    // A different account was requested, restart the activity
                                    MainActivityIntent.setPachliAccountId(intent, requestedId)
                                    changeAccount(requestedId, intent)
                                }
                            }
                        },
                    )
                }
            } else if (openDrafts) {
                val intent = DraftsActivityIntent(this)
                startActivity(intent)
            } else if (accountRequested && MainActivityIntent.hasNotificationType(intent)) {
                // user clicked a notification, show follow requests for type FOLLOW_REQUEST,
                // otherwise show notification tab
                if (MainActivityIntent.getNotificationType(intent) == Notification.Type.FOLLOW_REQUEST) {
                    val intent = AccountListActivityIntent(this, AccountListActivityIntent.Kind.FOLLOW_REQUESTS)
                    startActivityWithDefaultTransition(intent)
                } else {
                    showNotificationTab = true
                }
            }
        }
        window.statusBarColor = Color.TRANSPARENT // don't draw a status bar, the DrawerLayout and the MaterialDrawerLayout have their own
        setContentView(binding.root)

        glide = Glide.with(this)

        // Determine which of the three toolbars should be the supportActionBar (which hosts
        // the options menu).
        val hideTopToolbar = sharedPreferencesRepository.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        if (hideTopToolbar) {
            when (sharedPreferencesRepository.getString(PrefKeys.MAIN_NAV_POSITION, "top")) {
                "top" -> setSupportActionBar(binding.topNav)
                "bottom" -> setSupportActionBar(binding.bottomNav)
            }
            binding.mainToolbar.hide()
            // There's not enough space in the top/bottom bars to show the title as well.
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } else {
            setSupportActionBar(binding.mainToolbar)
            binding.mainToolbar.show()
        }

        loadDrawerAvatar(activeAccount.profilePictureUrl, true)

        addMenuProvider(this)

        binding.viewPager.reduceSwipeSensitivity()

        setupDrawer(
            savedInstanceState,
            addSearchButton = hideTopToolbar,
        )

        /* Fetch user info while we're doing other things. This has to be done after setting up the
         * drawer, though, because its callback touches the header in the drawer. */
        fetchUserInfo()

        fetchAnnouncements()

        // Initialise the tab adapter and set to viewpager. Fragments appear to be leaked if the
        // adapter changes over the life of the viewPager (the adapter, not its contents), so set
        // the initial list of tabs to empty, and set the full list later in setupTabs(). See
        // https://github.com/tuskyapp/Tusky/issues/3251 for details.
        tabAdapter = MainPagerAdapter(emptyList(), this)
        binding.viewPager.adapter = tabAdapter

        setupTabs(showNotificationTab)

        lifecycleScope.launch {
            eventHub.events.collect { event ->
                when (event) {
                    is ProfileEditedEvent -> onFetchUserInfoSuccess(event.newProfileData)
                    is MainTabsChangedEvent -> {
                        refreshMainDrawerItems(
                            addSearchButton = hideTopToolbar,
                        )

                        setupTabs(false)
                    }

                    is AnnouncementReadEvent -> {
                        unreadAnnouncementsCount--
                        updateAnnouncementsBadge()
                    }
                }
            }
        }

        lifecycleScope.launch {
            listsRepository.lists.collect { result ->
                result.onSuccess { lists ->
                    // Update the list of lists in the main drawer
                    refreshMainDrawerItems(addSearchButton = hideTopToolbar)

                    // Any lists in tabs might have changed titles, update those
                    if (lists is Lists.Loaded && tabAdapter.tabs.any { it.timeline is Timeline.UserList }) {
                        setupTabs(false)
                    }
                }

                result.onFailure {
                    Snackbar.make(binding.root, R.string.error_list_load, Snackbar.LENGTH_INDEFINITE)
                        .setAction(app.pachli.core.ui.R.string.action_retry) { listsRepository.refresh() }
                        .show()
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
        super<BottomSheetActivity>.onPrepareMenu(menu)

        menu.findItem(R.id.action_remove_tab).isVisible = tabAdapter.tabs[binding.viewPager.currentItem].timeline != Timeline.Home

        // If the main toolbar is hidden then there's no space in the top/bottomNav to show
        // the menu items as icons, so forceably disable them
        if (!binding.mainToolbar.isVisible) menu.forEach { it.setShowAsAction(SHOW_AS_ACTION_NEVER) }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return when (menuItem.itemId) {
            R.id.action_search -> {
                startActivity(SearchActivityIntent(this@MainActivity))
                true
            }
            R.id.action_remove_tab -> {
                val timeline = tabAdapter.tabs[binding.viewPager.currentItem].timeline
                accountManager.activeAccount?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        it.tabPreferences = it.tabPreferences.filterNot { it == timeline }
                        accountManager.saveAccount(it)
                        eventHub.dispatch(MainTabsChangedEvent(it.tabPreferences))
                    }
                }
                true
            }
            R.id.action_tab_preferences -> {
                startActivity(TabPreferenceActivityIntent(this))
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
                "onResume: EmojiPack has been changed from %s to %s"
                    .format(selectedEmojiPack, currentEmojiPack),
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
                startActivityWithDefaultTransition(SearchActivityIntent(this))
                return true
            }
        }
        if (event.isCtrlPressed || event.isShiftPressed) {
            // FIXME: blackberry keyONE raises SHIFT key event even CTRL IS PRESSED
            when (keyCode) {
                KeyEvent.KEYCODE_N -> {
                    // open compose activity by pressing SHIFT + N (or CTRL + N)
                    val composeIntent = ComposeActivityIntent(applicationContext)
                    startActivity(composeIntent)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    public override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        if (intent != null) {
            val redirectUrl = MainActivityIntent.getRedirectUrl(intent)
            if (redirectUrl != null) {
                viewUrl(redirectUrl, PostLookupFallbackBehavior.DISPLAY_ERROR)
            }
        }
    }

    private fun forwardToComposeActivity(intent: Intent) {
        val composeOptions = ComposeActivityIntent.getOptions(intent)

        val composeIntent = if (composeOptions != null) {
            ComposeActivityIntent(this, composeOptions)
        } else {
            ComposeActivityIntent(this).apply {
                action = intent.action
                type = intent.type
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        startActivity(composeIntent)

        // Recreate the activity to ensure it is using the correct active account
        // (which may have changed while processing the compose intent) and so
        // the user returns to the timeline when they finish ComposeActivity.
        recreate()
    }

    private fun setupDrawer(
        savedInstanceState: Bundle?,
        addSearchButton: Boolean,
    ) {
        val drawerOpenClickListener = View.OnClickListener { binding.mainDrawerLayout.open() }

        binding.mainToolbar.setNavigationOnClickListener(drawerOpenClickListener)
        binding.topNav.setNavigationOnClickListener(drawerOpenClickListener)
        binding.bottomNav.setNavigationOnClickListener(drawerOpenClickListener)

        header = AccountHeaderView(this).apply {
            headerBackgroundScaleType = ImageView.ScaleType.CENTER_CROP
            currentHiddenInList = true
            onAccountHeaderListener = { _: View?, profile: IProfile, current: Boolean ->
                handleProfileClick(profile, current)
                false
            }
            addProfile(
                ProfileSettingDrawerItem().apply {
                    identifier = DRAWER_ITEM_ADD_ACCOUNT
                    nameRes = R.string.add_account_name
                    descriptionRes = R.string.add_account_description
                    iconicsIcon = GoogleMaterial.Icon.gmd_add
                },
                0,
            )
            attachToSliderView(binding.mainDrawer)
            dividerBelowHeader = false
            closeDrawerOnProfileListClick = true
        }

        header.currentProfileName.maxLines = 1
        header.currentProfileName.ellipsize = TextUtils.TruncateAt.END

        // Account header background and text colours are not styleable, so set them here
        header.accountHeaderBackground.setBackgroundColor(
            MaterialColors.getColor(
                header,
                com.google.android.material.R.attr.colorSecondaryContainer,
            ),
        )
        val headerTextColor = MaterialColors.getColor(header, com.google.android.material.R.attr.colorOnSecondaryContainer)
        header.currentProfileName.setTextColor(headerTextColor)
        header.currentProfileEmail.setTextColor(headerTextColor)

        val animateAvatars = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)

        DrawerImageLoader.init(MainDrawerImageLoader(glide, animateAvatars))

        binding.mainDrawer.apply {
            refreshMainDrawerItems(addSearchButton)
            setSavedInstance(savedInstanceState)
        }

        binding.mainDrawerLayout.addDrawerListener(object : DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) { }

            override fun onDrawerOpened(drawerView: View) {
                onBackPressedCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                onBackPressedCallback.isEnabled = binding.tabLayout.selectedTabPosition > 0
            }

            override fun onDrawerStateChanged(newState: Int) { }
        })
    }

    private fun refreshMainDrawerItems(addSearchButton: Boolean) {
        val (listsDrawerItems, listsSectionTitle) = listsRepository.lists.value.getOrElse { null }?.let { result ->
            when (result) {
                Lists.Loading -> Pair(emptyList(), R.string.title_lists_loading)
                is Lists.Loaded -> Pair(
                    result.lists.sortedWith(compareByListTitle)
                        .map { list ->
                            primaryDrawerItem {
                                nameText = list.title
                                iconicsIcon = GoogleMaterial.Icon.gmd_list
                                onClick = {
                                    startActivityWithDefaultTransition(
                                        TimelineActivityIntent.list(this@MainActivity, list.id, list.title),
                                    )
                                }
                            }
                        },
                    app.pachli.feature.lists.R.string.title_lists,
                )
            }
        } ?: Pair(emptyList(), R.string.title_lists_failed)

        binding.mainDrawer.apply {
            itemAdapter.clear()
            tintStatusBar = true
            addItems(
                primaryDrawerItem {
                    nameRes = R.string.title_notifications
                    iconicsIcon = GoogleMaterial.Icon.gmd_notifications
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.notifications(context),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_local
                    iconRes = R.drawable.ic_local_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.publicLocal(context),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_federated
                    iconRes = R.drawable.ic_public_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.publicFederated(context),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_direct_messages
                    iconRes = R.drawable.ic_reblog_direct_24dp
                    onClick = {
                        startActivityWithDefaultTransition(
                            TimelineActivityIntent.conversations(context),
                        )
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_bookmarks
                    iconicsIcon = GoogleMaterial.Icon.gmd_bookmark
                    onClick = {
                        val intent = TimelineActivityIntent.bookmarks(context)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_favourites
                    isSelectable = false
                    iconicsIcon = GoogleMaterial.Icon.gmd_star
                    onClick = {
                        val intent = TimelineActivityIntent.favourites(context)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_public_trending
                    iconicsIcon = GoogleMaterial.Icon.gmd_trending_up
                    onClick = {
                        startActivityWithDefaultTransition(TrendingActivityIntent(context))
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.title_followed_hashtags
                    iconRes = R.drawable.ic_hashtag
                    onClick = {
                        startActivityWithDefaultTransition(FollowedTagsActivityIntent(context))
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_follow_requests
                    iconicsIcon = GoogleMaterial.Icon.gmd_person_add
                    onClick = {
                        val intent = AccountListActivityIntent(context, AccountListActivityIntent.Kind.FOLLOW_REQUESTS)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                SectionDrawerItem().apply {
                    nameRes = listsSectionTitle
                },
                *listsDrawerItems.toTypedArray(),
                primaryDrawerItem {
                    nameRes = R.string.manage_lists
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        startActivityWithDefaultTransition(ListActivityIntent(context))
                    }
                },
                DividerDrawerItem(),
                primaryDrawerItem {
                    nameRes = R.string.action_access_drafts
                    iconRes = R.drawable.ic_notebook
                    onClick = {
                        val intent = DraftsActivityIntent(context)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_access_scheduled_posts
                    iconRes = R.drawable.ic_access_time
                    onClick = {
                        startActivityWithDefaultTransition(ScheduledStatusActivityIntent(context))
                    }
                },
                primaryDrawerItem {
                    identifier = DRAWER_ITEM_ANNOUNCEMENTS
                    nameRes = R.string.title_announcements
                    iconRes = R.drawable.ic_bullhorn_24dp
                    onClick = {
                        startActivityWithDefaultTransition(AnnouncementsActivityIntent(context))
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
                        val intent = PreferencesActivityIntent(context, PreferenceScreen.ACCOUNT)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_view_preferences
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        val intent = PreferencesActivityIntent(context, PreferenceScreen.GENERAL)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_edit_profile
                    iconicsIcon = GoogleMaterial.Icon.gmd_person
                    onClick = {
                        val intent = EditProfileActivityIntent(context)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = app.pachli.feature.about.R.string.about_title_activity
                    iconicsIcon = GoogleMaterial.Icon.gmd_info
                    onClick = {
                        val intent = AboutActivityIntent(context)
                        startActivityWithDefaultTransition(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_logout
                    iconRes = R.drawable.ic_logout
                    onClick = ::logout
                },
            )

            if (addSearchButton) {
                binding.mainDrawer.addItemsAtPosition(
                    4,
                    primaryDrawerItem {
                        nameRes = R.string.action_search
                        iconicsIcon = GoogleMaterial.Icon.gmd_search
                        onClick = {
                            startActivityWithDefaultTransition(SearchActivityIntent(context))
                        }
                    },
                )
            }
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

        updateMainDrawerTypeface(
            EmbeddedFontFamily.from(sharedPreferencesRepository.getString(PrefKeys.FONT_FAMILY, "default")),
        )
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
                            accountManager.activeAccount?.let {
                                developerToolsUseCase.clearHomeTimelineCache(it.id)
                            }
                        }
                    }
                    1 -> {
                        Timber.d("Removing most recent 40 statuses")
                        lifecycleScope.launch {
                            accountManager.activeAccount?.let {
                                developerToolsUseCase.deleteFirstKStatuses(it.id, 40)
                            }
                        }
                    }
                }
            }
            .create()
    }

    /**
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

    private fun setupTabs(selectNotificationTab: Boolean) {
        val activeTabLayout = if (sharedPreferencesRepository.getString(PrefKeys.MAIN_NAV_POSITION, "top") == "bottom") {
            val actionBarSize = getDimension(this, androidx.appcompat.R.attr.actionBarSize)
            val fabMargin = resources.getDimensionPixelSize(DR.dimen.fabMargin)
            (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = actionBarSize + fabMargin
            binding.topNav.hide()
            binding.bottomTabLayout
        } else {
            binding.bottomNav.hide()
            (binding.viewPager.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = 0
            (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).anchorId =
                R.id.viewPager
            binding.tabLayout
        }

        // Save the previous tab so it can be restored later
        val previousTabIndex = binding.viewPager.currentItem
        val previousTab = tabAdapter.tabs.getOrNull(previousTabIndex)

        val tabs = accountManager.activeAccount!!.tabPreferences.map { TabViewData.from(it) }

        // Detach any existing mediator before changing tab contents and attaching a new mediator
        tabLayoutMediator?.detach()

        tabAdapter.tabs = tabs
        tabAdapter.notifyItemRangeChanged(0, tabs.size)

        tabLayoutMediator = TabLayoutMediator(activeTabLayout, binding.viewPager, true) {
                tab: TabLayout.Tab, position: Int ->
            tab.icon = AppCompatResources.getDrawable(this@MainActivity, tabs[position].icon)
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

        val enableSwipeForTabs = sharedPreferencesRepository.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.viewPager.isUserInputEnabled = enableSwipeForTabs

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

        updateProfiles()
    }

    private fun refreshComposeButtonState(tabViewData: TabViewData) {
        tabViewData.composeIntent?.let { intent ->
            binding.composeButton.setOnClickListener {
                startActivity(intent(applicationContext))
            }
            binding.composeButton.show()
        } ?: binding.composeButton.hide()
    }

    private fun handleProfileClick(profile: IProfile, current: Boolean) {
        val activeAccount = accountManager.activeAccount

        // open profile when active image was clicked
        if (current && activeAccount != null) {
            val intent = AccountActivityIntent(this, activeAccount.accountId)
            startActivityWithDefaultTransition(intent)
            return
        }
        // open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithDefaultTransition(
                LoginActivityIntent(this, LoginMode.ADDITIONAL_LOGIN),
            )
            return
        }
        // change Account
        changeAccount(profile.identifier, null)
        return
    }

    private fun changeAccount(newSelectedId: Long, forward: Intent?) {
        cacheUpdater.stop()
        accountManager.setActiveAccount(newSelectedId)
        val intent = MainActivityIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (forward != null) {
            intent.type = forward.type
            intent.action = forward.action
            intent.putExtras(forward)
        }
        startActivityWithTransition(intent, TransitionKind.EXPLODE)
        finish()
    }

    private fun logout() {
        accountManager.activeAccount?.let { activeAccount ->
            AlertDialog.Builder(this)
                .setTitle(R.string.action_logout)
                .setMessage(getString(R.string.action_logout_confirm, activeAccount.fullName))
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    binding.appBar.hide()
                    binding.viewPager.hide()
                    binding.progressBar.show()
                    binding.bottomNav.hide()
                    binding.composeButton.hide()

                    lifecycleScope.launch {
                        val otherAccountAvailable = logoutUsecase.logout()
                        val intent = if (otherAccountAvailable) {
                            MainActivityIntent(this@MainActivity)
                        } else {
                            LoginActivityIntent(this@MainActivity, LoginMode.DEFAULT)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun fetchUserInfo() = lifecycleScope.launch {
        mastodonApi.accountVerifyCredentials().fold(
            { userInfo ->
                onFetchUserInfoSuccess(userInfo)
            },
            { throwable ->
                Timber.e(throwable, "Failed to fetch user info.")
            },
        )
    }

    private fun onFetchUserInfoSuccess(me: Account) {
        glide.asBitmap()
            .load(me.header)
            .into(header.accountHeaderBackground)

        loadDrawerAvatar(me.avatar, false)

        accountManager.updateActiveAccount(me)
        createNotificationChannelsForAccount(accountManager.activeAccount!!, this)

        // Setup push notifications
        showMigrationNoticeIfNecessary(
            this,
            binding.mainCoordinatorLayout,
            binding.composeButton,
            accountManager,
            sharedPreferencesRepository,
        )
        if (androidNotificationsAreEnabled(this, accountManager)) {
            lifecycleScope.launch {
                enablePushNotificationsWithFallback(this@MainActivity, mastodonApi, accountManager)
            }
        } else {
            disableAllNotifications(this, accountManager)
        }

        updateProfiles()

        externalScope.launch {
            updateShortcut(applicationContext, accountManager.activeAccount!!)
        }
    }

    @SuppressLint("CheckResult")
    private fun loadDrawerAvatar(avatarUrl: String, showPlaceholder: Boolean) {
        val hideTopToolbar = sharedPreferencesRepository.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        val animateAvatars = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)

        val activeToolbar = if (hideTopToolbar) {
            val navOnBottom = sharedPreferencesRepository.getString(PrefKeys.MAIN_NAV_POSITION, "top") == "bottom"
            if (navOnBottom) {
                binding.bottomNav
            } else {
                binding.topNav
            }
        } else {
            binding.mainToolbar
        }

        val navIconSize = resources.getDimensionPixelSize(DR.dimen.avatar_toolbar_nav_icon_size)

        if (animateAvatars) {
            glide.asDrawable().load(avatarUrl).transform(
                RoundedCorners(
                    resources.getDimensionPixelSize(
                        DR.dimen.avatar_radius_36dp,
                    ),
                ),
            )
                .apply { if (showPlaceholder) placeholder(DR.drawable.avatar_default) }
                .into(
                    object : CustomTarget<Drawable>(navIconSize, navIconSize) {

                        override fun onLoadStarted(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?,
                        ) {
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
                RoundedCorners(
                    resources.getDimensionPixelSize(
                        DR.dimen.avatar_radius_36dp,
                    ),
                ),
            )
                .apply { if (showPlaceholder) placeholder(DR.drawable.avatar_default) }
                .into(
                    object : CustomTarget<Bitmap>(navIconSize, navIconSize) {
                        override fun onLoadStarted(placeholder: Drawable?) {
                            placeholder?.let {
                                activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?,
                        ) {
                            activeToolbar.navigationIcon = FixedSizeDrawable(
                                BitmapDrawable(resources, resource),
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

    private fun fetchAnnouncements() {
        lifecycleScope.launch {
            mastodonApi.listAnnouncements(false)
                .fold(
                    { announcements ->
                        unreadAnnouncementsCount = announcements.count { !it.read }
                        updateAnnouncementsBadge()
                    },
                    { throwable ->
                        Timber.w(throwable, "Failed to fetch announcements.")
                    },
                )
        }
    }

    private fun updateAnnouncementsBadge() {
        binding.mainDrawer.updateBadge(DRAWER_ITEM_ANNOUNCEMENTS, StringHolder(if (unreadAnnouncementsCount <= 0) null else unreadAnnouncementsCount.toString()))
    }

    private fun updateProfiles() {
        val animateEmojis = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val profiles: MutableList<IProfile> =
            accountManager.getAllAccountsOrderedByActive().map { acc ->
                ProfileDrawerItem().apply {
                    isSelected = acc.isActive
                    nameText = acc.displayName.emojify(acc.emojis, header, animateEmojis)
                    iconUrl = acc.profilePictureUrl
                    isNameShown = true
                    identifier = acc.id
                    descriptionText = acc.fullName
                }
            }.toMutableList()

        // reuse the already existing "add account" item
        for (profile in header.profiles.orEmpty()) {
            if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile)
                break
            }
        }
        header.clear()
        header.profiles = profiles
        header.setActiveProfile(accountManager.activeAccount!!.id)
        binding.mainToolbar.subtitle = if (accountManager.shouldDisplaySelfUsername(this)) {
            accountManager.activeAccount!!.fullName
        } else {
            null
        }
    }

    companion object {
        private const val DRAWER_ITEM_ADD_ACCOUNT: Long = -13
        private const val DRAWER_ITEM_ANNOUNCEMENTS: Long = 14
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
class MainDrawerImageLoader(val glide: RequestManager, val animateAvatars: Boolean) : AbstractDrawerImageLoader() {
    override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
        if (animateAvatars) {
            glide.load(uri).placeholder(placeholder).into(imageView)
        } else {
            glide.asBitmap().load(uri).placeholder(placeholder).into(imageView)
        }
    }

    override fun cancel(imageView: ImageView) {
        glide.clear(imageView)
    }

    override fun placeholder(ctx: Context, tag: String?): Drawable {
        if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
            return AppCompatResources.getDrawable(ctx, DR.drawable.avatar_default)!!
        }

        return super.placeholder(ctx, tag)
    }
}
