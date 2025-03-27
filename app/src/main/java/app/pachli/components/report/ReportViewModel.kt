/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import app.pachli.components.report.adapter.StatusesPagingSource
import app.pachli.components.report.model.StatusViewState
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.eventhub.BlockEvent
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.MuteEvent
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.model.Status
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Resource
import app.pachli.util.Success
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ReportViewModel.Factory::class)
class ReportViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    @Assisted("reportedAccountId") private val reportedAccountId: String,
    @Assisted("reportedAccountUsername") val reportedAccountUsername: String,
    @Assisted("reportedStatusId") private val reportedStatusId: String?,
    private val mastodonApi: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val eventHub: EventHub,
) : ViewModel() {

    private val navigationMutable = MutableLiveData<Screen?>()
    val navigation: LiveData<Screen?> = navigationMutable

    private val muteStateMutable = MutableLiveData<Resource<Boolean>>()
    val muteState: LiveData<Resource<Boolean>> = muteStateMutable

    private val blockStateMutable = MutableLiveData<Resource<Boolean>>()
    val blockState: LiveData<Resource<Boolean>> = blockStateMutable

    private val reportingStateMutable = MutableLiveData<Resource<Boolean>>()
    var reportingState: LiveData<Resource<Boolean>> = reportingStateMutable

    private val checkUrlMutable = MutableLiveData<String?>()
    val checkUrl: LiveData<String?> = checkUrlMutable

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    val statusesFlow = Pager(
        initialKey = this.reportedStatusId,
        config = PagingConfig(pageSize = 20, initialLoadSize = 20),
        pagingSourceFactory = { StatusesPagingSource(reportedAccountId, mastodonApi) },
    ).flow
        .map { pagingData ->
                /* TODO: refactor reports to use the isShowingContent / isExpanded / isCollapsed attributes from StatusViewData
                 instead of StatusViewState */
            pagingData.map { status -> StatusViewData.from(pachliAccountId, status, false, false, false) }
        }
        .cachedIn(viewModelScope)

    private val selectedIds = HashSet<String>().apply {
        reportedStatusId?.let { add(it) }
    }
    val statusViewState = StatusViewState()

    var reportNote: String = ""
    var isRemoteNotify = false

    var isRemoteAccount: Boolean = reportedAccountUsername.contains('@')
    var remoteServer: String? = if (isRemoteAccount) {
        reportedAccountUsername.substring(reportedAccountUsername.indexOf('@') + 1)
    } else {
        null
    }

    init {
        obtainRelationship()
    }

    fun navigateTo(screen: Screen) {
        navigationMutable.value = screen
    }

    fun navigated() {
        navigationMutable.value = null
    }

    private fun obtainRelationship() {
        val ids = listOf(this.reportedAccountId)
        muteStateMutable.value = Loading()
        blockStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.relationships(ids)
                .onSuccess { updateRelationship(it.body.firstOrNull()) }
                .onFailure { updateRelationship(null) }
        }
    }

    private fun updateRelationship(relationship: Relationship?) {
        if (relationship != null) {
            muteStateMutable.value = Success(relationship.muting)
            blockStateMutable.value = Success(relationship.blocking)
        } else {
            muteStateMutable.value = Error(false)
            blockStateMutable.value = Error(false)
        }
    }

    fun toggleMute() {
        val alreadyMuted = muteStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyMuted) {
                mastodonApi.unmuteAccount(this@ReportViewModel.reportedAccountId)
            } else {
                mastodonApi.muteAccount(this@ReportViewModel.reportedAccountId)
            }
                .onSuccess {
                    val relationship = it.body
                    val muting = relationship.muting
                    muteStateMutable.value = Success(muting)
                    if (muting) {
                        eventHub.dispatch(MuteEvent(this@ReportViewModel.reportedAccountId))
                    }
                }
                .onFailure { muteStateMutable.value = Error(false, it.throwable.message) }
        }

        muteStateMutable.value = Loading()
    }

    fun toggleBlock() {
        val alreadyBlocked = blockStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyBlocked) {
                mastodonApi.unblockAccount(this@ReportViewModel.reportedAccountId)
            } else {
                mastodonApi.blockAccount(this@ReportViewModel.reportedAccountId)
            }
                .onSuccess {
                    val relationship = it.body
                    val blocking = relationship.blocking
                    blockStateMutable.value = Success(blocking)
                    if (blocking) {
                        eventHub.dispatch(BlockEvent(this@ReportViewModel.reportedAccountId))
                    }
                }
                .onFailure {
                    blockStateMutable.value = Error(false, it.throwable.message)
                }
        }
        blockStateMutable.value = Loading()
    }

    fun doReport() {
        reportingStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.report(this@ReportViewModel.reportedAccountId, selectedIds.toList(), reportNote, if (isRemoteAccount) isRemoteNotify else null)
                .onSuccess { reportingStateMutable.value = Success(true) }
                .onFailure { error -> reportingStateMutable.value = Error(cause = error.throwable) }
        }
    }

    fun checkClickedUrl(url: String?) {
        checkUrlMutable.value = url
    }

    fun urlChecked() {
        checkUrlMutable.value = null
    }

    fun setStatusChecked(status: Status, checked: Boolean) {
        if (checked) {
            selectedIds.add(status.id)
        } else {
            selectedIds.remove(status.id)
        }
    }

    fun isStatusChecked(id: String): Boolean {
        return selectedIds.contains(id)
    }

    @AssistedFactory
    interface Factory {
        /** Creates [ReportViewModel] with [pachliAccountId] as the active account. */
        fun create(
            pachliAccountId: Long,
            @Assisted("reportedAccountId") reportedAccountId: String,
            @Assisted("reportedAccountUsername") reportedAccountUsername: String,
            @Assisted("reportedStatusId") reportedStatusId: String?,
        ): ReportViewModel
    }
}
