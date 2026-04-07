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
import androidx.paging.map
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.DraftRepository
import app.pachli.core.model.Draft
import app.pachli.core.network.retrofit.MastodonApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data to show a draft in [DraftViewHolder].
 *
 * @property draft The [Draft].
 * @property isChecked True if the draft is checked/selected.
 */
data class DraftViewData(
    val draft: Draft,
    val isChecked: Boolean,
)

@HiltViewModel
class DraftsViewModel @Inject constructor(
    val accountManager: AccountManager,
    val api: MastodonApi,
    private val draftRepository: DraftRepository,
) : ViewModel() {

    val drafts = draftRepository.getDrafts(accountManager.activeAccount?.id!!)
        .cachedIn(viewModelScope)

    private val checkedDrafts = MutableStateFlow<Set<Long>>(emptySet())

    val draftViewData = combine(drafts, checkedDrafts) { drafts, checkedDrafts ->
        drafts.map {
            DraftViewData(
                draft = it,
                isChecked = checkedDrafts.contains(it.id),
            )
        }
    }.cachedIn(viewModelScope)

    /** Marks [draft] as checked or unchecked, per [isChecked]. */
    fun checkDraft(draft: Draft, isChecked: Boolean) {
        checkedDrafts.update {
            if (isChecked) {
                it + draft.id
            } else {
                it - draft.id
            }
        }
    }

    /** Toggles the checked state of [draft]. */
    fun toggleDraftChecked(draft: Draft) {
        checkedDrafts.update {
            if (it.contains(draft.id)) it - draft.id else it + draft.id
        }
    }

    /** @return True if [draft] is checked. */
    fun isDraftChecked(draft: Draft) = checkedDrafts.value.contains(draft.id)

    /** @return The number of checked drafts. */
    fun countChecked() = checkedDrafts.value.size

    fun deleteCheckedDrafts(pachliAccountId: Long) {
        viewModelScope.launch {
            checkedDrafts.value.forEach { draftId ->
                draftRepository.deleteDraftAndAttachments(pachliAccountId, draftId)
            }
            checkedDrafts.update { emptySet() }
        }
    }

    suspend fun getStatus(statusId: String) = api.status(statusId)
}
