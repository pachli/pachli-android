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

package app.pachli.core.data.repository

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.R
import app.pachli.core.data.model.Server
import app.pachli.core.data.repository.ServerRepository.Error.Capabilities
import app.pachli.core.data.repository.ServerRepository.Error.GetInstanceInfoV1
import app.pachli.core.data.repository.ServerRepository.Error.GetNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.GetWellKnownNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.UnsupportedSchema
import app.pachli.core.data.repository.ServerRepository.Error.ValidateNodeInfo
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * NodeInfo schema versions we can parse.
 *
 * See https://nodeinfo.diaspora.software/schema.html.
 */
private val SCHEMAS = listOf(
    "http://nodeinfo.diaspora.software/ns/schema/2.1",
    "http://nodeinfo.diaspora.software/ns/schema/2.0",
    "http://nodeinfo.diaspora.software/ns/schema/1.1",
    "http://nodeinfo.diaspora.software/ns/schema/1.0",
)

@Singleton
class ServerRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val nodeInfoApi: NodeInfoApi,
    accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val reload = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    // SharedFlow, **not** StateFlow, to ensure a new value is emitted even if the
    // user switches between accounts that are on the same server.
    val flow = reload.combine(
        accountManager.activeAccountFlow
            .filterIsInstance<Loadable.Loaded<AccountEntity?>>()
            .distinctUntilChangedBy { it.data?.id },
    ) { _, _ -> getServer() }
        .shareIn(externalScope, SharingStarted.Lazily, replay = 1)

    fun reload() = externalScope.launch { reload.emit(Unit) }

    /**
     * @return the server info or a [Server.Error] if the server info can not
     * be determined.
     */
    private suspend fun getServer(): Result<Server, Error> = binding {
        // Fetch the /.well-known/nodeinfo document
        val nodeInfoJrd = nodeInfoApi.nodeInfoJrd()
            .mapError { GetWellKnownNodeInfo(it) }.bind().body

        // Find a link to a schema we can parse, prefering newer schema versions
        var nodeInfoUrlResult: Result<String, Error> = Err(UnsupportedSchema)
        for (link in nodeInfoJrd.links.sortedByDescending { it.rel }) {
            if (SCHEMAS.contains(link.rel)) {
                nodeInfoUrlResult = Ok(link.href)
                break
            }
        }

        val nodeInfoUrl = nodeInfoUrlResult.bind()

        Timber.d("Loading node info from %s", nodeInfoUrl)
        val nodeInfo = nodeInfoApi.nodeInfo(nodeInfoUrl).mapBoth(
            { it.body.validate().mapError { ValidateNodeInfo(nodeInfoUrl, it) } },
            { Err(GetNodeInfo(nodeInfoUrl, it)) },
        ).bind()

        mastodonApi.getInstanceV2().mapBoth(
            { Server.from(nodeInfo.software, it.body).mapError(::Capabilities) },
            { error ->
                Timber.e(error.throwable, "Couldn't process /api/v2/instance result")
                mastodonApi.getInstanceV1().mapBoth(
                    { Server.from(nodeInfo.software, it.body).mapError(::Capabilities) },
                    { Err(GetInstanceInfoV1(it)) },
                )
            },
        ).bind()
    }

    sealed class Error(
        @StringRes override val resourceId: Int,
        override val formatArgs: Array<out String>? = emptyArray<String>(),
        override val cause: PachliError? = null,
    ) : PachliError {

        data class GetWellKnownNodeInfo(override val cause: PachliError) : Error(
            R.string.server_repository_error_get_well_known_node_info,
        )

        data object UnsupportedSchema : Error(
            R.string.server_repository_error_unsupported_schema,
        )

        data class GetNodeInfo(val url: String, override val cause: PachliError) : Error(
            R.string.server_repository_error_get_node_info,
        )

        data class ValidateNodeInfo(val url: String, val error: UnvalidatedNodeInfo.Error) : Error(
            R.string.server_repository_error_validate_node_info,
            arrayOf(url),
        )

        data class GetInstanceInfoV1(override val cause: PachliError) : Error(
            R.string.server_repository_error_get_instance_info,
        )

        data class Capabilities(override val cause: Server.Error) : Error(
            R.string.server_repository_error_capabilities,
        )
    }
}
