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
import app.pachli.core.model.NodeInfo
import app.pachli.core.network.R
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

    fun validate(): Result<NodeInfo, Error> {
        return when {
            software == null -> Err(Error.NoSoftwareBlock)
            software.name.isNullOrBlank() -> Err(Error.NoSoftwareName)
            software.version.isNullOrBlank() -> Err(Error.NoSoftwareVersion)
            else -> Ok(NodeInfo(NodeInfo.Software(software.name, software.version)))
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
