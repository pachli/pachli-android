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
import app.pachli.core.data.repository.ServerRepository.ServerError.Capabilities
import app.pachli.core.data.repository.ServerRepository.ServerError.GetCustomEmojis
import app.pachli.core.data.repository.ServerRepository.ServerError.GetInstanceInfoV1
import app.pachli.core.data.repository.ServerRepository.ServerError.GetNodeInfo
import app.pachli.core.data.repository.ServerRepository.ServerError.GetWellKnownNodeInfo
import app.pachli.core.data.repository.ServerRepository.ServerError.UnsupportedSchema
import app.pachli.core.data.repository.ServerRepository.ServerError.ValidateNodeInfo
import app.pachli.core.database.dao.ServerDao
import app.pachli.core.model.Emoji
import app.pachli.core.model.InstanceInfo
import app.pachli.core.model.NodeInfo
import app.pachli.core.model.Server
import app.pachli.core.network.model.asModel
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.NodeInfoApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import timber.log.Timber

/**
 * NodeInfo schema versions we can parse.
 *
 * See https://nodeinfo.diaspora.software/schema.html.
 */
val SCHEMAS = listOf(
    "http://nodeinfo.diaspora.software/ns/schema/2.1",
    "http://nodeinfo.diaspora.software/ns/schema/2.0",
    "http://nodeinfo.diaspora.software/ns/schema/1.1",
    "http://nodeinfo.diaspora.software/ns/schema/1.0",
)

@Singleton
class ServerRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val nodeInfoApi: NodeInfoApi,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    /**
     * Fetches the server information for the active account from the remote
     * server.
     *
     * It is the caller's responsibility to save this information (e.g,. to
     * [ServerDao]).
     */
    suspend fun getServer(): Result<Server, ServerError> = binding {
        val deferNodeInfo = externalScope.async {
            fetchNodeInfo()
        }

        val deferInstanceInfo = externalScope.async {
            fetchInstanceInfo()
        }

        val deferEmojis = externalScope.async {
            fetchEmojis()
        }

        val nodeInfo = deferNodeInfo.await().bind()
        val instanceInfo = deferInstanceInfo.await().bind()
        val emojis = deferEmojis.await().bind()

        val server = Server.from(nodeInfo.software, instanceInfo, emojis)
            .mapError { Capabilities(it) }
            .bind()

        return@binding server
    }

    /** Fetches the node info for the server. */
    private suspend fun fetchNodeInfo(): Result<NodeInfo, ServerError> = binding {
        // Fetch the /.well-known/nodeinfo document
        val nodeInfoJrd = nodeInfoApi.nodeInfoJrd()
            .mapError { GetWellKnownNodeInfo(it) }.bind().body

        // Find a link to a schema we can parse, prefering newer schema versions
        var nodeInfoUrlResult: Result<String, ServerError> = Err(UnsupportedSchema)
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

        return@binding nodeInfo
    }

    /** Fetches the instance information for the active account. */
    private suspend fun fetchInstanceInfo(): Result<InstanceInfo, GetInstanceInfoV1> {
        return mastodonApi.getInstanceV2()
            .map { it.body.asModel() }
            .orElse {
                mastodonApi.getInstanceV1().mapEither(
                    { it.body.asModel() },
                    { GetInstanceInfoV1(it) },
                )
            }
    }

    private suspend fun fetchEmojis(): Result<List<Emoji>, GetCustomEmojis> {
        return mastodonApi.getCustomEmojis().mapEither(
            { it.body.asModel() },
            { GetCustomEmojis(it) },
        )
    }

    sealed class ServerError(
        @StringRes override val resourceId: Int,
        override val formatArgs: Array<out String>? = emptyArray<String>(),
        override val cause: PachliError? = null,
    ) : PachliError {

        data class GetWellKnownNodeInfo(override val cause: PachliError) : ServerError(
            R.string.server_repository_error_get_well_known_node_info,
        )

        data object UnsupportedSchema : ServerError(
            R.string.server_repository_error_unsupported_schema,
        )

        data class GetNodeInfo(val url: String, override val cause: PachliError) : ServerError(
            R.string.server_repository_error_get_node_info,
            arrayOf(url),
        )

        data class ValidateNodeInfo(val url: String, val error: UnvalidatedNodeInfo.Error) : ServerError(
            R.string.server_repository_error_validate_node_info,
            arrayOf(url),
        )

        data class GetInstanceInfoV1(override val cause: PachliError) : ServerError(
            R.string.server_repository_error_get_instance_info,
        )

        data class GetCustomEmojis(override val cause: PachliError) : ServerError(
            R.string.server_repository_error_get_emojis,
        )

        data class Capabilities(override val cause: Server.Error) : ServerError(
            R.string.server_repository_error_capabilities,
        )
    }
}
