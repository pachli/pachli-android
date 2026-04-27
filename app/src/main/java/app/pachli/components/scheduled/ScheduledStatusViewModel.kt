/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.scheduled

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.components.scheduled.ScheduledStatusViewData.State
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.StatusScheduledEvent
import app.pachli.core.model.ScheduledStatus
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ComposeKind
import app.pachli.core.network.retrofit.MastodonApi
import com.gaelmarhic.quadrant.QuadrantConstants
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Data to show a scheduled status in [ScheduledStatusViewHolder].
 *
 * @property scheduledStatus The [ScheduledStatus].
 * @property state Item's [State].
 */
internal data class ScheduledStatusViewData(
    val scheduledStatus: ScheduledStatus,
    val state: State,
) {
    enum class State {
        DEFAULT,

        /** User has checked the status. */
        CHECKED,

        /** User is editing the status in another activity. */
        EDITING,
    }
}

@HiltViewModel
internal class ScheduledStatusViewModel @Inject constructor(
    @ApplicationContext context: Context,
    val mastodonApi: MastodonApi,
    val eventHub: EventHub,
) : ViewModel() {
    private val activityManager = ContextCompat.getSystemService(context, ActivityManager::class.java)!!

    private val pagingSourceFactory = ScheduledStatusPagingSourceFactory(mastodonApi)

    /**
     * Emits a new item to indicate the UI has changed (e.g., to edit a
     * scheduled status) and anything that depends on the UI state should
     * regenerate.
     */
    private val reloadUi = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    /**
     * Emites a new item to indicate the remote data has changed (e.g., the
     * user has posted a new scheduled status), and anything that depends on
     * remote data should regenerate.
     */
    private val reloadRemote = eventHub.events.filterIsInstance<StatusScheduledEvent>().map { }
        .onStart { emit(Unit) }

    private val scheduledStatuses = reloadRemote.flatMapLatest {
        Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 60),
            pagingSourceFactory = pagingSourceFactory,
        ).flow
    }.cachedIn(viewModelScope)

    /** Map from [ScheduledStatus.id] to [State]. */
    private val states = MutableStateFlow<Map<String, State>>(emptyMap())

    /** Set of [ScheduledStatus.id] that are being edited by the user. */
    private val activeEdits = reloadUi.map {
        activityManager.appTasks
            .asSequence()
            .filter { it.taskInfo.baseActivity?.className == QuadrantConstants.COMPOSE_ACTIVITY }
            .mapNotNull { ComposeActivityIntent.getComposeOptionsOrNull(it.taskInfo.baseIntent) }
            .filter { it.kind == ComposeKind.EDIT_SCHEDULED }
            .mapNotNull { it.draft.statusId }
            .toSet()
    }

    internal val viewData = combine(
        scheduledStatuses,
        activeEdits,
        states,
    ) {
            scheduledStatuses,
            activeEdits,
            states,
        ->
        scheduledStatuses.map {
            ScheduledStatusViewData(
                scheduledStatus = it,
                state = if (activeEdits.contains(it.id)) State.EDITING else states.getOrElse(it.id, { State.DEFAULT }),
            )
        }
    }.cachedIn(viewModelScope)

    /**
     * Marks [scheduledStatus] as checked or unchecked, per [isChecked].
     *
     * @return The number of checked scheduled statuses.
     */
    fun checkScheduledStatus(scheduledStatus: ScheduledStatus, isChecked: Boolean): Int {
        states.update {
            val statusId = scheduledStatus.id
            if (it[statusId] == State.EDITING) return@update it

            it + Pair(statusId, if (isChecked) State.CHECKED else State.DEFAULT)
        }
        return countChecked()
    }

    /** Toggles the checked state of [scheduledStatus]. */
    fun toggleScheduledStatusChecked(scheduledStatus: ScheduledStatus) {
        states.update {
            val statusId = scheduledStatus.id
            val newState = when (it[statusId]) {
                State.EDITING -> State.EDITING
                State.CHECKED -> State.DEFAULT
                State.DEFAULT, null -> State.CHECKED
            }
            it + Pair(statusId, newState)
        }
    }

    /** @return True if [scheduledStatus] is checked. */
    fun isScheduledStatusChecked(scheduledStatus: ScheduledStatus) = states.value.get(scheduledStatus.id) == State.CHECKED

    /** @return The number of checked scheduled statuses. */
    fun countChecked() = states.value.values.filter { it == State.CHECKED }.size

    /** Unchecks all scheduled statuses. */
    fun clearChecked() {
        states.update {
            it.filterValues { it != State.CHECKED }
        }
    }

    /** Deletes all checked scheduled statuses. */
    fun deleteCheckedScheduledStatuses() {
        viewModelScope.launch {
            states.value.filter { it.value == State.CHECKED }.keys.forEach { scheduledStatusId ->
                mastodonApi.deleteScheduledStatus(scheduledStatusId)
                    .onSuccess { pagingSourceFactory.remove(scheduledStatusId) }
                    .onFailure { Timber.w("Error deleting scheduled status: %s", it) }
            }
        }
    }

    fun deleteScheduledStatus(status: ScheduledStatus) {
        viewModelScope.launch {
            mastodonApi.deleteScheduledStatus(status.id)
                .onSuccess { pagingSourceFactory.remove(status) }
                .onFailure { Timber.w("Error deleting scheduled status: %s", it) }
        }
    }

    fun refresh() {
        viewModelScope.launch { reloadUi.emit(Unit) }
    }
}
