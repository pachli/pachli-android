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
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.MarginPageTransformer
import app.pachli.appstore.AnnouncementReadEvent
import app.pachli.appstore.CacheUpdater
import app.pachli.appstore.EventHub
import app.pachli.appstore.MainTabsChangedEvent
import app.pachli.appstore.ProfileEditedEvent
import app.pachli.components.account.AccountActivity
import app.pachli.components.accountlist.AccountListActivity
import app.pachli.components.announcements.AnnouncementsActivity
import app.pachli.components.compose.ComposeActivity
import app.pachli.components.compose.ComposeActivity.Companion.canHandleMimeType
import app.pachli.components.drafts.DraftsActivity
import app.pachli.components.login.LoginActivity
import app.pachli.components.notifications.createNotificationChannelsForAccount
import app.pachli.components.notifications.disableAllNotifications
import app.pachli.components.notifications.enablePushNotificationsWithFallback
import app.pachli.components.notifications.notificationsAreEnabled
import app.pachli.components.notifications.showMigrationNoticeIfNecessary
import app.pachli.components.preference.PreferencesActivity
import app.pachli.components.scheduled.ScheduledStatusActivity
import app.pachli.components.search.SearchActivity
import app.pachli.components.trending.TrendingActivity
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TabKind
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Notification
import app.pachli.core.preferences.PrefKeys
import app.pachli.databinding.ActivityMainBinding
import app.pachli.db.DraftsAlert
import app.pachli.interfaces.AccountSelectionListener
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.FabFragment
import app.pachli.interfaces.ReselectableFragment
import app.pachli.pager.MainPagerAdapter
import app.pachli.updatecheck.UpdateCheck
import app.pachli.updatecheck.UpdateNotificationFrequency
import app.pachli.usecase.DeveloperToolsUseCase
import app.pachli.usecase.LogoutUsecase
import app.pachli.util.EmbeddedFontFamily
import app.pachli.util.await
import app.pachli.util.deleteStaleCachedMedia
import app.pachli.util.emojify
import app.pachli.util.getDimension
import app.pachli.util.hide
import app.pachli.util.reduceSwipeSensitivity
import app.pachli.util.show
import app.pachli.util.unsafeLazy
import app.pachli.util.updateShortcut
import app.pachli.util.viewBinding
import at.connyduck.calladapter.networkresult.fold
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
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
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
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BottomSheetActivity(), ActionButtonActivity, MenuProvider {
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

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeAccount = accountManager.activeAccount
            ?: return // will be redirected to LoginActivity by BaseActivity

        var showNotificationTab = false

        // check for savedInstanceState in order to not handle intent events more than once
        if (intent != null && savedInstanceState == null) {
            val notificationId = intent.getIntExtra(NOTIFICATION_ID, -1)
            if (notificationId != -1) {
                // opened from a notification action, cancel the notification
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(intent.getStringExtra(NOTIFICATION_TAG), notificationId)
            }

            /** there are two possibilities the accountId can be passed to MainActivity:
             * - from our code as Long Intent Extra PACHLI_ACCOUNT_ID
             * - from share shortcuts as String 'android.intent.extra.shortcut.ID'
             */
            var pachliAccountId = intent.getLongExtra(PACHLI_ACCOUNT_ID, -1)
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

            val openDrafts = intent.getBooleanExtra(OPEN_DRAFTS, false)

            if (canHandleMimeType(intent.type) || intent.hasExtra(COMPOSE_OPTIONS)) {
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
                                    intent.putExtra(PACHLI_ACCOUNT_ID, requestedId)
                                    changeAccount(requestedId, intent)
                                }
                            }
                        },
                    )
                }
            } else if (openDrafts) {
                val intent = DraftsActivity.newIntent(this)
                startActivity(intent)
            } else if (accountRequested && intent.hasExtra(NOTIFICATION_TYPE)) {
                // user clicked a notification, show follow requests for type FOLLOW_REQUEST,
                // otherwise show notification tab
                if (intent.getSerializableExtra(NOTIFICATION_TYPE) == Notification.Type.FOLLOW_REQUEST) {
                    val intent = AccountListActivity.newIntent(this, AccountListActivity.Type.FOLLOW_REQUESTS)
                    startActivityWithSlideInAnimation(intent)
                } else {
                    showNotificationTab = true
                }
            }
        }
        window.statusBarColor = Color.TRANSPARENT // don't draw a status bar, the DrawerLayout and the MaterialDrawerLayout have their own
        setContentView(binding.root)

        glide = Glide.with(this)

        binding.composeButton.setOnClickListener {
            val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
            startActivity(composeIntent)
        }

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

        Schedulers.io().scheduleDirect {
            // Flush old media that was cached for sharing
            deleteStaleCachedMedia(applicationContext.getExternalFilesDir("Pachli"))
        }

        selectedEmojiPack = sharedPreferencesRepository.getString(EMOJI_PREFERENCE, "")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.mainDrawerLayout.isOpen -> {
                            binding.mainDrawerLayout.close()
                        }
                        binding.viewPager.currentItem != 0 -> {
                            binding.viewPager.currentItem = 0
                        }
                        else -> {
                            finish()
                        }
                    }
                }
            },
        )

        if (Build.VERSION.SDK_INT >= TIRAMISU && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(POST_NOTIFICATIONS), 1)
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this, true)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_main, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@MainActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.mainToolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)

        // If the main toolbar is hidden then there's no space in the top/bottomNav to show
        // the menu items as icons, so forceably disable them
        if (!binding.mainToolbar.isVisible) menu.forEach { it.setShowAsAction(SHOW_AS_ACTION_NEVER) }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(SearchActivity.getIntent(this@MainActivity))
                true
            }
            else -> super.onOptionsItemSelected(item)
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

        // TODO: This can be removed after 2024-03-01, assume everyone has upgraded by then
        if (sharedPreferencesRepository.getBoolean(PrefKeys.SHOW_JANKY_ANIMATION_WARNING, true)) {
            showJankyAnimationWarning()
        }

        checkForUpdate()
    }

    /** Warn the user about possibly-broken animations. */
    private fun showJankyAnimationWarning() = lifecycleScope.launch {
        AlertDialog.Builder(this@MainActivity)
            .setTitle(R.string.janky_animation_title)
            .setMessage(R.string.janky_animation_msg)
            .create()
            .await(android.R.string.ok)
        sharedPreferencesRepository.edit { putBoolean(PrefKeys.SHOW_JANKY_ANIMATION_WARNING, false) }
    }

    /**
     * Check for available updates, and prompt user to update.
     *
     * Show a dialog prompting the user to update if a newer version of the app is available.
     * The user can start an update, ignore this version, or dismiss all future update
     * notifications.
     */
    private fun checkForUpdate() = lifecycleScope.launch {
        val frequency = UpdateNotificationFrequency.from(sharedPreferencesRepository.getString(PrefKeys.UPDATE_NOTIFICATION_FREQUENCY, null))
        if (frequency == UpdateNotificationFrequency.NEVER) return@launch

        val latestVersionCode = updateCheck.getLatestVersionCode()

        if (latestVersionCode <= BuildConfig.VERSION_CODE) return@launch

        if (frequency == UpdateNotificationFrequency.ONCE_PER_VERSION) {
            val ignoredVersion = sharedPreferencesRepository.getInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, -1)
            if (latestVersionCode == ignoredVersion) {
                Timber.d("Ignoring update to $latestVersionCode")
                return@launch
            }
        }

        Timber.d("New version is: $latestVersionCode")
        when (showUpdateDialog()) {
            AlertDialog.BUTTON_POSITIVE -> {
                startActivity(updateCheck.updateIntent)
            }
            AlertDialog.BUTTON_NEUTRAL -> {
                with(sharedPreferencesRepository.edit()) {
                    putInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, latestVersionCode)
                    apply()
                }
            }
            AlertDialog.BUTTON_NEGATIVE -> {
                with(sharedPreferencesRepository.edit()) {
                    putString(
                        PrefKeys.UPDATE_NOTIFICATION_FREQUENCY,
                        UpdateNotificationFrequency.NEVER.name,
                    )
                    apply()
                }
            }
        }
    }

    private suspend fun showUpdateDialog() = AlertDialog.Builder(this)
        .setTitle(R.string.update_dialog_title)
        .setMessage(R.string.update_dialog_message)
        .setCancelable(true)
        .setIcon(R.mipmap.ic_launcher)
        .create()
        .await(R.string.update_dialog_positive, R.string.update_dialog_negative, R.string.update_dialog_neutral)

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
                startActivityWithSlideInAnimation(SearchActivity.getIntent(this))
                return true
            }
        }
        if (event.isCtrlPressed || event.isShiftPressed) {
            // FIXME: blackberry keyONE raises SHIFT key event even CTRL IS PRESSED
            when (keyCode) {
                KeyEvent.KEYCODE_N -> {
                    // open compose activity by pressing SHIFT + N (or CTRL + N)
                    val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
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
            val redirectUrl = intent.getStringExtra(REDIRECT_URL)
            if (redirectUrl != null) {
                viewUrl(redirectUrl, PostLookupFallbackBehavior.DISPLAY_ERROR)
            }
        }
    }

    private fun forwardToComposeActivity(intent: Intent) {
        val composeOptions = IntentCompat.getParcelableExtra(intent, COMPOSE_OPTIONS, ComposeActivity.ComposeOptions::class.java)

        val composeIntent = if (composeOptions != null) {
            ComposeActivity.startIntent(this, composeOptions)
        } else {
            Intent(this, ComposeActivity::class.java).apply {
                action = intent.action
                type = intent.type
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        startActivity(composeIntent)
        finish()
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

        DrawerImageLoader.init(
            object : AbstractDrawerImageLoader() {
                override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
                    if (animateAvatars) {
                        glide.load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                    } else {
                        glide.asBitmap()
                            .load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                    }
                }

                override fun cancel(imageView: ImageView) {
                    glide.clear(imageView)
                }

                override fun placeholder(ctx: Context, tag: String?): Drawable {
                    if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
                        return AppCompatResources.getDrawable(ctx, R.drawable.avatar_default)!!
                    }

                    return super.placeholder(ctx, tag)
                }
            },
        )

        binding.mainDrawer.apply {
            refreshMainDrawerItems(addSearchButton)
            setSavedInstance(savedInstanceState)
        }
    }

    private fun refreshMainDrawerItems(addSearchButton: Boolean) {
        binding.mainDrawer.apply {
            itemAdapter.clear()
            tintStatusBar = true
            addItems(
                primaryDrawerItem {
                    nameRes = R.string.action_edit_profile
                    iconicsIcon = GoogleMaterial.Icon.gmd_person
                    onClick = {
                        val intent = Intent(context, EditProfileActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_favourites
                    isSelectable = false
                    iconicsIcon = GoogleMaterial.Icon.gmd_star
                    onClick = {
                        val intent = StatusListActivity.newFavouritesIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_bookmarks
                    iconicsIcon = GoogleMaterial.Icon.gmd_bookmark
                    onClick = {
                        val intent = StatusListActivity.newBookmarksIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_follow_requests
                    iconicsIcon = GoogleMaterial.Icon.gmd_person_add
                    onClick = {
                        val intent = AccountListActivity.newIntent(context, AccountListActivity.Type.FOLLOW_REQUESTS)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_lists
                    iconicsIcon = GoogleMaterial.Icon.gmd_list
                    onClick = {
                        startActivityWithSlideInAnimation(ListsActivity.newIntent(context))
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_access_drafts
                    iconRes = R.drawable.ic_notebook
                    onClick = {
                        val intent = DraftsActivity.newIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_access_scheduled_posts
                    iconRes = R.drawable.ic_access_time
                    onClick = {
                        startActivityWithSlideInAnimation(ScheduledStatusActivity.newIntent(context))
                    }
                },
                primaryDrawerItem {
                    identifier = DRAWER_ITEM_ANNOUNCEMENTS
                    nameRes = R.string.title_announcements
                    iconRes = R.drawable.ic_bullhorn_24dp
                    onClick = {
                        startActivityWithSlideInAnimation(AnnouncementsActivity.newIntent(context))
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
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.ACCOUNT_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_view_preferences
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.GENERAL_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.about_title_activity
                    iconicsIcon = GoogleMaterial.Icon.gmd_info
                    onClick = {
                        val intent = Intent(context, AboutActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
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
                            startActivityWithSlideInAnimation(SearchActivity.getIntent(context))
                        }
                    },
                )
            }

            binding.mainDrawer.addItemsAtPosition(
                5,
                primaryDrawerItem {
                    nameRes = R.string.title_public_trending
                    iconicsIcon = GoogleMaterial.Icon.gmd_trending_up
                    onClick = {
                        startActivityWithSlideInAnimation(TrendingActivity.getIntent(context))
                    }
                },
            )
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
                    "Reset janky animation warning flag",
                ),
            ) { _, which ->
                Timber.d("Developer tools: $which")
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
                    2 -> {
                        developerToolsUseCase.resetJankyAnimationWarningFlag()
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
            val fabMargin = resources.getDimensionPixelSize(R.dimen.fabMargin)
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
        val previousTab = tabAdapter.tabs.getOrNull(binding.viewPager.currentItem)

        val tabs = accountManager.activeAccount!!.tabPreferences.map { TabViewData.from(it) }

        // Detach any existing mediator before changing tab contents and attaching a new mediator
        tabLayoutMediator?.detach()

        tabAdapter.tabs = tabs
        tabAdapter.notifyItemRangeChanged(0, tabs.size)

        tabLayoutMediator = TabLayoutMediator(activeTabLayout, binding.viewPager, true) {
                tab: TabLayout.Tab, position: Int ->
            tab.icon = AppCompatResources.getDrawable(this@MainActivity, tabs[position].icon)
            tab.contentDescription = when (tabs[position].kind) {
                TabKind.LIST -> tabs[position].arguments[1]
                else -> getString(tabs[position].text)
            }
        }.also { it.attach() }

        // Selected tab is either
        // - Notification tab (if appropriate)
        // - The previously selected tab (if it hasn't been removed)
        // - Left-most tab
        val position = if (selectNotificationTab) {
            tabs.indexOfFirst { it.kind == TabKind.NOTIFICATIONS }
        } else {
            previousTab?.let { tabs.indexOfFirst { it == previousTab } }
        }.takeIf { it != -1 } ?: 0
        binding.viewPager.setCurrentItem(position, false)

        val pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        binding.viewPager.setPageTransformer(MarginPageTransformer(pageMargin))

        val enableSwipeForTabs = sharedPreferencesRepository.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.viewPager.isUserInputEnabled = enableSwipeForTabs

        onTabSelectedListener?.let {
            activeTabLayout.removeOnTabSelectedListener(it)
        }

        onTabSelectedListener = object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.mainToolbar.title = tab.contentDescription

                refreshComposeButtonState(tabAdapter, tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                val fragment = tabAdapter.getFragment(tab.position)
                if (fragment is ReselectableFragment) {
                    (fragment as ReselectableFragment).onReselect()
                }

                refreshComposeButtonState(tabAdapter, tab.position)
            }
        }.also {
            activeTabLayout.addOnTabSelectedListener(it)
        }

        supportActionBar?.title = tabs[position].title(this@MainActivity)
        binding.mainToolbar.setOnClickListener {
            (tabAdapter.getFragment(activeTabLayout.selectedTabPosition) as? ReselectableFragment)?.onReselect()
        }

        updateProfiles()
    }

    private fun refreshComposeButtonState(adapter: MainPagerAdapter, tabPosition: Int) {
        adapter.getFragment(tabPosition)?.also { fragment ->
            if (fragment is FabFragment) {
                if (fragment.isFabVisible()) {
                    binding.composeButton.show()
                } else {
                    binding.composeButton.hide()
                }
            } else {
                binding.composeButton.show()
            }
        }
    }

    private fun handleProfileClick(profile: IProfile, current: Boolean) {
        val activeAccount = accountManager.activeAccount

        // open profile when active image was clicked
        if (current && activeAccount != null) {
            val intent = AccountActivity.getIntent(this, activeAccount.accountId)
            startActivityWithSlideInAnimation(intent)
            return
        }
        // open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(LoginActivity.getIntent(this, LoginActivity.MODE_ADDITIONAL_LOGIN))
            return
        }
        // change Account
        changeAccount(profile.identifier, null)
        return
    }

    private fun changeAccount(newSelectedId: Long, forward: Intent?) {
        cacheUpdater.stop()
        accountManager.setActiveAccount(newSelectedId)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (forward != null) {
            intent.type = forward.type
            intent.action = forward.action
            intent.putExtras(forward)
        }
        startActivity(intent)
        finishWithoutSlideOutAnimation()
        overridePendingTransition(
            R.anim.explode,
            R.anim.explode,
        )
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
                            Intent(this@MainActivity, MainActivity::class.java)
                        } else {
                            LoginActivity.getIntent(this@MainActivity, LoginActivity.MODE_DEFAULT)
                        }
                        startActivity(intent)
                        finishWithoutSlideOutAnimation()
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
                Timber.e("Failed to fetch user info. " + throwable.message)
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
        if (notificationsAreEnabled(this, accountManager)) {
            lifecycleScope.launch {
                enablePushNotificationsWithFallback(this@MainActivity, mastodonApi, accountManager)
            }
        } else {
            disableAllNotifications(this, accountManager)
        }

        updateProfiles()
        updateShortcut(this, accountManager.activeAccount!!)
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

        val navIconSize = resources.getDimensionPixelSize(R.dimen.avatar_toolbar_nav_icon_size)

        if (animateAvatars) {
            glide.asDrawable().load(avatarUrl).transform(
                RoundedCorners(
                    resources.getDimensionPixelSize(
                        R.dimen.avatar_radius_36dp,
                    ),
                ),
            )
                .apply { if (showPlaceholder) placeholder(R.drawable.avatar_default) }
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
                        R.dimen.avatar_radius_36dp,
                    ),
                ),
            )
                .apply { if (showPlaceholder) placeholder(R.drawable.avatar_default) }
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
                        Timber.w("Failed to fetch announcements.", throwable)
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
        private const val REDIRECT_URL = "redirectUrl"
        private const val OPEN_DRAFTS = "draft"
        private const val PACHLI_ACCOUNT_ID = "pachliAccountId"
        private const val COMPOSE_OPTIONS = "composeOptions"
        private const val NOTIFICATION_TYPE = "notificationType"
        private const val NOTIFICATION_TAG = "notificationTag"
        private const val NOTIFICATION_ID = "notificationId"

        /**
         * Switches the active account to the provided accountId and then stays on MainActivity
         */
        @JvmStatic
        fun accountSwitchIntent(context: Context, pachliAccountId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(PACHLI_ACCOUNT_ID, pachliAccountId)
            }
        }

        /**
         * Switches the active account to the accountId and takes the user to the correct place according to the notification they clicked
         */
        @JvmStatic
        fun openNotificationIntent(context: Context, pachliAccountId: Long, type: Notification.Type): Intent {
            return accountSwitchIntent(context, pachliAccountId).apply {
                putExtra(NOTIFICATION_TYPE, type)
            }
        }

        /**
         * Switches the active account to the accountId and then opens ComposeActivity with the provided options
         * @param pachliAccountId the id of the Pachli account to open the screen with. Set to -1 for current account.
         * @param notificationId optional id of the notification that should be cancelled when this intent is opened
         * @param notificationTag optional tag of the notification that should be cancelled when this intent is opened
         */
        @JvmStatic
        fun composeIntent(
            context: Context,
            options: ComposeActivity.ComposeOptions,
            pachliAccountId: Long = -1,
            notificationTag: String? = null,
            notificationId: Int = -1,
        ): Intent {
            return accountSwitchIntent(context, pachliAccountId).apply {
                action = Intent.ACTION_SEND // so it can be opened via shortcuts
                putExtra(COMPOSE_OPTIONS, options)
                putExtra(NOTIFICATION_TAG, notificationTag)
                putExtra(NOTIFICATION_ID, notificationId)
            }
        }

        /**
         * switches the active account to the accountId and then tries to resolve and show the provided url
         */
        @JvmStatic
        fun redirectIntent(context: Context, pachliAccountId: Long, url: String): Intent {
            return accountSwitchIntent(context, pachliAccountId).apply {
                putExtra(REDIRECT_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        /**
         * switches the active account to the provided accountId and then opens drafts
         */
        fun draftIntent(context: Context, pachliAccountId: Long): Intent {
            return accountSwitchIntent(context, pachliAccountId).apply {
                putExtra(OPEN_DRAFTS, true)
            }
        }
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
