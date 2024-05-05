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

package app.pachli.core.data.repository

sealed interface UiAction
sealed interface UiSuccess

sealed interface ListAction : UiAction {
    data class CreateList(val title: String, val exclusive: Boolean) : ListAction
    data class EditList(val listId: String, val title: String, val exclusive: Boolean) : ListAction
    data class DeleteList(val listId: String) : ListAction
}

sealed interface ListActionSuccess : UiSuccess {
    val action: ListAction

    data class CreateList(override val action: ListAction.CreateList) : ListActionSuccess
    data class EditList(override val action: ListAction.EditList) : ListActionSuccess
    data class DeleteList(override val action: ListAction.DeleteList) : ListActionSuccess
}

sealed interface UiError {
    val error: Error
    val action: ListAction

    data class CreateList(override val error: Error, override val action: ListAction.CreateList) : UiError
    data class EditList(override val error: Error, override val action: ListAction.EditList) : UiError
    data class DeleteList(override val error: Error, override val action: ListAction.DeleteList) : UiError
}
