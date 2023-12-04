/*
 * Copyright 2023 Pachli Association
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

package app.pachli.components.notifications

import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

class NotificationsRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val gson: Gson,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private var factory: InvalidatingPagingSourceFactory<String, Notification>? = null

    /**
     * @return flow of Mastodon [Notification], excluding all types in [filter].
     * Notifications are loaded in [pageSize] increments.
     */
    fun getNotificationsStream(
        filter: Set<Notification.Type>,
        pageSize: Int = PAGE_SIZE,
        initialKey: String? = null,
    ): Flow<PagingData<Notification>> {
        Timber.d("getNotificationsStream(), filtering: $filter")

        factory = InvalidatingPagingSourceFactory {
            NotificationsPagingSource(mastodonApi, gson, filter)
        }

        return Pager(
            config = PagingConfig(pageSize = pageSize, initialLoadSize = pageSize),
            initialKey = initialKey,
            pagingSourceFactory = factory!!,
        ).flow
    }

    /** Invalidate the active paging source, see [PagingSource.invalidate] */
    fun invalidate() {
        factory?.invalidate()
    }

    /** Clear notifications */
    suspend fun clearNotifications(): Response<ResponseBody> = externalScope.async {
        return@async mastodonApi.clearNotifications()
    }.await()

    companion object {
        private const val PAGE_SIZE = 30
    }
}
