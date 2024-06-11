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

package app.pachli.core.network.model.nodeinfo

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.network.R
import app.pachli.core.network.model.nodeinfo.NodeInfo.Error.NoSoftwareBlock
import app.pachli.core.network.model.nodeinfo.NodeInfo.Error.NoSoftwareName
import app.pachli.core.network.model.nodeinfo.NodeInfo.Error.NoSoftwareVersion
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.squareup.moshi.JsonClass

/**
 * The JRD document that links to one or more URLs that contain the schema.
 *
 * See https://nodeinfo.diaspora.software/protocol.html.
 */
@JsonClass(generateAdapter = true)
data class UnvalidatedJrd(val links: List<Link>) {
    @JsonClass(generateAdapter = true)
    data class Link(val rel: String, val href: String)
}

/**
 * An unvalidated schema document. May not conform to the schema.
 *
 * See https://nodeinfo.diaspora.software/protocol.html and
 * https://nodeinfo.diaspora.software/schema.html.
 */
@JsonClass(generateAdapter = true)
data class UnvalidatedNodeInfo(val software: Software?) {
    @JsonClass(generateAdapter = true)
    data class Software(val name: String?, val version: String?)
}

/**
 * A validated NodeInfo.
 *
 * See https://nodeinfo.diaspora.software/protocol.html and
 * https://nodeinfo.diaspora.software/schema.html.
 */
data class NodeInfo(val software: Software) {
    data class Software(
        /** Software name, won't be null, empty, or blank */
        val name: String,
        /** Software version, won't be null, empty, or blank */
        val version: String,
    )

    companion object {
        fun from(nodeInfo: UnvalidatedNodeInfo): Result<NodeInfo, Error> {
            return when {
                nodeInfo.software == null -> Err(NoSoftwareBlock)
                nodeInfo.software.name.isNullOrBlank() -> Err(NoSoftwareName)
                nodeInfo.software.version.isNullOrBlank() -> Err(NoSoftwareVersion)
                else -> Ok(NodeInfo(Software(nodeInfo.software.name, nodeInfo.software.version)))
            }
        }
    }

    sealed class Error(
        @StringRes override val resourceId: Int,
    ) : PachliError {
        override val formatArgs = emptyArray<String>()
        override val cause: PachliError? = null

        data object NoSoftwareBlock : Error(R.string.node_info_error_no_software)
        data object NoSoftwareName : Error(R.string.node_info_error_no_software_name)
        data object NoSoftwareVersion : Error(R.string.node_info_error_no_software_version)
    }
}
