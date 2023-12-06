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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.components.timeline.util.ifExpected
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.network.retrofit.MastodonApi
import retrofit2.HttpException

@OptIn(ExperimentalPagingApi::class)
class AccountMediaRemoteMediator(
    private val api: MastodonApi,
    private val activeAccount: AccountEntity,
    private val viewModel: AccountMediaViewModel,
) : RemoteMediator<String, AttachmentViewData>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, AttachmentViewData>,
    ): MediatorResult {
        try {
            val statusResponse = when (loadType) {
                LoadType.REFRESH -> {
                    api.accountStatuses(viewModel.accountId, onlyMedia = true)
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = state.lastItemOrNull()?.statusId
                    if (maxId != null) {
                        api.accountStatuses(viewModel.accountId, maxId = maxId, onlyMedia = true)
                    } else {
                        return MediatorResult.Success(endOfPaginationReached = false)
                    }
                }
            }

            val statuses = statusResponse.body()
            if (!statusResponse.isSuccessful || statuses == null) {
                return MediatorResult.Error(HttpException(statusResponse))
            }

            val attachments = statuses.flatMap { status ->
                AttachmentViewData.list(status, activeAccount.alwaysShowSensitiveMedia)
            }

            if (loadType == LoadType.REFRESH) {
                viewModel.attachmentData.clear()
            }

            viewModel.attachmentData.addAll(attachments)

            viewModel.currentSource?.invalidate()
            return MediatorResult.Success(endOfPaginationReached = statuses.isEmpty())
        } catch (e: Exception) {
            return ifExpected(e) {
                MediatorResult.Error(e)
            }
        }
    }
}
