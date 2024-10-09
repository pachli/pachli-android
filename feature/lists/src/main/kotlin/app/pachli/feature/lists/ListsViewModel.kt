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
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.ui.OperationCounter
import com.github.michaelbull.result.onFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

@HiltViewModel
internal class ListsViewModel @Inject constructor(
    private val listsRepository: ListsRepository,
) : ViewModel() {
    private val _errors = Channel<Error>()
    val errors = _errors.receiveAsFlow()

    private val operationCounter = OperationCounter()
    val operationCount = operationCounter.count

    val lists = listsRepository.lists

    init {
        viewModelScope.launch {
            operationCounter { listsRepository.refresh() }
        }
    }

    fun refresh() = viewModelScope.launch {
        operationCounter { listsRepository.refresh() }
    }

    fun createNewList(title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = viewModelScope.launch {
        operationCounter {
            listsRepository.createList(title, exclusive, repliesPolicy).onFailure {
                _errors.send(Error.Create(title, it))
            }
        }
    }

    fun updateList(listId: String, title: String, exclusive: Boolean, repliesPolicy: UserListRepliesPolicy) = viewModelScope.launch {
        operationCounter {
            listsRepository.editList(listId, title, exclusive, repliesPolicy).onFailure {
                _errors.send(Error.Update(title, it))
            }
        }
    }

    fun deleteList(listId: String, title: String) = viewModelScope.launch {
        operationCounter {
            listsRepository.deleteList(listId).onFailure {
                _errors.send(Error.Delete(title, it))
            }
        }
    }
}
