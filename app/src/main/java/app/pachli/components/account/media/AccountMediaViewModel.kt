/* Copyright 2022 Tusky Contributors
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

package app.pachli.components.account.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.network.retrofit.MastodonApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

@HiltViewModel
class AccountMediaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountManager: AccountManager,
    private val api: MastodonApi,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel() {
    val attachmentData: MutableList<AttachmentViewData> = mutableListOf()

    var currentSource: AccountMediaPagingSource? = null

    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    @OptIn(ExperimentalPagingApi::class)
    fun getMedia(pachliAccountId: Long, accountId: String): Flow<PagingData<AttachmentViewData>> {
        return accountManager.getPachliAccountFlow(pachliAccountId).filterNotNull().flatMapLatest { activeAccount ->
            Pager(
                config = PagingConfig(
                    pageSize = LOAD_AT_ONCE,
                    prefetchDistance = LOAD_AT_ONCE * 2,
                ),
                pagingSourceFactory = {
                    AccountMediaPagingSource(
                        viewModel = this,
                    ).also { source ->
                        currentSource = source
                    }
                },
                remoteMediator = AccountMediaRemoteMediator(
                    context,
                    api,
                    activeAccount.entity.alwaysShowSensitiveMedia,
                    accountId,
                    this,
                ),
            ).flow
        }.cachedIn(viewModelScope)
    }

    fun revealAttachment(viewData: AttachmentViewData) {
        val position = attachmentData.indexOfFirst { oldViewData -> oldViewData.id == viewData.id }
        attachmentData[position] = viewData.copy(isRevealed = true)
        currentSource?.invalidate()
    }

    companion object {
        private const val LOAD_AT_ONCE = 30
    }
}
