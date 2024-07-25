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

package app.pachli.core.data.repository

import androidx.annotation.VisibleForTesting
import app.pachli.core.common.PachliError
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.R
import app.pachli.core.data.model.Filter
import app.pachli.core.data.model.NewFilterKeyword
import app.pachli.core.data.repository.FiltersError.DeleteFilterError
import app.pachli.core.data.repository.FiltersError.GetFiltersError
import app.pachli.core.data.repository.FiltersError.ServerDoesNotFilter
import app.pachli.core.data.repository.FiltersError.ServerRepositoryError
import app.pachli.core.network.Server
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.model.NewFilterV1
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapResult
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A filter to be created or updated.
 *
 * Same as [Filter] except a [NewFilter] does not have an [id][Filter.id], as it
 * has not been created on the server.
 */
data class NewFilter(
    val title: String,
    val contexts: Set<FilterContext>,
    val expiresIn: Int,
    val action: app.pachli.core.network.model.Filter.Action,
    val keywords: List<NewFilterKeyword>,
) {
    fun toNewFilterV1() = this.keywords.map { keyword ->
        NewFilterV1(
            phrase = keyword.keyword,
            contexts = this.contexts,
            expiresIn = this.expiresIn,
            irreversible = false,
            wholeWord = keyword.wholeWord,
        )
    }

    companion object {
        fun from(filter: Filter) = NewFilter(
            title = filter.title,
            contexts = filter.contexts,
            expiresIn = -1,
            action = filter.action,
            keywords = filter.keywords.map {
                NewFilterKeyword(
                    keyword = it.keyword,
                    wholeWord = it.wholeWord,
                )
            },
        )
    }
}

/**
 * Represents a collection of edits to make to an existing filter.
 *
 * @param id ID of the filter to be changed
 * @param title New title, null if the title should not be changed
 * @param contexts New contexts, null if the contexts should not be changed
 * @param expiresIn New expiresIn, -1 if the expiry time should not be changed
 * @param action New action, null if the action should not be changed
 * @param keywordsToAdd One or more keywords to add to the filter, null if none to add
 * @param keywordsToDelete One or more keywords to delete from the filter, null if none to delete
 * @param keywordsToModify One or more keywords to modify in the filter, null if none to modify
 */
data class FilterEdit(
    val id: String,
    val title: String? = null,
    val contexts: Collection<FilterContext>? = null,
    val expiresIn: Int = -1,
    val action: NetworkFilter.Action? = null,
    val keywordsToAdd: List<FilterKeyword>? = null,
    val keywordsToDelete: List<FilterKeyword>? = null,
    val keywordsToModify: List<FilterKeyword>? = null,
)

/** Errors that can be returned from this repository. */
sealed interface FiltersError : PachliError {
    /** Wraps errors from actions on the [ServerRepository]. */
    @JvmInline
    value class ServerRepositoryError(private val error: ServerRepository.Error) :
        FiltersError, PachliError by error

    /** The user's server does not support filters. */
    data object ServerDoesNotFilter : FiltersError {
        override val resourceId: Int = R.string.error_filter_server_does_not_filter
        override val formatArgs: Array<out Any>? = null
        override val cause: PachliError? = null
    }

    /** API error fetching a filter by ID. */
    @JvmInline
    value class GetFilterError(private val error: PachliError) : FiltersError, PachliError by error

    /** API error fetching all filters. */
    @JvmInline
    value class GetFiltersError(@get:VisibleForTesting val error: PachliError) : FiltersError, PachliError by error

    /** API error creating a filter. */
    @JvmInline
    value class CreateFilterError(private val error: PachliError) : FiltersError, PachliError by error

    /** API error updating a filter. */
    @JvmInline
    value class UpdateFilterError(private val error: PachliError) : FiltersError, PachliError by error

    /** API error deleting a filter. */
    @JvmInline
    value class DeleteFilterError(private val error: PachliError) : FiltersError, PachliError by error
}

enum class FilterVersion {
    V1,
    V2,
}

// Hack, so that FilterModel can know whether this is V1 or V2 filters.
// See usage in:
// - TimelineViewModel.getFilters()
// - NotificationsViewModel.getFilters()
// Need to think about a better way to do this.
data class Filters(
    val filters: List<Filter>,
    val version: FilterVersion,
)

/** Repository for filter information */
@Singleton
class FiltersRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val mastodonApi: MastodonApi,
    serverRepository: ServerRepository,
) {
    /** Flow where emissions trigger fresh loads from the server. */
    private val reload = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private lateinit var server: Result<Server, ServerRepositoryError>

    /**
     * Flow of filters from the server. Updates when:
     *
     * - A new value is emitted to [reload]
     * - The active server changes
     *
     * The [Ok] value is either `null` if the filters have not yet been loaded, or
     * the most recent loaded filters.
     */
    val filters = reload.combine(serverRepository.flow) { _, server ->
        this.server = server.mapError { ServerRepositoryError(it) }
        server
            .mapError { GetFiltersError(it) }
            .andThen { getFilters(it) }
    }
        .stateIn(externalScope, SharingStarted.Lazily, Ok(null))

    suspend fun reload() = reload.emit(Unit)

    /** Get a specific filter from the server, by [filterId]. */
    suspend fun getFilter(filterId: String): Result<Filter, FiltersError> = binding {
        val server = server.bind()

        when {
            server.canFilterV2() -> mastodonApi.getFilter(filterId).map { Filter.from(it.body) }
            server.canFilterV1() -> mastodonApi.getFilterV1(filterId).map { Filter.from(it.body) }
            else -> Err(ServerDoesNotFilter)
        }.mapError { FiltersError.GetFilterError(it) }.bind()
    }

    /** Get the current set of filters. */
    private suspend fun getFilters(server: Server): Result<Filters, FiltersError> = binding {
        when {
            server.canFilterV2() -> mastodonApi.getFilters().map {
                Filters(
                    filters = it.body.map { Filter.from(it) },
                    version = FilterVersion.V2,
                )
            }
            server.canFilterV1() -> mastodonApi.getFiltersV1().map {
                Filters(
                    filters = it.body.map { Filter.from(it) },
                    version = FilterVersion.V1,
                )
            }
            else -> Err(ServerDoesNotFilter)
        }.mapError { GetFiltersError(it) }.bind()
    }

    /**
     * Creates the filter in [filter].
     *
     * Reloads filters whether or not an error occured.
     *
     * @return The newly created [Filter], or a [FiltersError].
     */
    suspend fun createFilter(filter: NewFilter): Result<Filter, FiltersError> = binding {
        val server = server.bind()

        val expiresInSeconds = when (val expiresIn = filter.expiresIn) {
            0 -> ""
            else -> expiresIn.toString()
        }

        externalScope.async {
            when {
                server.canFilterV2() -> {
                    mastodonApi.createFilter(
                        title = filter.title,
                        contexts = filter.contexts,
                        filterAction = filter.action,
                        expiresInSeconds = expiresInSeconds,
                    ).andThen { response ->
                        val filterId = response.body.id
                        filter.keywords.mapResult {
                            mastodonApi.addFilterKeyword(
                                filterId,
                                keyword = it.keyword,
                                wholeWord = it.wholeWord,
                            )
                        }.map { Filter.from(response.body) }
                    }
                }

                server.canFilterV1() -> {
                    filter.toNewFilterV1().mapResult {
                        mastodonApi.createFilterV1(
                            phrase = it.phrase,
                            context = it.contexts,
                            irreversible = it.irreversible,
                            wholeWord = it.wholeWord,
                            expiresInSeconds = expiresInSeconds,
                        )
                    }.map {
                        Filter.from(it.last().body)
                    }
                }

                else -> Err(ServerDoesNotFilter)
            }.mapError { FiltersError.CreateFilterError(it) }
                .also { reload.emit(Unit) }
        }.await().bind()
    }

    /**
     * Updates [originalFilter] on the server by applying the changes in
     * [filterEdit].
     *
     * Reloads filters whether or not an error occured.*
     */
    suspend fun updateFilter(originalFilter: Filter, filterEdit: FilterEdit): Result<Filter, FiltersError> = binding {
        val server = server.bind()

        // Modify
        val expiresInSeconds = when (val expiresIn = filterEdit.expiresIn) {
            -1 -> null
            0 -> ""
            else -> expiresIn.toString()
        }

        externalScope.async {
            when {
                server.canFilterV2() -> {
                    // Retrofit can't send a form where there are multiple parameters
                    // with the same ID (https://github.com/square/retrofit/issues/1324)
                    // so it's not possible to update keywords

                    if (filterEdit.title != null ||
                        filterEdit.contexts != null ||
                        filterEdit.action != null ||
                        expiresInSeconds != null
                    ) {
                        mastodonApi.updateFilter(
                            id = filterEdit.id,
                            title = filterEdit.title,
                            contexts = filterEdit.contexts,
                            filterAction = filterEdit.action,
                            expiresInSeconds = expiresInSeconds,
                        )
                    } else {
                        Ok(originalFilter)
                    }
                        .andThen {
                            filterEdit.keywordsToDelete.orEmpty().mapResult {
                                mastodonApi.deleteFilterKeyword(it.id)
                            }
                        }
                        .andThen {
                            filterEdit.keywordsToModify.orEmpty().mapResult {
                                mastodonApi.updateFilterKeyword(
                                    it.id,
                                    it.keyword,
                                    it.wholeWord,
                                )
                            }
                        }
                        .andThen {
                            filterEdit.keywordsToAdd.orEmpty().mapResult {
                                mastodonApi.addFilterKeyword(
                                    filterEdit.id,
                                    it.keyword,
                                    it.wholeWord,
                                )
                            }
                        }
                        .andThen {
                            mastodonApi.getFilter(originalFilter.id)
                        }
                        .map { Filter.from(it.body) }
                }
                server.canFilterV1() -> {
                    mastodonApi.updateFilterV1(
                        id = filterEdit.id,
                        phrase = filterEdit.keywordsToModify?.firstOrNull()?.keyword ?: originalFilter.keywords.first().keyword,
                        wholeWord = filterEdit.keywordsToModify?.firstOrNull()?.wholeWord,
                        contexts = filterEdit.contexts ?: originalFilter.contexts,
                        irreversible = false,
                        expiresInSeconds = expiresInSeconds,
                    ).map { Filter.from(it.body) }
                }
                else -> {
                    Err(ServerDoesNotFilter)
                }
            }.mapError { FiltersError.UpdateFilterError(it) }
                .also { reload() }
        }.await().bind()
    }

    /**
     * Deletes the filter identified by [filterId] from the server.
     *
     * Reloads filters whether or not an error occured.
     */
    suspend fun deleteFilter(filterId: String): Result<Unit, FiltersError> = binding {
        val server = server.bind()

        externalScope.async {
            when {
                server.canFilterV2() -> mastodonApi.deleteFilter(filterId)
                server.canFilterV1() -> mastodonApi.deleteFilterV1(filterId)
                else -> Err(ServerDoesNotFilter)
            }.mapError { DeleteFilterError(it) }
                .also { reload() }
        }.await().bind()
    }
}

private fun Server.canFilterV1() = this.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint())
private fun Server.canFilterV2() = this.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
