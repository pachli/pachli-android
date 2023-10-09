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

package app.pachli.network

import app.pachli.db.AccountManager
import app.pachli.di.ApplicationScope
import at.connyduck.calladapter.networkresult.fold
import com.github.michaelbull.result.getOr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ServerCapabilitiesRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _flow = MutableStateFlow(ServerCapabilities.default())
    val flow = _flow.asStateFlow()

    init {
        externalScope.launch {
            _flow.emit(getCapabilities())
        }

        externalScope.launch {
            accountManager.activeAccountFlow.collect {
                _flow.emit(getCapabilities())
            }
        }
    }

    /**
     * Returns the capabilities of the current server. If the capabilties cannot be
     * determined then a default set of capabilities that all servers are expected
     * to support is returned.
     */
    private suspend fun getCapabilities(): ServerCapabilities {
        return mastodonApi.getInstanceV2().fold(
            { instance -> ServerCapabilities.from(instance).getOr { null } },
            {
                mastodonApi.getInstanceV1().fold({ instance ->
                    ServerCapabilities.from(instance).getOr { null }
                }, { null },)
            },
        ) ?: ServerCapabilities.default()
    }
}
