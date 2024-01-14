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

package app.pachli.core.network.retrofit

import app.pachli.core.network.model.nodeinfo.UnvalidatedJrd
import app.pachli.core.network.model.nodeinfo.UnvalidatedNodeInfo
import at.connyduck.calladapter.networkresult.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Url

interface NodeInfoApi {
    /**
     * Instance info from the Nodeinfo .well_known (https://nodeinfo.diaspora.software/protocol.html) endpoint
     */
    @GET("/.well-known/nodeinfo")
    suspend fun nodeInfoJrd(): NetworkResult<UnvalidatedJrd>

    /**
     * Instance info from NodeInfo (https://nodeinfo.diaspora.software/schema.html) endpoint
     */
    @GET
    suspend fun nodeInfo(@Url nodeInfoUrl: String): NetworkResult<UnvalidatedNodeInfo>
}
