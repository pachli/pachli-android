/* Copyright 2020 Tusky Contributors
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.components.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.db.AccountManager
import app.pachli.db.DraftDao
import app.pachli.db.DraftEntity
import app.pachli.entity.Status
import app.pachli.network.MastodonApi
import at.connyduck.calladapter.networkresult.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val draftDao: DraftDao,
    val accountManager: AccountManager,
    val api: MastodonApi,
    private val draftHelper: DraftHelper,
) : ViewModel() {

    val drafts = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { draftDao.draftsPagingSource(accountManager.activeAccount?.id!!) },
    ).flow
        .cachedIn(viewModelScope)

    private val deletedDrafts: MutableList<DraftEntity> = mutableListOf()

    fun deleteDraft(draft: DraftEntity) {
        // this does not immediately delete media files to avoid unnecessary file operations
        // in case the user decides to restore the draft
        viewModelScope.launch {
            draftDao.delete(draft.id)
            deletedDrafts.add(draft)
        }
    }

    fun restoreDraft(draft: DraftEntity) {
        viewModelScope.launch {
            draftDao.insertOrReplace(draft)
            deletedDrafts.remove(draft)
        }
    }

    suspend fun getStatus(statusId: String): NetworkResult<Status> {
        return api.status(statusId)
    }

    override fun onCleared() {
        viewModelScope.launch {
            deletedDrafts.forEach {
                draftHelper.deleteAttachments(it)
            }
        }
    }
}
