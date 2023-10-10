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

package app.pachli.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.di.ApplicationScope
import app.pachli.settings.AccountPreferenceDataStore
import app.pachli.settings.PrefKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for [StatusDisplayOptions], exposed through the [flow] property.
 *
 * A new value is emitted whenever a relevant preference changes or the active
 * account changes.
 */
@Singleton
class StatusDisplayOptionsRepository @Inject constructor(
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val accountManager: AccountManager,
    private val accountPreferenceDataStore: AccountPreferenceDataStore,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    /** Default status display options */
    private val default = StatusDisplayOptions()

    private val _flow = MutableStateFlow(initialStatusDisplayOptions())

    /** Flow of [StatusDisplayOptions] over time */
    val flow = _flow.asStateFlow()

    /** Preference keys that, if changed, affect StatusDisplayOptions */
    private val prefKeys = setOf(
        PrefKeys.ABSOLUTE_TIME_VIEW,
        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA,
        PrefKeys.ALWAYS_OPEN_SPOILER,
        PrefKeys.ANIMATE_CUSTOM_EMOJIS,
        PrefKeys.ANIMATE_GIF_AVATARS,
        PrefKeys.CONFIRM_FAVOURITES,
        PrefKeys.CONFIRM_REBLOGS,
        PrefKeys.MEDIA_PREVIEW_ENABLED,
        PrefKeys.SHOW_BOT_OVERLAY,
        PrefKeys.USE_BLURHASH,
        PrefKeys.WELLBEING_HIDE_STATS_POSTS,
        PrefKeys.SHOW_STATS_INLINE,
    )

    init {
        Log.d(TAG, "Created StatusDisplayOptionsRepository")

        // Update whenever preferences change
        externalScope.launch {
            sharedPreferencesRepository.changes.filter { prefKeys.contains(it) }.collect { key ->
                Log.d(TAG, "Updating because shared preference changed")
                println("Updating because shared preference changed: $key")
                _flow.update { prev ->
                    when (key) {
                        PrefKeys.ANIMATE_GIF_AVATARS -> prev.copy(
                            animateAvatars = sharedPreferencesRepository.getBoolean(key, default.animateAvatars),
                        )
                        PrefKeys.MEDIA_PREVIEW_ENABLED -> prev.copy(
                            mediaPreviewEnabled = accountManager.activeAccountFlow.value?.mediaPreviewEnabled ?: default.mediaPreviewEnabled,
                        )
                        PrefKeys.ABSOLUTE_TIME_VIEW -> prev.copy(
                            useAbsoluteTime = sharedPreferencesRepository.getBoolean(key, default.useAbsoluteTime),
                        )
                        PrefKeys.SHOW_BOT_OVERLAY -> prev.copy(
                            showBotOverlay = sharedPreferencesRepository.getBoolean(key, default.showBotOverlay),
                        )
                        PrefKeys.USE_BLURHASH -> prev.copy(
                            useBlurhash = sharedPreferencesRepository.getBoolean(key, default.useBlurhash),
                        )
                        PrefKeys.SHOW_CARDS_IN_TIMELINES -> prev.copy(
                            cardViewMode = if (sharedPreferencesRepository.getBoolean(key, false)) CardViewMode.INDENTED else CardViewMode.NONE,
                        )
                        PrefKeys.CONFIRM_FAVOURITES -> prev.copy(
                            confirmFavourites = sharedPreferencesRepository.getBoolean(key, default.confirmFavourites),
                        )
                        PrefKeys.CONFIRM_REBLOGS -> prev.copy(
                            confirmReblogs = sharedPreferencesRepository.getBoolean(key, default.confirmReblogs),
                        )
                        PrefKeys.WELLBEING_HIDE_STATS_POSTS -> prev.copy(
                            hideStats = sharedPreferencesRepository.getBoolean(key, default.hideStats),
                        )
                        PrefKeys.ANIMATE_CUSTOM_EMOJIS -> prev.copy(
                            animateEmojis = sharedPreferencesRepository.getBoolean(key, default.animateEmojis),
                        )
                        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> prev.copy(
                            showSensitiveMedia = accountManager.activeAccountFlow.value?.alwaysShowSensitiveMedia ?: default.showSensitiveMedia,
                        )
                        PrefKeys.ALWAYS_OPEN_SPOILER -> prev.copy(
                            openSpoiler = accountManager.activeAccountFlow.value?.alwaysOpenSpoiler ?: default.openSpoiler,
                        )
                        PrefKeys.SHOW_STATS_INLINE -> prev.copy(
                            showStatsInline = sharedPreferencesRepository.getBoolean(key, default.showStatsInline),
                        )
                        else -> { prev }
                    }
                }
            }
        }

        externalScope.launch {
            accountManager.activeAccountFlow.collect {
                Log.d(TAG, "Updating because active account changed")
                _flow.emit(initialStatusDisplayOptions(it))
            }
        }

        externalScope.launch {
            accountPreferenceDataStore.changes.collect { (key, value) ->
                Log.d(TAG, "Updating because account preference changed")
                _flow.update { prev ->
                    when (key) {
                        PrefKeys.MEDIA_PREVIEW_ENABLED -> prev.copy(mediaPreviewEnabled = value)
                        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> prev.copy(showSensitiveMedia = value)
                        PrefKeys.ALWAYS_OPEN_SPOILER -> prev.copy(openSpoiler = value)
                        else -> { prev }
                    }
                }
            }
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    fun initialStatusDisplayOptions(account: AccountEntity? = null): StatusDisplayOptions {
        return StatusDisplayOptions(
            animateAvatars = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, default.animateAvatars),
            animateEmojis = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, default.animateEmojis),
            mediaPreviewEnabled = account?.mediaPreviewEnabled ?: default.mediaPreviewEnabled,
            useAbsoluteTime = sharedPreferencesRepository.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, default.useAbsoluteTime),
            showBotOverlay = sharedPreferencesRepository.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, default.showBotOverlay),
            useBlurhash = sharedPreferencesRepository.getBoolean(PrefKeys.USE_BLURHASH, default.useBlurhash),
            cardViewMode = if (sharedPreferencesRepository.getBoolean(PrefKeys.SHOW_CARDS_IN_TIMELINES, false)) {
                CardViewMode.INDENTED
            } else {
                default.cardViewMode
            },
            confirmReblogs = sharedPreferencesRepository.getBoolean(PrefKeys.CONFIRM_REBLOGS, default.confirmReblogs),
            confirmFavourites = sharedPreferencesRepository.getBoolean(PrefKeys.CONFIRM_FAVOURITES, default.confirmFavourites),
            hideStats = sharedPreferencesRepository.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, default.hideStats),
            showStatsInline = sharedPreferencesRepository.getBoolean(PrefKeys.SHOW_STATS_INLINE, default.showStatsInline),
            showSensitiveMedia = account?.alwaysShowSensitiveMedia ?: default.showSensitiveMedia,
            openSpoiler = account?.alwaysOpenSpoiler ?: default.openSpoiler,
        )
    }

    companion object {
        private const val TAG = "StatusDisplayOptionsRepository"
    }
}
