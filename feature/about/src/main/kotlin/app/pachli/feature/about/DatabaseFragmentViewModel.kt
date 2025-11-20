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

package app.pachli.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.support.getSupportWrapper
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.DebugDao
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.TimelineDao
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Record the number of rows in tables of interest. */
data class TableRowCounts(
    val conversationEntity: Result<Int, Throwable> = Ok(0),
    val draftEntity: Result<Int, Throwable> = Ok(0),
    val emojisEntity: Result<Int, Throwable> = Ok(0),
    val logEntryEntity: Result<Int, Throwable> = Ok(0),
    val notificationEntity: Result<Int, Throwable> = Ok(0),
    val serverEntity: Result<Int, Throwable> = Ok(0),
    val statusEntity: Result<Int, Throwable> = Ok(0),
    val statusViewDataEntity: Result<Int, Throwable> = Ok(0),
    val timelineAccountEntity: Result<Int, Throwable> = Ok(0),
    val timelineStatusEntity: Result<Int, Throwable> = Ok(0),
    val timelineStatusWithAccount: Result<Int, Throwable> = Ok(0),
    val translatedStatusEntity: Result<Int, Throwable> = Ok(0),
)

/**
 * Record how long different queries take to run, and any errors that occurred.
 *
 * In results with a `Pair<Duration, Int>` the second item is the number of rows
 * returned.
 *
 * @property getStatusRowNumber
 * @property getStatusesWithQuote
 * @property getNotificationsWithQuote
 * @property getConversationsWithQuote
 */
data class QueryDurations(
    val getStatusRowNumber: Result<Duration, Throwable>? = null,
    val getStatusesWithQuote: Result<Pair<Duration, Int>, Throwable>? = null,
    val getNotificationsWithQuote: Result<Pair<Duration, Int>, Throwable>? = null,
    val getConversationsWithQuote: Result<Pair<Duration, Int>, Throwable>? = null,
)

/**
 * @property pachliAccountId
 * @property tableRowCounts
 * @property integrityCheck Result of calling [DatabaseFragmentViewModel.getIntegrityCheck].
 * @property queryDurations Result of calling [DatabaseFragmentViewModel.getQueryDurations].
 * @property vacuumResult Result of calling [DatabaseFragmentViewModel.vacuum]. `Ok(null)`
 * means the call hasn't happened yet.
 * @property clearCacheResult Result of calling [DatabaseFragmentViewModel.clearContentCache].
 * `Ok(null)` means the call hasn't happened yet.
 */
data class DatabaseUiState(
    val pachliAccountId: Long,
    val tableRowCounts: TableRowCounts,
    val integrityCheck: Result<String, Throwable> = Ok("Check not run yet"),
    val queryDurations: QueryDurations? = null,
    val pruneCacheResult: Result<Unit?, Throwable> = Ok(null),
    val vacuumResult: Result<Unit?, Throwable> = Ok(null),
    val clearCacheResult: Result<Unit?, Throwable> = Ok(null),
)

@HiltViewModel
class DatabaseFragmentViewModel @Inject constructor(
    private val db: AppDatabase,
    private val debugDao: DebugDao,
    private val timelineDao: TimelineDao,
    private val notificationDao: NotificationDao,
    private val conversationsDao: ConversationsDao,
    private val accountManager: AccountManager,
) : ViewModel() {
    private val reload = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private val _uiState: MutableStateFlow<DatabaseUiState?> = MutableStateFlow(null)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(reload, accountManager.accountsOrderedByActiveFlow) { _, accounts -> accounts.first() }.collect { account ->
                _uiState.update {
                    DatabaseUiState(
                        pachliAccountId = account.id,
                        tableRowCounts = getTableRowCounts(),
                        integrityCheck = Ok("Check not run yet"),
                        queryDurations = null,
                    )
                }
            }
        }
    }

    /** @return Individual table row counts. */
    private suspend fun getTableRowCounts(): TableRowCounts {
        return TableRowCounts(
            conversationEntity = getTableCount("ConversationEntity"),
            draftEntity = getTableCount("DraftEntity"),
            emojisEntity = getTableCount("EmojisEntity"),
            logEntryEntity = getTableCount("LogEntryEntity"),
            notificationEntity = getTableCount("NotificationEntity"),
            serverEntity = getTableCount("ServerEntity"),
            statusEntity = getTableCount("StatusEntity"),
            statusViewDataEntity = getTableCount("StatusViewDataEntity"),
            timelineAccountEntity = getTableCount("TimelineAccountEntity"),
            timelineStatusEntity = getTableCount("TimelineStatusEntity"),
            timelineStatusWithAccount = getTableCount("TimelineStatusWithAccount"),
            translatedStatusEntity = getTableCount("TranslatedStatusEntity"),
        )
    }

    /** @return The number of rows in [tableName], or any error that occurred. */
    private suspend fun getTableCount(tableName: String) = withContext(Dispatchers.IO) {
        return@withContext runSuspendCatching {
            db.getSupportWrapper().query("SELECT COUNT(*) FROM $tableName")
                .use {
                    it.moveToFirst()
                    it.getInt(0)
                }
        }
    }

    /**
     * Runs an integrity check on the database and updates [_uiState] with the
     * result.
     */
    fun getIntegrityCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it?.copy(integrityCheck = Ok("Checking...")) }

            val integrityCheck = runSuspendCatching {
                db.getSupportWrapper().query("PRAGMA integrity_check").use {
                    buildString {
                        while (it.moveToNext()) {
                            append(it.getString(0))
                        }
                    }
                }
            }
            delay(500.milliseconds)
            _uiState.update { it?.copy(integrityCheck = integrityCheck) }
        }
    }

    /**
     * Runs different queries and updates [_uiState] with the result.
     */
    fun getQueryDurations(pachliAccountId: Long) {
        viewModelScope.launch {
            _uiState.update { it?.copy(queryDurations = null) }

            val statusIds = timelineDao.getMostRecentNStatusIds(pachliAccountId, 10)
            val id = statusIds.get(min(5, statusIds.size))

            val getStatusRowNumber = runSuspendCatching {
                val start = Instant.now()
                timelineDao.getStatusRowNumber(pachliAccountId, id)
                Duration.between(start, Instant.now())
            }

            // Do these updates individually, so if one of them hangs the UI should still
            // update with the result of the most recent successful query.
            _uiState.update {
                it?.copy(
                    queryDurations = it.queryDurations?.copy(getStatusRowNumber = getStatusRowNumber)
                        ?: QueryDurations(getStatusRowNumber = getStatusRowNumber),
                )
            }

            val getStatusesWithQuote = runSuspendCatching {
                val start = Instant.now()
                val statuses = timelineDao.debugGetStatusesWithQuote(pachliAccountId)
                Pair(Duration.between(start, Instant.now()), statuses.size)
            }

            _uiState.update {
                it?.copy(
                    queryDurations = it.queryDurations?.copy(getStatusesWithQuote = getStatusesWithQuote)
                        ?: QueryDurations(getStatusesWithQuote = getStatusesWithQuote),
                )
            }

            val getNotificationsWithQuote = runSuspendCatching {
                val start = Instant.now()
                val notifications = notificationDao.debugGetNotificationsWithQuote(pachliAccountId)
                Pair(Duration.between(start, Instant.now()), notifications.size)
            }

            _uiState.update {
                it?.copy(
                    queryDurations = it.queryDurations?.copy(getNotificationsWithQuote = getNotificationsWithQuote)
                        ?: QueryDurations(getNotificationsWithQuote = getNotificationsWithQuote),
                )
            }

            val getConversationsWithQuote = runSuspendCatching {
                val start = Instant.now()
                val conversations = conversationsDao.debugGetConversationsWithQuote(pachliAccountId)
                Pair(Duration.between(start, Instant.now()), conversations.size)
            }

            _uiState.update {
                it?.copy(
                    queryDurations = it.queryDurations?.copy(getConversationsWithQuote = getConversationsWithQuote)
                        ?: QueryDurations(getConversationsWithQuote = getConversationsWithQuote),
                )
            }
        }
    }

    /**
     * Prunes the cache of unreferenced statuses, accounts, etc.
     *
     * Unlike the other methods, this operates on all the user's accounts, so they don't
     * have to do this for all of them individually.
     */
    fun pruneCache() {
        viewModelScope.launch {
            _uiState.update { it?.copy(pruneCacheResult = Ok(null)) }
            val result = runSuspendCatching {
                accountManager.accounts.forEach {
                    timelineDao.cleanup(it.id)
                }
            }
            _uiState.update {
                it?.copy(
                    tableRowCounts = getTableRowCounts(),
                    pruneCacheResult = result,
                )
            }
        }
    }

    fun vacuum() {
        viewModelScope.launch {
            _uiState.update { it?.copy(vacuumResult = Ok(null)) }
            val result = runSuspendCatching { db.getSupportWrapper().execSQL("VACUUM") }
            _uiState.update { it?.copy(vacuumResult = result) }
        }
    }

    fun clearContentCache() {
        viewModelScope.launch {
            _uiState.update { it?.copy(clearCacheResult = Ok(null)) }
            val result = runSuspendCatching { debugDao.clearCache() }
            _uiState.update {
                it?.copy(
                    tableRowCounts = getTableRowCounts(),
                    clearCacheResult = result,
                )
            }
        }
    }
}
