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
import androidx.preference.PreferenceFragmentCompat
import app.pachli.R
import app.pachli.components.notifications.AndroidNotificationsAreEnabledUseCase
import app.pachli.components.notifications.disablePullNotifications
import app.pachli.components.notifications.enablePullNotifications
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.preferences.PrefKeys
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preferenceCategory
import app.pachli.settings.switchPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationPreferencesFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var androidNotificationsAreEnabledUseCase: AndroidNotificationsAreEnabledUseCase

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activeAccount = accountManager.activeAccount ?: return
        val context = requireContext()
        makePreferenceScreen {
            switchPreference {
                setTitle(R.string.pref_title_notifications_enabled)
                key = PrefKeys.NOTIFICATIONS_ENABLED
                isIconSpaceReserved = false
                isChecked = activeAccount.notificationsEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    accountManager.setNotificationsEnabled(activeAccount.id, newValue as Boolean)
                    if (androidNotificationsAreEnabledUseCase(context)) {
                        enablePullNotifications(context)
                    } else {
                        disablePullNotifications(context)
                    }
                    true
                }
            }

            preferenceCategory(R.string.pref_title_notification_filters) { category ->
                category.dependency = PrefKeys.NOTIFICATIONS_ENABLED
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follows)
                    key = PrefKeys.NOTIFICATIONS_FILTER_FOLLOWS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowed
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsFollowed(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follow_requests)
                    key = PrefKeys.NOTIFICATION_FILTER_FOLLOW_REQUESTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowRequested
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsFollowRequested(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reblogs)
                    key = PrefKeys.NOTIFICATION_FILTER_REBLOGS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReblogged
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsReblogged(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_favourites)
                    key = PrefKeys.NOTIFICATION_FILTER_FAVS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFavorited
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsFavorited(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_poll)
                    key = PrefKeys.NOTIFICATION_FILTER_POLLS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsPolls
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsPolls(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_subscriptions)
                    key = PrefKeys.NOTIFICATION_FILTER_SUBSCRIPTIONS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSubscriptions
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsSubscriptions(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_sign_ups)
                    key = PrefKeys.NOTIFICATION_FILTER_SIGN_UPS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSignUps
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsSignUps(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_updates)
                    key = PrefKeys.NOTIFICATION_FILTER_UPDATES
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsUpdates
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsUpdates(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reports)
                    key = PrefKeys.NOTIFICATION_FILTER_REPORTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReports
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationsReports(activeAccount.id, newValue as Boolean)
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_notification_alerts) { category ->
                category.dependency = PrefKeys.NOTIFICATIONS_ENABLED
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_sound)
                    key = PrefKeys.NOTIFICATION_ALERT_SOUND
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationSound
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationSound(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_vibrate)
                    key = PrefKeys.NOTIFICATION_ALERT_VIBRATE
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationVibration
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationVibration(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_light)
                    key = PrefKeys.NOTIFICATION_ALERT_LIGHT
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationLight
                    setOnPreferenceChangeListener { _, newValue ->
                        accountManager.setNotificationLight(activeAccount.id, newValue as Boolean)
                        true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_edit_notification_settings)
    }

    companion object {
        fun newInstance(): NotificationPreferencesFragment {
            return NotificationPreferencesFragment()
        }
    }
}
