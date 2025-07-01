/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.data.repository

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Repository for [StatusDisplayOptions], exposed through the [flow] property.
 *
 * A new value is emitted whenever a relevant preference changes or the active
 * account changes.
 */
@Singleton
class StatusDisplayOptionsRepository @Inject constructor(
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val serverRepository: ServerRepository,
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
        PrefKeys.SHOW_CARDS_IN_TIMELINES,
        PrefKeys.USE_BLURHASH,
        PrefKeys.WELLBEING_HIDE_STATS_POSTS,
        PrefKeys.SHOW_STATS_INLINE,
        PrefKeys.LAB_RENDER_MARKDOWN,
    )

    init {
        // Update whenever preferences change
        externalScope.launch {
            sharedPreferencesRepository.changes.filter { prefKeys.contains(it) }.collect { key ->
                Timber.d("Updating because shared preference changed")
                _flow.update { prev ->
                    when (key) {
                        PrefKeys.ANIMATE_GIF_AVATARS -> prev.copy(
                            animateAvatars = sharedPreferencesRepository.animateAvatars,
                        )
                        PrefKeys.MEDIA_PREVIEW_ENABLED -> prev.copy(
                            mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled ?: default.mediaPreviewEnabled,
                        )
                        PrefKeys.ABSOLUTE_TIME_VIEW -> prev.copy(
                            useAbsoluteTime = sharedPreferencesRepository.useAbsoluteTime,
                        )
                        PrefKeys.SHOW_BOT_OVERLAY -> prev.copy(
                            showBotOverlay = sharedPreferencesRepository.showBotOverlay,
                        )
                        PrefKeys.USE_BLURHASH -> prev.copy(
                            useBlurhash = sharedPreferencesRepository.useBlurHash,
                        )
                        PrefKeys.SHOW_CARDS_IN_TIMELINES -> prev.copy(
                            cardViewMode = if (sharedPreferencesRepository.showLinkPreviewCards) CardViewMode.INDENTED else CardViewMode.NONE,
                        )
                        PrefKeys.CONFIRM_FAVOURITES -> prev.copy(
                            confirmFavourites = sharedPreferencesRepository.confirmFavourites,
                        )
                        PrefKeys.CONFIRM_REBLOGS -> prev.copy(
                            confirmReblogs = sharedPreferencesRepository.confirmReblogs,
                        )
                        PrefKeys.WELLBEING_HIDE_STATS_POSTS -> prev.copy(
                            hideStatsInDetailedView = sharedPreferencesRepository.hideStatsInDetailedView,
                        )
                        PrefKeys.ANIMATE_CUSTOM_EMOJIS -> prev.copy(
                            animateEmojis = sharedPreferencesRepository.animateEmojis,
                        )
                        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> prev.copy(
                            showSensitiveMedia = accountManager.activeAccount?.alwaysShowSensitiveMedia ?: default.showSensitiveMedia,
                        )
                        PrefKeys.ALWAYS_OPEN_SPOILER -> prev.copy(
                            openSpoiler = accountManager.activeAccount?.alwaysOpenSpoiler ?: default.openSpoiler,
                        )
                        PrefKeys.SHOW_STATS_INLINE -> prev.copy(
                            showStatsInline = sharedPreferencesRepository.showInlineStats,
                        )
                        PrefKeys.LAB_RENDER_MARKDOWN -> prev.copy(
                            renderMarkdown = sharedPreferencesRepository.renderMarkdown,
                        )
                        else -> {
                            prev
                        }
                    }
                }
            }
        }

        externalScope.launch {
            accountManager.activeAccountFlow.collect {
                if (it is Loadable.Loaded) {
                    Timber.d("Updating because active account changed")
                    _flow.emit(initialStatusDisplayOptions(it.data))
                }
            }
        }

        externalScope.launch {
            accountPreferenceDataStore.changes.collect { (key, value) ->
                Timber.d("Updating because account preference changed")
                _flow.update { prev ->
                    when (key) {
                        PrefKeys.MEDIA_PREVIEW_ENABLED -> prev.copy(mediaPreviewEnabled = value)
                        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> prev.copy(showSensitiveMedia = value)
                        PrefKeys.ALWAYS_OPEN_SPOILER -> prev.copy(openSpoiler = value)
                        else -> {
                            prev
                        }
                    }
                }
            }
        }

        externalScope.launch {
            serverRepository.flow.collect { result ->
                Timber.d("Updating because server capabilities changed")
                result.onSuccess { server ->
                    _flow.update {
                        it.copy(
                            canTranslate = server.can(ORG_JOINMASTODON_STATUSES_TRANSLATE, ">=1.0".toConstraint()),
                        )
                    }
                }
                result.onFailure { _flow.update { it.copy(canTranslate = false) } }
            }
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    fun initialStatusDisplayOptions(account: AccountEntity? = null): StatusDisplayOptions {
        return StatusDisplayOptions(
            animateAvatars = sharedPreferencesRepository.animateAvatars,
            animateEmojis = sharedPreferencesRepository.animateEmojis,
            mediaPreviewEnabled = account?.mediaPreviewEnabled ?: default.mediaPreviewEnabled,
            useAbsoluteTime = sharedPreferencesRepository.useAbsoluteTime,
            showBotOverlay = sharedPreferencesRepository.showBotOverlay,
            useBlurhash = sharedPreferencesRepository.useBlurHash,
            cardViewMode = if (sharedPreferencesRepository.showLinkPreviewCards) {
                CardViewMode.INDENTED
            } else {
                default.cardViewMode
            },
            confirmReblogs = sharedPreferencesRepository.confirmReblogs,
            confirmFavourites = sharedPreferencesRepository.confirmFavourites,
            hideStatsInDetailedView = sharedPreferencesRepository.hideStatsInDetailedView,
            showStatsInline = sharedPreferencesRepository.showInlineStats,
            showSensitiveMedia = account?.alwaysShowSensitiveMedia ?: default.showSensitiveMedia,
            openSpoiler = account?.alwaysOpenSpoiler ?: default.openSpoiler,
            canTranslate = default.canTranslate,
            renderMarkdown = sharedPreferencesRepository.renderMarkdown,
        )
    }
}
