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
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.components.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.DraftRepository
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.model.Draft
import app.pachli.core.network.retrofit.MastodonApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val draftDao: DraftDao,
    val accountManager: AccountManager,
    val api: MastodonApi,
    private val draftRepository: DraftRepository,
) : ViewModel() {

    val drafts = draftRepository.getDrafts(accountManager.activeAccount?.id!!)
        .cachedIn(viewModelScope)

    private val deletedDrafts: MutableList<Draft> = mutableListOf()

    fun deleteDraft(pachliAccountId: Long, draft: Draft) {
        // this does not immediately delete media files to avoid unnecessary file operations
        // in case the user decides to restore the draft
        viewModelScope.launch {
            draftRepository.deleteDraft(pachliAccountId, draft.id)
            deletedDrafts.add(draft)
        }
    }

    fun restoreDraft(pachliAccountId: Long, draft: Draft) {
        viewModelScope.launch {
            draftRepository.upsert(pachliAccountId, draft)
            deletedDrafts.remove(draft)
        }
    }

    suspend fun getStatus(statusId: String) = api.status(statusId)

    override fun onCleared() {
        viewModelScope.launch {
            deletedDrafts.forEach {
                draftRepository.deleteAttachments(it.attachments)
            }
        }
    }
}
