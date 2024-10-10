/*
 * Copyright 2023 Pachli Association
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

package app.pachli.core.preferences

import android.content.SharedPreferences
import androidx.annotation.Keep
import app.pachli.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * An implementation of [SharedPreferences] that exposes all changes to the
 * preferences through the [changes] flow.
 *
 * @param sharedPreferences instance to delegate to
 * @param externalScope [CoroutineScope] to use when emitting in to [changes]
 */
@Singleton
class SharedPreferencesRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    @ApplicationScope private val externalScope: CoroutineScope,
) : SharedPreferences by sharedPreferences {
    /**
     *  Flow of keys that have been updated/deleted in the preferences.
     *
     *  Null means that preferences were cleared.
     */
    val changes = MutableSharedFlow<String?>()

    /** Application theme. */
    val appTheme: AppTheme
        get() = getEnum(PrefKeys.APP_THEME, AppTheme.AUTO_SYSTEM)

    /** True if avatars should be animated. */
    val animateAvatars: Boolean
        get() = getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)

    /** True if emojis should be animated. */
    val animateEmojis: Boolean
        get() = getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)

    /** Location of downloaded files. */
    val downloadLocation: DownloadLocation
        get() = getEnum(PrefKeys.DOWNLOAD_LOCATION, DownloadLocation.DOWNLOADS)

    /** True if swipe-gesture between tabs should be enabled. */
    val enableTabSwipe: Boolean
        get() = getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)

    /** Whether to hide the top toolbar. */
    val hideTopToolbar: Boolean
        get() = getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)

    /** Screen location of primary navigation UI (tabs, etc). */
    val mainNavigationPosition: MainNavigationPosition
        get() = getEnum(PrefKeys.MAIN_NAV_POSITION, MainNavigationPosition.TOP)

    val tabTapBehaviour: TabTapBehaviour
        get() = getEnum(PrefKeys.TAB_TAP_BEHAVIOUR, TabTapBehaviour.JUMP_TO_NEXT_PAGE)

    // Ensure the listener is retained during minification. If you do not do this the
    // field is removed and eventually garbage collected (because registering it as a
    // change listener does not create a strong reference to it) and then no more
    // changes are emitted. See https://github.com/pachli/pachli-android/issues/225.
    @Keep
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            externalScope.launch { changes.emit(key) }
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
