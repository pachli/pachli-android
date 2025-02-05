/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.pachli.core.eventhub.EventHub
import app.pachli.core.network.model.ScheduledStatus
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ScheduledStatusViewModel @Inject constructor(
    val mastodonApi: MastodonApi,
    val eventHub: EventHub,
) : ViewModel() {

    private val pagingSourceFactory = ScheduledStatusPagingSourceFactory(mastodonApi)

    val data = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20),
        pagingSourceFactory = pagingSourceFactory,
    ).flow
        .cachedIn(viewModelScope)

    fun deleteScheduledStatus(status: ScheduledStatus) {
        viewModelScope.launch {
            mastodonApi.deleteScheduledStatus(status.id)
                .onSuccess { pagingSourceFactory.remove(status) }
                .onFailure { Timber.w("Error deleting scheduled status: %s", it) }
        }
    }
}
