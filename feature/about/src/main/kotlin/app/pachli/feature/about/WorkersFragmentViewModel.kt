/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.worker.PruneCacheWorker
import app.pachli.core.worker.PruneCachedMediaWorker
import app.pachli.core.worker.PruneLogEntryEntityWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WorkersFragmentViewModel @Inject constructor(
    application: Application,
    private val logEntryDao: LogEntryDao,
) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)

    val pruneCachedMediaWorkerWorkInfo = workManager
        .getWorkInfosForUniqueWorkFlow(PruneCachedMediaWorker.PERIODIC_WORK_TAG)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    val pruneCacheWorkerWorkInfo = workManager
        .getWorkInfosForUniqueWorkFlow(PruneCacheWorker.PERIODIC_WORK_TAG)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val pruneLogEntryEntityWorkerWorkInfo = workManager
        .getWorkInfosForUniqueWorkFlow(PruneLogEntryEntityWorker.PERIODIC_WORK_TAG)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val pruneCachedMediaWorkerLogEntry = logEntryDao.loadAllByTag("PruneCachedMediaWorker")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val pruneCacheWorkerLogEntry = logEntryDao.loadAllByTag("PruneCacheWorker")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val pruneLogEntryEntityWorkerLogEntry = logEntryDao.loadAllByTag("PruneLogEntryEntityWorker")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}
