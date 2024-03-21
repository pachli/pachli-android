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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.preference.PreferenceFragmentCompat
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.appstore.EventHub
import app.pachli.components.notifications.currentAccountNeedsMigration
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.FiltersActivityIntent
import app.pachli.core.navigation.FollowedTagsActivityIntent
import app.pachli.core.navigation.InstanceListActivityIntent
import app.pachli.core.navigation.LoginActivityIntent
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.PreferencesActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent.PreferenceScreen
import app.pachli.core.navigation.TabPreferenceActivityIntent
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.preferences.PrefKeys
import app.pachli.network.ServerRepository
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.settings.listPreference
import app.pachli.settings.makePreferenceScreen
import app.pachli.settings.preference
import app.pachli.settings.preferenceCategory
import app.pachli.settings.switchPreference
import app.pachli.util.getInitialLanguages
import app.pachli.util.getLocaleList
import app.pachli.util.getPachliDisplayName
import app.pachli.util.iconRes
import app.pachli.util.makeIcon
import com.github.michaelbull.result.getOrElse
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

@AndroidEntryPoint
class AccountPreferencesFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var accountPreferenceDataStore: AccountPreferenceDataStore

    private val iconSize by unsafeLazy { resources.getDimensionPixelSize(DR.dimen.preference_icon_size) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        makePreferenceScreen {
            preference {
                setTitle(R.string.pref_title_edit_notification_settings)
                icon = makeIcon(GoogleMaterial.Icon.gmd_notifications)
                setOnPreferenceClickListener {
                    openNotificationSystemPrefs()
                    true
                }
            }

            preference {
                setTitle(R.string.title_tab_preferences)
                setIcon(R.drawable.ic_tabs)
                setOnPreferenceClickListener {
                    val intent = TabPreferenceActivityIntent(context)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        DR.anim.slide_from_right,
                        DR.anim.slide_to_left,
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.title_followed_hashtags)
                setIcon(R.drawable.ic_hashtag)
                setOnPreferenceClickListener {
                    val intent = FollowedTagsActivityIntent(context)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        DR.anim.slide_from_right,
                        DR.anim.slide_to_left,
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_mutes)
                setIcon(R.drawable.ic_mute_24dp)
                setOnPreferenceClickListener {
                    val intent = AccountListActivityIntent(context, AccountListActivityIntent.Kind.MUTES)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        DR.anim.slide_from_right,
                        DR.anim.slide_to_left,
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_blocks)
                icon = makeIcon(GoogleMaterial.Icon.gmd_block)
                setOnPreferenceClickListener {
                    val intent = AccountListActivityIntent(context, AccountListActivityIntent.Kind.BLOCKS)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        DR.anim.slide_from_right,
                        DR.anim.slide_to_left,
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.title_domain_mutes)
                setIcon(R.drawable.ic_mute_24dp)
                setOnPreferenceClickListener {
                    val intent = InstanceListActivityIntent(context)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        DR.anim.slide_from_right,
                        DR.anim.slide_to_left,
                    )
                    true
                }
            }

            if (currentAccountNeedsMigration(accountManager)) {
                preference {
                    setTitle(R.string.title_migration_relogin)
                    setIcon(R.drawable.ic_logout)
                    setOnPreferenceClickListener {
                        val intent = LoginActivityIntent(context, LoginMode.MIGRATION)
                        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
                        true
                    }
                }
            }

            preference {
                setTitle(R.string.pref_title_timeline_filters)
                setIcon(R.drawable.ic_filter_24dp)
                setOnPreferenceClickListener {
                    launchFilterActivity()
                    true
                }
                val server = serverRepository.flow.value.getOrElse { null }
                isEnabled = server?.let {
                    it.can(
                        ORG_JOINMASTODON_FILTERS_CLIENT, ">1.0.0".toConstraint(),
                    ) || it.can(
                        ORG_JOINMASTODON_FILTERS_SERVER, ">1.0.0".toConstraint(),
                    )
                } ?: false
                if (!isEnabled) summary = context.getString(R.string.pref_summary_timeline_filters)
            }

            preferenceCategory(R.string.pref_publishing) {
                listPreference {
                    setTitle(R.string.pref_default_post_privacy)
                    setEntries(R.array.post_privacy_names)
                    setEntryValues(R.array.post_privacy_values)
                    key = PrefKeys.DEFAULT_POST_PRIVACY
                    setSummaryProvider { entry }
                    val visibility = accountManager.activeAccount?.defaultPostPrivacy ?: Status.Visibility.PUBLIC
                    value = visibility.serverString()
                    visibility.iconRes()?.let { setIcon(it) }
                    setOnPreferenceChangeListener { _, newValue ->
                        Status.Visibility.byString(newValue as String).iconRes()?.let { setIcon(it) }
                        syncWithServer(visibility = newValue)
                        true
                    }
                }

                listPreference {
                    val locales = getLocaleList(getInitialLanguages(null, accountManager.activeAccount))
                    setTitle(R.string.pref_default_post_language)
                    // Explicitly add "System default" to the start of the list
                    entries = (
                        listOf(context.getString(R.string.system_default)) + locales.map {
                            it.getPachliDisplayName(context)
                        }
                        ).toTypedArray()
                    entryValues = (listOf("") + locales.map { it.language }).toTypedArray()
                    key = PrefKeys.DEFAULT_POST_LANGUAGE
                    icon = makeIcon(GoogleMaterial.Icon.gmd_translate)
                    value = accountManager.activeAccount?.defaultPostLanguage.orEmpty()
                    isPersistent = false // This will be entirely server-driven
                    setSummaryProvider { entry }

                    setOnPreferenceChangeListener { _, newValue ->
                        syncWithServer(language = (newValue as String))
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_default_media_sensitivity)
                    setIcon(R.drawable.ic_eye_24dp)
                    key = PrefKeys.DEFAULT_MEDIA_SENSITIVITY
                    isSingleLineTitle = false
                    val sensitivity = accountManager.activeAccount?.defaultMediaSensitivity ?: false
                    setDefaultValue(sensitivity)
                    setIcon(getIconForSensitivity(sensitivity))
                    setOnPreferenceChangeListener { _, newValue ->
                        setIcon(getIconForSensitivity(newValue as Boolean))
                        syncWithServer(sensitive = newValue)
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_timelines) {
                // TODO having no activeAccount in this fragment does not really make sense, enforce it?
                //   All other locations here make it optional, however.

                switchPreference {
                    key = PrefKeys.MEDIA_PREVIEW_ENABLED
                    setTitle(R.string.pref_title_show_media_preview)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceDataStore
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA
                    setTitle(R.string.pref_title_alway_show_sensitive_media)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceDataStore
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_OPEN_SPOILER
                    setTitle(R.string.pref_title_alway_open_spoiler)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceDataStore
                }
            }
        }
    }

    private fun makeIcon(icon: GoogleMaterial.Icon): IconicsDrawable {
        return makeIcon(requireContext(), icon, iconSize)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.action_view_account_preferences)
    }

    private fun openNotificationSystemPrefs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent()
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("android.provider.extra.APP_PACKAGE", BuildConfig.APPLICATION_ID)
            startActivity(intent)
        } else {
            activity?.let {
                val intent =
                    PreferencesActivityIntent(it, PreferenceScreen.NOTIFICATION)
                it.startActivity(intent)
                it.overridePendingTransition(DR.anim.slide_from_right, DR.anim.slide_to_left)
            }
        }
    }

    private fun syncWithServer(visibility: String? = null, sensitive: Boolean? = null, language: String? = null) {
        // TODO these could also be "datastore backed" preferences (a ServerPreferenceDataStore); follow-up of issue #3204

        mastodonApi.accountUpdateSource(visibility, sensitive, language)
            .enqueue(
                object : Callback<Account> {
                    override fun onResponse(call: Call<Account>, response: Response<Account>) {
                        val account = response.body()
                        if (response.isSuccessful && account != null) {
                            accountManager.activeAccount?.let {
                                it.defaultPostPrivacy = account.source?.privacy
                                    ?: Status.Visibility.PUBLIC
                                it.defaultMediaSensitivity = account.source?.sensitive ?: false
                                it.defaultPostLanguage = language.orEmpty()
                                accountManager.saveAccount(it)
                            }
                        } else {
                            Timber.e("failed updating settings on server")
                            showErrorSnackbar(visibility, sensitive)
                        }
                    }

                    override fun onFailure(call: Call<Account>, t: Throwable) {
                        Timber.e(t, "failed updating settings on server")
                        showErrorSnackbar(visibility, sensitive)
                    }
                },
            )
    }

    private fun showErrorSnackbar(visibility: String?, sensitive: Boolean?) {
        view?.let { view ->
            Snackbar.make(view, R.string.pref_failed_to_sync, Snackbar.LENGTH_LONG)
                .setAction(app.pachli.core.ui.R.string.action_retry) { syncWithServer(visibility, sensitive) }
                .show()
        }
    }

    @DrawableRes
    private fun getIconForSensitivity(sensitive: Boolean): Int {
        return if (sensitive) {
            R.drawable.ic_hide_media_24dp
        } else {
            R.drawable.ic_eye_24dp
        }
    }

    private fun launchFilterActivity() {
        val intent = FiltersActivityIntent(requireContext())
        activity?.startActivity(intent)
        activity?.overridePendingTransition(DR.anim.slide_from_right, DR.anim.slide_to_left)
    }

    companion object {
        fun newInstance() = AccountPreferencesFragment()
    }
}
