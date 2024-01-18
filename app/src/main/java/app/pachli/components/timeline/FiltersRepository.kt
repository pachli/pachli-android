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

package app.pachli.components.timeline

import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.network.ServerRepository
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrThrow
import com.github.michaelbull.result.getOrElse
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

sealed interface FilterKind {
    /** API v1 filter, filtering happens client side */
    data class V1(val filters: List<FilterV1>) : FilterKind

    /** API v2 filter, filtering happens server side */
    data class V2(val filters: List<Filter>) : FilterKind
}

/** Repository for filter information */
@Singleton
class FiltersRepository @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val serverRepository: ServerRepository,
) {
    /**
     * Get the current set of filters.
     *
     * Checks for server-side (v2) filters first. If that fails then fetches filters to
     * apply client-side.
     *
     * @throws HttpException if the requests fail
     */
    suspend fun getFilters(): FilterKind {
        // If fetching capabilities failed then assume no filtering
        val server = serverRepository.flow.value.getOrElse { null } ?: return FilterKind.V2(emptyList())

        // If the server doesn't support filtering then return an empty list of filters
        if (!server.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint()) &&
            !server.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
        ) {
            return FilterKind.V2(emptyList())
        }

        return mastodonApi.getFilters().fold(
            { filters -> FilterKind.V2(filters) },
            { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
                    val filters = mastodonApi.getFiltersV1().getOrThrow()
                    FilterKind.V1(filters)
                } else {
                    throw throwable
                }
            },
        )
    }
}
