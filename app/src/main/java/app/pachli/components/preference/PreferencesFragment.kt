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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.pachli.R
import app.pachli.db.AccountManager
import app.pachli.entity.Notification
import app.pachli.settings.AppTheme
import app.pachli.settings.PrefKeys
import app.pachli.settings.emojiPreference
import app.pachli.settings.listPreference
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preference
import app.pachli.settings.preferenceCategory
import app.pachli.settings.sliderPreference
import app.pachli.settings.switchPreference
import app.pachli.util.APP_THEME_DEFAULT
import app.pachli.util.LocaleManager
import app.pachli.util.deserialize
import app.pachli.util.makeIcon
import app.pachli.util.serialize
import app.pachli.util.unsafeLazy
import app.pachli.view.FontFamilyDialogFragment
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.views.picker.preference.EmojiPickerPreference
import javax.inject.Inject

@AndroidEntryPoint
class PreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var localeManager: LocaleManager

    private val iconSize by unsafeLazy { resources.getDimensionPixelSize(R.dimen.preference_icon_size) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            preferenceCategory(R.string.pref_title_appearance_settings) {
                listPreference {
                    setDefaultValue(APP_THEME_DEFAULT)
                    setEntries(R.array.app_theme_names)
                    entryValues = AppTheme.stringValues()
                    key = PrefKeys.APP_THEME
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_app_theme)
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
                    setDefaultValue("top")
                    setEntries(R.array.pref_main_nav_position_options)
                    setEntryValues(R.array.pref_main_nav_position_values)
                    key = PrefKeys.MAIN_NAV_POSITION
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_main_nav_position)
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
                    setDefaultValue(true)
                    key = PrefKeys.ENABLE_SWIPE_FOR_TABS
                    setTitle(R.string.pref_title_enable_swipe_for_tabs)
                    isSingleLineTitle = false
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.SHOW_STATS_INLINE
                    setTitle(R.string.pref_title_show_stat_inline)
                    isSingleLineTitle = false
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
