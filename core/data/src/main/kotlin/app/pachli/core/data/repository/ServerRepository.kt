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
import app.pachli.core.data.repository.ServerRepository.Error.GetInstanceInfoV1
import app.pachli.core.data.repository.ServerRepository.Error.GetNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.GetWellKnownNodeInfo
import app.pachli.core.data.repository.ServerRepository.Error.UnsupportedSchema
import app.pachli.core.data.repository.ServerRepository.Error.ValidateNodeInfo
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.InstanceInfo
import app.pachli.core.model.NodeInfo
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
import com.github.michaelbull.result.onSuccess
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
    private val instanceDao: InstanceDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    // TODO: Document.
    suspend fun getServer(account: AccountEntity): Result<Server, ServerRepository.Error> = binding {
        val deferNodeInfo = externalScope.async {
            fetchNodeInfo()
        }

        val deferInstanceInfo = externalScope.async {
            fetchInstanceInfo(account.domain)
        }

        val nodeInfo = deferNodeInfo.await().bind()
        val instanceInfo = deferInstanceInfo.await().bind()

        // TODO: Rename instanceDao to serverDao

        // TODO: Split EmojiDao out.
        val server = Server.from(nodeInfo.software, instanceInfo)
            .mapError { Error.Capabilities(it) }
            .onSuccess { instanceDao.upsert(it.asEntity(account.id)) }
            .bind()

        return@binding server
    }

    private suspend fun fetchInstanceInfo(domain: String): Result<InstanceInfo, GetInstanceInfoV1> {
        return mastodonApi.getInstanceV2()
            .map { it.body.asModel(domain) }
            .orElse {
                mastodonApi.getInstanceV1().mapEither(
                    { it.body.asModel(domain) },
                    { GetInstanceInfoV1(it) },
                )
            }
    }

    private suspend fun fetchNodeInfo(): Result<NodeInfo, Error> = binding {
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

        return@binding nodeInfo
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
            arrayOf(url),
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
