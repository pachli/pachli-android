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
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.pachli.R
import app.pachli.appstore.EventHub
import app.pachli.core.activity.BaseActivity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.MainActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent
import app.pachli.core.navigation.PreferencesActivityIntent.PreferenceScreen
import app.pachli.core.preferences.AppTheme
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.PrefKeys.APP_THEME
import app.pachli.databinding.ActivityPreferencesBinding
import app.pachli.util.setAppNightMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Show specific preferences.
 */
@AndroidEntryPoint
class PreferencesActivity :
    BaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Inject
    lateinit var eventHub: EventHub

    private val restartActivitiesOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            /* Switching themes won't actually change the theme of activities on the back stack.
             * Either the back stack activities need to all be recreated, or do the easier thing, which
             * is hijack the back button press and use it to launch a new MainActivity and clear the
             * back stack. */
            val intent = MainActivityIntent(this@PreferencesActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivityWithSlideInAnimation(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val preferenceType = PreferencesActivityIntent.getPreferenceType(intent)

        val fragmentTag = "preference_fragment_$preferenceType"

        val fragment: Fragment = supportFragmentManager.findFragmentByTag(fragmentTag)
            ?: when (preferenceType) {
                PreferenceScreen.GENERAL -> PreferencesFragment.newInstance()
                PreferenceScreen.ACCOUNT -> AccountPreferencesFragment.newInstance()
                PreferenceScreen.NOTIFICATION -> NotificationPreferencesFragment.newInstance()
                else -> throw IllegalArgumentException("preferenceType not known")
            }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

        onBackPressedDispatcher.addCallback(this, restartActivitiesOnBackPressedCallback)
        restartActivitiesOnBackPressedCallback.isEnabled = intent.extras?.getBoolean(
            EXTRA_RESTART_ON_BACK,
        ) ?: savedInstanceState?.getBoolean(EXTRA_RESTART_ON_BACK, false) ?: false

        lifecycleScope.launch {
            sharedPreferencesRepository.changes.filterNotNull().collect { key ->
                when (key) {
                    APP_THEME -> {
                        val theme = AppTheme.from(sharedPreferencesRepository)
                        Timber.d("activeTheme: %s", theme)
                        setAppNightMode(theme)

                        restartActivitiesOnBackPressedCallback.isEnabled = true
                        this@PreferencesActivity.restartCurrentActivity()
                    }
                    PrefKeys.FONT_FAMILY, PrefKeys.UI_TEXT_SCALE_RATIO -> {
                        restartActivitiesOnBackPressedCallback.isEnabled = true
                        this@PreferencesActivity.restartCurrentActivity()
                    }
                    PrefKeys.STATUS_TEXT_SIZE, PrefKeys.ABSOLUTE_TIME_VIEW, PrefKeys.SHOW_BOT_OVERLAY, PrefKeys.ANIMATE_GIF_AVATARS, PrefKeys.USE_BLURHASH,
                    PrefKeys.SHOW_SELF_USERNAME, PrefKeys.SHOW_CARDS_IN_TIMELINES, PrefKeys.CONFIRM_REBLOGS, PrefKeys.CONFIRM_FAVOURITES,
                    PrefKeys.ENABLE_SWIPE_FOR_TABS, PrefKeys.MAIN_NAV_POSITION, PrefKeys.HIDE_TOP_TOOLBAR, PrefKeys.SHOW_STATS_INLINE,
                    -> {
                        restartActivitiesOnBackPressedCallback.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!,
        )
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.commit {
            setCustomAnimations(
                DR.anim.slide_from_right,
                DR.anim.slide_to_left,
                DR.anim.slide_from_left,
                DR.anim.slide_to_right,
            )
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
        return true
    }

    private fun saveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_RESTART_ON_BACK, restartActivitiesOnBackPressedCallback.isEnabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_RESTART_ON_BACK, restartActivitiesOnBackPressedCallback.isEnabled)
        super.onSaveInstanceState(outState)
    }

    private fun restartCurrentActivity() {
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        val savedInstanceState = Bundle()
        saveInstanceState(savedInstanceState)
        intent.putExtras(savedInstanceState)
        startActivityWithSlideInAnimation(intent)
        finish()
        overridePendingTransition(DR.anim.fade_in, DR.anim.fade_out)
    }

    companion object {
        private const val EXTRA_RESTART_ON_BACK = "restart"
    }
}
