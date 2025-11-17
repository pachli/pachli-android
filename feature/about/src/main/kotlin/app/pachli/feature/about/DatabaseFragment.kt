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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.ui.ClipboardUseCase
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.feature.about.databinding.FragmentDatabaseBinding
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DatabaseFragment : Fragment(R.layout.fragment_database) {
    @Inject
    lateinit var clipboard: ClipboardUseCase

    @Inject
    lateinit var openUrl: OpenUrlUseCase

    private val viewModel: DatabaseFragmentViewModel by viewModels()

    private val binding by viewBinding(FragmentDatabaseBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.applyWindowInsets(bottom = InsetType.PADDING)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.filterNotNull().collect(::bindUiState)
            }
        }
    }

    fun bindUiState(uiState: DatabaseUiState) {
        bindTableSizes(uiState.tableRowCounts)
        bindIntegrityCheck(uiState.integrityCheck)
        bindQueryTimings(uiState.queryDurations)
        bindPruneCache(uiState.pruneCacheResult)
        bindVacuum(uiState.vacuumResult)
        bindClearCache(uiState.clearCacheResult)

        binding.buttonIntegrityCheck.setOnClickListener { v ->
            v.isEnabled = false
            viewModel.getIntegrityCheck()
        }

        binding.buttonQueryTimings.setOnClickListener { v ->
            v.isEnabled = false
            viewModel.getQueryDurations(uiState.pachliAccountId)
        }

        binding.buttonPruneCache.setOnClickListener { v ->
            binding.pruneCacheResult.hide()
            viewModel.pruneCache()
        }

        binding.buttonVacuum.setOnClickListener { v ->
            binding.vacuumResult.hide()
            viewModel.vacuum()
        }

        binding.buttonClearCache.setOnClickListener { v ->
            binding.clearCacheResult.hide()
            viewModel.clearContentCache()
        }
    }

    private fun bindTableSizes(tableRowCounts: TableRowCounts) {
        val text = """
ConversationEntity: ${tableRowCounts.conversationEntity.fmt()}
DraftEntity: ${tableRowCounts.draftEntity.fmt()}
EmojisEntity: ${tableRowCounts.emojisEntity.fmt()}
LogEntryEntity: ${tableRowCounts.logEntryEntity.fmt()}
NotificationEntity: ${tableRowCounts.notificationEntity.fmt()}
ServerEntity: ${tableRowCounts.serverEntity.fmt()}
StatusEntity: ${tableRowCounts.statusEntity.fmt()}
StatusViewDataEntity: ${tableRowCounts.statusViewDataEntity.fmt()}
TimelineAccountEntity: ${tableRowCounts.timelineAccountEntity.fmt()}
TimelineStatusEntity: ${tableRowCounts.timelineStatusEntity.fmt()}
TimelineStatusWithAccount: ${tableRowCounts.timelineStatusWithAccount.fmt()}
TranslatedStatusEntity: ${tableRowCounts.translatedStatusEntity.fmt()}
        """.trimIndent()
        binding.tableSizes.text = text
    }

    private fun bindIntegrityCheck(integrityCheck: Result<String, Throwable>) {
        binding.integrityCheck.text = integrityCheck.getOrElse { it.localizedMessage }
        binding.buttonIntegrityCheck.isEnabled = true
    }

    private fun bindQueryTimings(queryDurations: QueryDurations?) {
        val text = """
getStatusRowNumber: ${queryDurations?.getStatusRowNumber?.fmt() ?: "?"}
getStatusesWithQuote: ${queryDurations?.getStatusesWithQuote?.fmt() ?: "?"}
getNotificationsWithQuote: ${queryDurations?.getNotificationsWithQuote?.fmt() ?: "?"}
getConversationsWithQuote: ${queryDurations?.getConversationsWithQuote?.fmt() ?: "?"}
        """.trimIndent()
        binding.queryDurations.text = text
        binding.buttonQueryTimings.isEnabled = true
    }

    fun Result<Int, Throwable>.fmt(): String {
        return when (this) {
            is Err -> error.localizedMessage
            is Ok -> this.value.toString()
        }
    }

    @JvmName("result_duration_throwable_fmt")
    fun Result<Duration, Throwable>.fmt(): String {
        return when (this) {
            is Err -> error.localizedMessage
            is Ok -> "${this.value.toMillis()} ms"
        }
    }

    @JvmName("result_pair_duration_int_throwable_fmt")
    fun Result<Pair<Duration, Int>, Throwable>.fmt(): String {
        return when (this) {
            is Err -> error.localizedMessage
            is Ok -> "${this.value.first.toMillis()} ms, ${this.value.second} items"
        }
    }

    private fun bindPruneCache(pruneCacheResult: Result<Unit?, Throwable>) {
        binding.pruneCacheResult.visible(pruneCacheResult.get() != null)
        binding.pruneCacheResult.text = when (pruneCacheResult) {
            is Err -> pruneCacheResult.error.localizedMessage
            is Ok -> getString(R.string.database_prune_cache_complete)
        }
    }

    private fun bindVacuum(vacuumResult: Result<Unit?, Throwable>) {
        binding.vacuumResult.visible(vacuumResult.get() != null)
        binding.vacuumResult.text = when (vacuumResult) {
            is Err -> vacuumResult.error.localizedMessage
            is Ok -> getString(R.string.database_vacuum_complete)
        }
    }

    private fun bindClearCache(clearCacheResult: Result<Unit?, Throwable>) {
        binding.clearCacheResult.visible(clearCacheResult.get() != null)
        binding.clearCacheResult.text = when (clearCacheResult) {
            is Err -> clearCacheResult.error.localizedMessage
            is Ok -> getString(R.string.database_clear_cache_complete)
        }
    }

    companion object {
        fun newInstance() = DatabaseFragment()
    }
}
