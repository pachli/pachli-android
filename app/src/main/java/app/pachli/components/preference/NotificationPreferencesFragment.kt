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
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceFragmentCompat
import app.pachli.R
import app.pachli.components.notifications.disablePullNotifications
import app.pachli.components.notifications.domain.AndroidNotificationsAreEnabledUseCase
import app.pachli.components.notifications.enablePullNotifications
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
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
    lateinit var androidNotificationsAreEnabled: AndroidNotificationsAreEnabledUseCase

    private val viewModel: NotificationPreferencesViewModel by viewModels()

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
                    viewModel.setNotificationsEnabled(activeAccount.id, newValue as Boolean)
                    if (androidNotificationsAreEnabled(context)) {
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
                    setTitle(R.string.pref_title_notification_filter_mentions)
                    key = PrefKeys.NOTIFICATION_FILTER_MENTIONS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsMentioned
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsMentioned(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follows)
                    key = PrefKeys.NOTIFICATIONS_FILTER_FOLLOWS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowed
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsFollowed(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_follow_requests)
                    key = PrefKeys.NOTIFICATION_FILTER_FOLLOW_REQUESTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFollowRequested
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsFollowRequested(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reblogs)
                    key = PrefKeys.NOTIFICATION_FILTER_REBLOGS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReblogged
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsReblogged(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_favourites)
                    key = PrefKeys.NOTIFICATION_FILTER_FAVS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsFavorited
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsFavorited(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_poll)
                    key = PrefKeys.NOTIFICATION_FILTER_POLLS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsPolls
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsPolls(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_subscriptions)
                    key = PrefKeys.NOTIFICATION_FILTER_SUBSCRIPTIONS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSubscriptions
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsSubscriptions(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_sign_ups)
                    key = PrefKeys.NOTIFICATION_FILTER_SIGN_UPS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSignUps
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsSignUps(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_updates)
                    key = PrefKeys.NOTIFICATION_FILTER_UPDATES
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsUpdates
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsUpdates(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_reports)
                    key = PrefKeys.NOTIFICATION_FILTER_REPORTS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsReports
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsReports(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_severed_relationships)
                    key = PrefKeys.NOTIFICATION_FILTER_SEVERED_RELATIONSHIPS
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsSeveredRelationships
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsSeveredRelationships(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_filter_moderation_warning)
                    key = PrefKeys.NOTIFICATION_FILTER_MODERATION_WARNING
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationsModerationWarnings
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationsModerationWarnings(activeAccount.id, newValue as Boolean)
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
                        viewModel.setNotificationSound(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_vibrate)
                    key = PrefKeys.NOTIFICATION_ALERT_VIBRATE
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationVibration
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationVibration(activeAccount.id, newValue as Boolean)
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_title_notification_alert_light)
                    key = PrefKeys.NOTIFICATION_ALERT_LIGHT
                    isIconSpaceReserved = false
                    isChecked = activeAccount.notificationLight
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.setNotificationLight(activeAccount.id, newValue as Boolean)
                        true
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.applyDefaultWindowInsets()
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
