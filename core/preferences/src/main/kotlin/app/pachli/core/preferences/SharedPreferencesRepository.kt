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
import androidx.core.content.edit
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.designsystem.EmbeddedFontFamily
import app.pachli.core.preferences.PrefKeys.FONT_FAMILY
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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

    /** True to prompt the user to confirm after tapping the favourite button. */
    val confirmFavourites: Boolean
        get() = getBoolean(PrefKeys.CONFIRM_FAVOURITES, false)

    /** True to prompt the user to confirm after tapping the reblog button. */
    val confirmReblogs: Boolean
        get() = getBoolean(PrefKeys.CONFIRM_REBLOGS, true)

    /** True if the status' language should be checked before sending. */
    var confirmStatusLanguage: Boolean
        get() = getBoolean(PrefKeys.CONFIRM_STATUS_LANGUAGE, true)
        set(value) {
            edit { putBoolean(PrefKeys.CONFIRM_STATUS_LANGUAGE, value) }
        }

    /** How statuses should be translated. */
    var translationBackend: TranslationBackend
        get() = if (BuildConfig.FLAVOR_store != "google") {
            TranslationBackend.SERVER_ONLY
        } else {
            getEnum(PrefKeys.TRANSLATION_BACKEND, TranslationBackend.SERVER_FIRST)
        }
        set(value) {
            edit { putEnum(PrefKeys.TRANSLATION_BACKEND, value) }
        }

    /** True if downloading translation models should only be done over Wi-Fi. */
    val translationDownloadRequireWiFi: Boolean
        get() = getBoolean(PrefKeys.TRANSLATION_DOWNLOAD_REQUIRE_WIFI, true)

    /** Location of downloaded files. */
    val downloadLocation: DownloadLocation
        get() = getEnum(PrefKeys.DOWNLOAD_LOCATION, DownloadLocation.DOWNLOADS)

    /** True if swipe-gesture between tabs should be enabled. */
    val enableTabSwipe: Boolean
        get() = getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)

    /** Application font. */
    val fontFamily: EmbeddedFontFamily
        get() = EmbeddedFontFamily.from(getString(FONT_FAMILY, "default"))

    /** True if the FAB should be hidden when scrolling. */
    val hideFabWhenScrolling: Boolean
        get() = getBoolean(PrefKeys.FAB_HIDE, false)

    /** True if stats should be hidden in the detailed status view. */
    val hideStatsInDetailedView: Boolean
        get() = getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false)

    /**
     * If true then when viewing a profile:
     *
     * - Stats (post count, follower count, etc) are hidden
     * - The "follows you" badge is hidden
     */
    val hideStatsInProfile: Boolean
        get() = getBoolean(PrefKeys.WELLBEING_HIDE_STATS_PROFILE, false)

    /** Whether to hide the top toolbar. */
    val hideTopToolbar: Boolean
        get() = getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)

    /** True if an HTTP proxy should be used [httpProxyServer] and [httpProxyPort]. */
    val httpProxyEnabled: Boolean
        get() = getBoolean(PrefKeys.HTTP_PROXY_ENABLED, false)

    /** Address of the HTTP proxy to use, if [httpProxyEnabled]. */
    val httpProxyServer: String?
        get() = getString(PrefKeys.HTTP_PROXY_SERVER, null)

    /** Port of the HTTP proxy to use, if [httpProxyEnabled]. */
    val httpProxyPort: Int
        get() = getString(PrefKeys.HTTP_PROXY_PORT, "-1")?.toIntOrNull() ?: -1

    /**
     * Application language.
     *
     * @see [app.pachli.util.LocaleManager].
     */
    var language: String
        get() = getNonNullString(PrefKeys.LANGUAGE, "default")
        set(value) {
            edit { putString(PrefKeys.LANGUAGE, value) }
        }

    /**
     * Same as [language], but if the user has chosen to use the system language it is
     * expanded to the language code the device is using.
     */
    val languageExpandDefault: String
        get() = when (val l = language) {
            "default" -> Locale.getDefault().language
            "handled_by_system" -> Locale.getDefault().language
            else -> l
        }

    /** Screen location of primary navigation UI (tabs, etc). */
    val mainNavigationPosition: MainNavigationPosition
        get() = getEnum(PrefKeys.MAIN_NAV_POSITION, MainNavigationPosition.TOP)

    /** Default audio playback option. */
    val defaultAudioPlayback: DefaultAudioPlayback
        get() = getEnum(PrefKeys.DEFAULT_AUDIO_PLAYBACK, DefaultAudioPlayback.UNMUTED)

    /** True to parse content as Markdown. */
    val renderMarkdown: Boolean
        get() = getBoolean(PrefKeys.LAB_RENDER_MARKDOWN, false)

    /** True to show the timeline in reverse order (oldest at the top). */
    val reverseTimeline: Boolean
        get() = getBoolean(PrefKeys.LAB_REVERSE_TIMELINE, false)

    /** True if an overlay should be shown on avatars to indicate the account is a bot. */
    val showBotOverlay: Boolean
        get() = getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true)

    /** True if post statistics (favourites count, etc) should be shown in a timeline. */
    val showInlineStats: Boolean
        get() = getBoolean(PrefKeys.SHOW_STATS_INLINE, false)

    /** True if link preview cards should be shown attached to statuses. */
    val showLinkPreviewCards: Boolean
        get() = getBoolean(PrefKeys.SHOW_CARDS_IN_TIMELINES, false)

    /** When to show the account's username in the title. */
    val showSelfUsername: ShowSelfUsername
        get() = getEnum(PrefKeys.SHOW_SELF_USERNAME, ShowSelfUsername.DISAMBIGUATE)

    /**
     * Text size to use for status content.
     *
     * @see [app.pachli.core.activity.BaseActivity.textStyle]
     */
    val statusTextSize: String
        get() = getNonNullString(PrefKeys.STATUS_TEXT_SIZE, "medium")

    /** How to align tabs. */
    val tabAlignment: TabAlignment
        get() = getEnum(PrefKeys.TAB_ALIGNMENT, TabAlignment.START)

    /** How to display tabs. */
    val tabContents: TabContents
        get() = getEnum(PrefKeys.TAB_CONTENTS, TabContents.ICON_ONLY)

    /** If true replies **are** shown in the Home tab.. */
    val tabHomeShowReplies: Boolean
        get() = getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)

    /** If true reblogs **are** shown in the Home tab.. */
    val tabHomeShowReblogs: Boolean
        get() = getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)

    /** If true self-reblogs **are** shown in the Home tab.. */
    val tabHomeShowSelfReblogs: Boolean
        get() = getBoolean(PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS, true)

    /** Behaviour when tapping on a tab. */
    val tabTapBehaviour: TabTapBehaviour
        get() = getEnum(PrefKeys.TAB_TAP_BEHAVIOUR, TabTapBehaviour.JUMP_TO_NEXT_PAGE)

    /** UI text scaling factor, stored as a float. 100 = 100% = no scaling. */
    val uiTextScaleRatio: Float
        get() = getFloat(PrefKeys.UI_TEXT_SCALE_RATIO, 100f)

    var updateNotificationFrequency: UpdateNotificationFrequency
        get() = getEnum(PrefKeys.UPDATE_NOTIFICATION_FREQUENCY, UpdateNotificationFrequency.ALWAYS)
        set(value) {
            edit { putEnum(PrefKeys.UPDATE_NOTIFICATION_FREQUENCY, value) }
        }

    var updateNotificationVersionCode: Int
        get() = getInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, -1)
        set(value) {
            edit { putInt(PrefKeys.UPDATE_NOTIFICATION_VERSIONCODE, value) }
        }

    var updateNotificationLastNotificationMs: Long
        get() = getLong(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS, 0)
        set(value) {
            edit { putLong(PrefKeys.UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS, value) }
        }

    /**
     * If true times are shown as absolute times, if false times are shown relative
     * to the current time (e.g., "5 minutes ago").
     */
    val useAbsoluteTime: Boolean
        get() = getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)

    /**
     * If true then the blurhash is used to hide media marked sensitive or
     * not yet downloaded.
     */
    val useBlurHash: Boolean
        get() = getBoolean(PrefKeys.USE_BLURHASH, true)

    /** True if links should be opened in a Chrome custom tab. */
    val useCustomTab: Boolean
        get() = getBoolean(PrefKeys.CUSTOM_TABS, false)

    /**
     * True if the user's previous choice of UnifiedPush distributor should be
     * used by default.
     *
     * Default is `true`.
     */
    var usePreviousUnifiedPushDistributor: Boolean
        get() = getBoolean(PrefKeys.USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR, true)
        set(value) {
            edit { putBoolean(PrefKeys.USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR, value) }
        }

    // Ensure the listener is retained during minification. If you do not do this the
    // field is removed and eventually garbage collected (because registering it as a
    // change listener does not create a strong reference to it) and then no more
    // changes are emitted. See https://github.com/pachli/pachli-android/issues/225.
    @Keep
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            externalScope.launch { changes.emit(key) }
        }

    fun upgradeSharedPreferences(oldVersion: Int, newVersion: Int) {
        Timber.d("Upgrading shared preferences: %d -> %d", oldVersion, newVersion)
        edit {
            // General usage is:
            //
            // if (oldVersion < ...) {
            //     ... use `editor` to modify the preferences ...
            // }

            if (oldVersion < 2024101701) {
                remove(PrefKeys.Deprecated.WELLBEING_LIMITED_NOTIFICATIONS)
            }

            // Deleted ATKINSON_HYPERLEGIBLE, migrate any font preferences that used
            // that to ATKINSON_HYPERLEGIBLE_NEXT.
            if (oldVersion < 2025033001) {
                val fontPref = getString(FONT_FAMILY, "default")
                if (fontPref == "atkinson_hyperlegible") {
                    putString(FONT_FAMILY, "atkinson_hyperlegible_next")
                }
            }

            putInt(PrefKeys.SCHEMA_VERSION, newVersion)
        }
    }

    init {
        // If the user has migrated from a Google build to a non-Google build the
        // translation backend property may be incorrect. Shouldn't happen in the
        // wild, but can happen when trying different build flavours locally.
        if (BuildConfig.FLAVOR_store != "google" && getEnum(PrefKeys.TRANSLATION_BACKEND, TranslationBackend.SERVER_ONLY) != TranslationBackend.SERVER_ONLY) {
            edit { putEnum(PrefKeys.TRANSLATION_BACKEND, TranslationBackend.SERVER_ONLY) }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
