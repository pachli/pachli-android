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

package app.pachli.components.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.DividerItemDecoration
import app.pachli.R
import app.pachli.components.notifications.AccountNotificationMethod
import app.pachli.components.notifications.AppNotificationMethod
import app.pachli.components.notifications.getApplicationLabel
import app.pachli.components.notifications.hasPushScope
import app.pachli.components.notifications.notificationMethod
import app.pachli.core.activity.NotificationConfig
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.Notification
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.DownloadLocation
import app.pachli.core.preferences.MainNavigationPosition
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.extensions.await
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.AccountNotificationDetailsListItemBinding
import app.pachli.feature.about.asDdHhMmSs
import app.pachli.feature.about.instantFormatter
import app.pachli.settings.emojiPreference
import app.pachli.settings.enumListPreference
import app.pachli.settings.listPreference
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preference
import app.pachli.settings.preferenceCategory
import app.pachli.settings.sliderPreference
import app.pachli.settings.switchPreference
import app.pachli.updatecheck.UpdateCheck
import app.pachli.updatecheck.UpdateCheckResult.AT_LATEST
import app.pachli.updatecheck.UpdateNotificationFrequency
import app.pachli.util.LocaleManager
import app.pachli.util.deserialize
import app.pachli.util.serialize
import app.pachli.view.FontFamilyDialogFragment
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.views.picker.preference.EmojiPickerPreference
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

@AndroidEntryPoint
class PreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var updateCheck: UpdateCheck

    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    @Inject
    lateinit var powerManager: PowerManager

    private val iconSize by unsafeLazy { resources.getDimensionPixelSize(DR.dimen.preference_icon_size) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Show the "Check for update now" summary. This must also change
        // depending on the update notification frequency. You can't link two
        // preferences like that as a dependency, so listen for changes to
        // the relevant keys and update the summary when they change.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                sharedPreferencesRepository.changes.collect { prefKey ->
                    when (prefKey) {
                        PrefKeys.UPDATE_NOTIFICATION_FREQUENCY,
                        PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS,
                        -> {
                            findPreference<Preference>(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS)?.let {
                                it.summary = updateCheck.provideSummary(it)
                            }
                        }
                        else -> { /* do nothing */ }
                    }
                }
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @SuppressLint("BatteryLife", "ApplySharedPref")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            preferenceCategory(R.string.pref_title_appearance_settings) {
                enumListPreference<AppTheme> {
                    setDefaultValue(AppTheme.AUTO_SYSTEM)
                    setTitle(R.string.pref_title_app_theme)
                    key = PrefKeys.APP_THEME
                    icon = makeIcon(GoogleMaterial.Icon.gmd_palette)
                }

                emojiPreference(requireActivity()) {
                    setTitle(R.string.emoji_style)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_sentiment_satisfied)
                }

                listPreference {
                    setDefaultValue("default")
                    setEntries(R.array.language_entries)
                    setEntryValues(R.array.language_values)
                    key = PrefKeys.LANGUAGE + "_" // deliberately not the actual key, the real handling happens in LocaleManager
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_language)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_translate)
                    preferenceDataStore = localeManager
                }

                sliderPreference {
                    key = PrefKeys.UI_TEXT_SCALE_RATIO
                    setDefaultValue(100F)
                    valueTo = 150F
                    valueFrom = 50F
                    stepSize = 5F
                    setTitle(R.string.pref_ui_text_size)
                    format = "%.0f%%"
                    decrementIcon = makeIcon(GoogleMaterial.Icon.gmd_zoom_out)
                    incrementIcon = makeIcon(GoogleMaterial.Icon.gmd_zoom_in)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_format_size)
                }

                listPreference {
                    setDefaultValue("default")
                    setEntries(R.array.pref_font_family_names)
                    setEntryValues(R.array.pref_font_family_values)
                    key = PrefKeys.FONT_FAMILY
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_font_family)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_font_download)
                }

                listPreference {
                    setDefaultValue("medium")
                    setEntries(R.array.post_text_size_names)
                    setEntryValues(R.array.post_text_size_values)
                    key = PrefKeys.STATUS_TEXT_SIZE
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_post_text_size)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_format_size)
                }

                listPreference {
                    setDefaultValue("disambiguate")
                    setEntries(R.array.pref_show_self_username_names)
                    setEntryValues(R.array.pref_show_self_username_values)
                    key = PrefKeys.SHOW_SELF_USERNAME
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_show_self_username)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.HIDE_TOP_TOOLBAR
                    setTitle(R.string.pref_title_hide_top_toolbar)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.FAB_HIDE
                    setTitle(R.string.pref_title_hide_follow_button)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ABSOLUTE_TIME_VIEW
                    setTitle(R.string.pref_title_absolute_time)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.SHOW_BOT_OVERLAY
                    setTitle(R.string.pref_title_bot_overlay)
                    isSingleLineTitle = false
                    setIcon(R.drawable.ic_bot_24dp)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ANIMATE_GIF_AVATARS
                    setTitle(R.string.pref_title_animate_gif_avatars)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ANIMATE_CUSTOM_EMOJIS
                    setTitle(R.string.pref_title_animate_custom_emojis)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.USE_BLURHASH
                    setTitle(R.string.pref_title_gradient_for_media)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.SHOW_CARDS_IN_TIMELINES
                    setTitle(R.string.pref_title_show_cards_in_timelines)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.CONFIRM_REBLOGS
                    setTitle(R.string.pref_title_confirm_reblogs)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.CONFIRM_FAVOURITES
                    setTitle(R.string.pref_title_confirm_favourites)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.SHOW_STATS_INLINE
                    setTitle(R.string.pref_title_show_stat_inline)
                    isSingleLineTitle = false
                }
            }

            preferenceCategory(app.pachli.core.preferences.R.string.pref_category_tabs) {
                enumListPreference<MainNavigationPosition> {
                    setDefaultValue(MainNavigationPosition.TOP)
                    setTitle(R.string.pref_main_nav_position)
                    key = PrefKeys.MAIN_NAV_POSITION
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.ENABLE_SWIPE_FOR_TABS
                    setTitle(R.string.pref_title_enable_swipe_for_tabs)
                    isSingleLineTitle = false
                }
            }

            preferenceCategory(app.pachli.core.preferences.R.string.pref_category_downloads) {
                enumListPreference<DownloadLocation> {
                    setDefaultValue(DownloadLocation.DOWNLOADS)
                    setTitle(app.pachli.core.preferences.R.string.pref_title_downloads)
                    key = PrefKeys.DOWNLOAD_LOCATION
                    icon = makeIcon(GoogleMaterial.Icon.gmd_file_download)
                }
            }

            preferenceCategory(R.string.pref_title_edit_notification_settings) {
                val method = notificationMethod(context, accountManager)

                preference {
                    setTitle(R.string.pref_title_notification_up_distributor)

                    val distributorPkg = UnifiedPush.getAckDistributor(context)
                    val distributorLabel = distributorPkg?.let { getApplicationLabel(context, it) }

                    setSummaryProvider {
                        distributorLabel?.let {
                            context.getString(R.string.pref_notification_up_distributor_name_fmt, it)
                        } ?: context.getString(R.string.pref_notification_up_distributor_none)
                    }

                    setOnPreferenceClickListener {
                        distributorPkg?.let { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.also {
                                startActivity(it)
                            }
                        }

                        return@setOnPreferenceClickListener true
                    }
                }

                if (UnifiedPush.getDistributors(context).size > 1) {
                    preference {
                        setTitle(R.string.pref_title_change_unified_push_distributor)

                        setOnPreferenceClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val button = AlertDialog.Builder(context)
                                    .setMessage(R.string.pref_change_unified_push_distributor_msg)
                                    .setCancelable(true)
                                    .create()
                                    .await(R.string.restart, android.R.string.cancel)

                                if (button != AlertDialog.BUTTON_POSITIVE) return@launch

                                // Ideally UnifiedPush.forceRemoveDistributor would be used here
                                // and then the restart would force a new choice. However,
                                // forceRemoveDistributor triggers ConcurrentModificationException
                                // and crashes.
                                //
                                // So work around that by setting a preference to indicate that
                                // the chosen distributor should be ignored. This is then used
                                // in MainActivity and passed to chooseUnifiedPushDistributor.
                                sharedPreferencesRepository.edit().apply {
                                    putBoolean(PrefKeys.USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR, false)
                                }.commit()

                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)!!
                                val componentName = intent.component
                                val mainIntent = Intent.makeRestartActivityTask(componentName)
                                mainIntent.setPackage(context.packageName)
                                context.startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
                            }
                            return@setOnPreferenceClickListener true
                        }
                    }
                }

                preference {
                    setTitle(R.string.pref_title_notification_method)
                    setSummaryProvider {
                        val string = when (method) {
                            AppNotificationMethod.ALL_PUSH -> R.string.pref_notification_method_all_push
                            AppNotificationMethod.MIXED -> R.string.pref_notification_method_mixed
                            AppNotificationMethod.ALL_PULL -> R.string.pref_notification_method_all_pull
                        }
                        context.getString(string)
                    }

                    setOnPreferenceClickListener {
                        val adapter = AccountNotificationDetailsAdapter(context, accountManager.accounts.sortedBy { it.fullName })

                        viewLifecycleOwner.lifecycleScope.launch {
                            val dialog = AlertDialog.Builder(requireContext())
                                .setAdapter(adapter) { _, _ -> }
                                .create()
                            dialog.setOnShowListener {
                                dialog.listView.divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL).drawable

                                // Prevent a divider from appearing after the last item by disabling footer
                                // dividers and adding an empty footer.
                                dialog.listView.setFooterDividersEnabled(false)
                                dialog.listView.addFooterView(View(context))
                            }

                            dialog.await(positiveText = null)
                        }

                        return@setOnPreferenceClickListener true
                    }
                }

                preference {
                    setTitle(R.string.pref_title_notification_battery_optimisation)
                    val needsPull = method != AppNotificationMethod.ALL_PUSH
                    val isIgnoringBatteryOptimisations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    val shouldIgnore = needsPull && !isIgnoringBatteryOptimisations

                    setSummaryProvider {
                        when {
                            shouldIgnore -> context.getString(R.string.pref_notification_battery_optimisation_should_ignore)
                            isIgnoringBatteryOptimisations -> context.getString(R.string.pref_notification_battery_optimisation_is_disabled)
                            else -> context.getString(R.string.pref_notification_battery_optimisation_ok)
                        }
                    }
                    setOnPreferenceClickListener {
                        if (shouldIgnore) {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                        return@setOnPreferenceClickListener true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_browser_settings) {
                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.CUSTOM_TABS
                    setTitle(R.string.pref_title_custom_tabs)
                    isSingleLineTitle = false
                }
            }

            preferenceCategory(R.string.pref_title_timeline_filters) {
                preference {
                    setTitle(R.string.pref_title_post_tabs)
                    fragment = TabFilterPreferencesFragment::class.qualifiedName
                }
            }

            preferenceCategory(R.string.pref_title_wellbeing_mode) {
                switchPreference {
                    title = getString(R.string.limit_notifications)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_LIMITED_NOTIFICATIONS
                    setOnPreferenceChangeListener { _, value ->
                        for (account in accountManager.accounts) {
                            val notificationFilter = deserialize(account.notificationsFilter).toMutableSet()

                            if (value == true) {
                                notificationFilter.add(Notification.Type.FAVOURITE)
                                notificationFilter.add(Notification.Type.FOLLOW)
                                notificationFilter.add(Notification.Type.REBLOG)
                            } else {
                                notificationFilter.remove(Notification.Type.FAVOURITE)
                                notificationFilter.remove(Notification.Type.FOLLOW)
                                notificationFilter.remove(Notification.Type.REBLOG)
                            }

                            account.notificationsFilter = serialize(notificationFilter)
                            accountManager.saveAccount(account)
                        }
                        true
                    }
                }

                switchPreference {
                    title = getString(R.string.wellbeing_hide_stats_posts)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_HIDE_STATS_POSTS
                }

                switchPreference {
                    title = getString(R.string.wellbeing_hide_stats_profile)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_HIDE_STATS_PROFILE
                }
            }

            preferenceCategory(R.string.pref_title_proxy_settings) {
                preference {
                    setTitle(R.string.pref_title_http_proxy_settings)
                    fragment = ProxyPreferencesFragment::class.qualifiedName
                    summaryProvider = ProxyPreferencesFragment.SummaryProvider
                }
            }

            preferenceCategory(R.string.pref_title_update_settings) {
                it.icon = makeIcon(GoogleMaterial.Icon.gmd_upgrade)

                listPreference {
                    setDefaultValue(UpdateNotificationFrequency.ALWAYS.name)
                    setEntries(R.array.pref_update_notification_frequency_names)
                    setEntryValues(R.array.pref_update_notification_frequency_values)
                    key = PrefKeys.UPDATE_NOTIFICATION_FREQUENCY
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_update_notification_frequency)
                    isSingleLineTitle = false
                    icon = makeIcon(GoogleMaterial.Icon.gmd_calendar_today)
                }

                preference {
                    title = getString(R.string.pref_title_update_check_now)
                    key = PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS
                    setOnPreferenceClickListener {
                        lifecycleScope.launch {
                            if (updateCheck.checkForUpdate(this@PreferencesFragment.requireContext(), true) == AT_LATEST) {
                                Toast.makeText(
                                    this@PreferencesFragment.requireContext(),
                                    getString(R.string.pref_update_check_no_updates),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        return@setOnPreferenceClickListener true
                    }
                    summary = updateCheck.provideSummary(this)
                    icon = makeIcon(GoogleMaterial.Icon.gmd_refresh)
                }
            }

            preferenceCategory(R.string.pref_title_labs) {
                it.icon = makeIcon(GoogleMaterial.Icon.gmd_science)
                preference {
                    setTitle(R.string.pref_title_labs)
                    fragment = LabPreferencesFragment::class.qualifiedName
                }
            }
        }
    }

    private fun makeIcon(icon: GoogleMaterial.Icon): IconicsDrawable {
        return makeIcon(requireContext(), icon, iconSize)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.action_view_preferences)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (PrefKeys.FONT_FAMILY == preference.key) {
            val fragment = FontFamilyDialogFragment.newInstance(PrefKeys.FONT_FAMILY)
            fragment.setTargetFragment(this, 0)
            fragment.show(parentFragmentManager, FontFamilyDialogFragment.TXN_TAG)
            return
        }
        if (!EmojiPickerPreference.onDisplayPreferenceDialog(this, preference)) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        fun newInstance(): PreferencesFragment {
            return PreferencesFragment()
        }
    }
}

/**
 * Displays notification configuration information for each [AccountEntity].
 *
 * Shows:
 *
 * - Account's full name.
 * - The notification method (push or pull).
 * - If pull, an explanation for why it's not push.
 * - The last time notifications were fetched for the account, and the result.
 */
class AccountNotificationDetailsAdapter(context: Context, accounts: List<AccountEntity>) : ArrayAdapter<AccountEntity>(
    context,
    R.layout.account_notification_details_list_item,
    R.id.accountName,
    accounts,
) {

    /** String resource for the account's notification method. */
    @get:StringRes
    private val AccountNotificationMethod.stringRes: Int
        get() = when (this) {
            AccountNotificationMethod.PUSH -> R.string.pref_notification_method_push
            AccountNotificationMethod.PULL -> R.string.pref_notification_method_pull
        }

    /**
     * String to show as the "extra" for the notification method.
     *
     * If the notification method is [PUSH][AccountNotificationMethod.PUSH] this should be the
     * URL notifications are delivered to.
     *
     * Otherwise this should explain why the method is [PULL][AccountNotificationMethod.PULL]
     * (either the error when registering, or the lack of the `push` oauth scope).
     */
    private fun AccountEntity.notificationMethodExtra(): String {
        return when (notificationMethod) {
            AccountNotificationMethod.PUSH -> unifiedPushUrl
            AccountNotificationMethod.PULL -> if (hasPushScope) {
                context.getString(R.string.pref_notification_fetch_server_rejected, domain)
            } else {
                context.getString(R.string.pref_notification_fetch_needs_push)
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            AccountNotificationDetailsListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            AccountNotificationDetailsListItemBinding.bind(convertView)
        }

        val account = getItem(position) ?: return binding.root

        with(binding) {
            accountName.text = account.fullName
            notificationMethod.text = context.getString(account.notificationMethod.stringRes)
            notificationMethodExtra.text = account.notificationMethodExtra()

            accountName.show()
            notificationMethod.show()
            notificationMethodExtra.show()

            val lastFetch = NotificationConfig.lastFetchNewNotifications[account.fullName]
            if (lastFetch == null) {
                lastFetchTime.hide()
                lastFetchError.hide()
                return@with
            }

            val now = Instant.now()
            val instant = lastFetch.first
            val result = lastFetch.second

            val (resTimestamp, error) = when (result) {
                is Ok -> Pair(R.string.pref_notification_fetch_ok_timestamp_fmt, null)
                is Err -> Pair(R.string.pref_notification_fetch_err_timestamp_fmt, result.error)
            }

            lastFetchTime.text = context.getString(
                resTimestamp,
                Duration.between(instant, now).asDdHhMmSs(),
                instantFormatter.format(instant),
            )

            lastFetchTime.show()
            if (error != null) {
                lastFetchError.text = error
                lastFetchError.show()
            } else {
                lastFetchError.hide()
            }
        }

        return binding.root
    }
}
