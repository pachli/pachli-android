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

package app.pachli.feature.lists

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.MastodonList
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.ui.OperationCounter
import com.github.michaelbull.result.onFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

sealed class Error(
    @StringRes override val resourceId: Int,
    override val formatArgs: Array<out String>,
    override val cause: ListsError? = null,
) : ListsError {
    data class Create(val title: String, override val cause: ListsError.Create) :
        Error(R.string.error_create_list_fmt, arrayOf(title.unicodeWrap()), cause)

    data class Delete(val title: String, override val cause: ListsError.Delete) :
        Error(R.string.error_delete_list_fmt, arrayOf(title.unicodeWrap()), cause)

    data class Update(val title: String, override val cause: ListsError.Update) :
        Error(R.string.error_rename_list_fmt, arrayOf(title.unicodeWrap()), cause)
}

@HiltViewModel(assistedFactory = ListsViewModel.Factory::class)
internal class ListsViewModel @AssistedInject constructor(
    private val listsRepository: ListsRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {
    private val _errors = Channel<Error>()
    val errors = _errors.receiveAsFlow()

    private val operationCounter = OperationCounter()
    val operationCount = operationCounter.count

    // Not a stateflow, as that makes updates distinct. A refresh that returns
    // no changes is not distinct, and that prevents the refresh spinner from
    // disappearing when the user refreshes.
    val lists = listsRepository.getLists(pachliAccountId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    fun refresh() = viewModelScope.launch {
        operationCounter { listsRepository.refresh(pachliAccountId) }
    }

    fun createNewList(title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = viewModelScope.launch {
        operationCounter {
            listsRepository.createList(pachliAccountId, title, exclusive, repliesPolicy)
                .onFailure { _errors.send(Error.Create(title, it)) }
        }
    }

    fun updateList(listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = viewModelScope.launch {
        operationCounter {
            listsRepository.updateList(pachliAccountId, listId, title, exclusive, repliesPolicy)
                .onFailure { _errors.send(Error.Update(title, it)) }
        }
    }

    fun deleteList(list: MastodonList) = viewModelScope.launch {
        operationCounter {
            listsRepository.deleteList(list).onFailure { _errors.send(Error.Delete(list.title, it)) }
        }
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [ListsViewModel] with [pachliAccountId] as the active account.
         */
        fun create(pachliAccountId: Long): ListsViewModel
    }
}
