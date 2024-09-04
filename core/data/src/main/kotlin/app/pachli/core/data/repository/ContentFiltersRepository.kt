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
import app.pachli.core.data.model.ContentFilter
import app.pachli.core.data.model.NewContentFilterKeyword
import app.pachli.core.data.repository.ContentFiltersError.CreateContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.DeleteContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.GetContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.GetContentFiltersError
import app.pachli.core.data.repository.ContentFiltersError.ServerDoesNotFilter
import app.pachli.core.data.repository.ContentFiltersError.ServerRepositoryError
import app.pachli.core.data.repository.ContentFiltersError.UpdateContentFilterError
import app.pachli.core.network.Server
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.model.Filter as NetworkFilter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.model.NewContentFilterV1
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
 * A content filter to be created or updated.
 *
 * Same as [ContentFilter] except a [NewContentFilter] does not have an [id][ContentFilter.id], as it
 * has not been created on the server.
 */
data class NewContentFilter(
    val title: String,
    val contexts: Set<FilterContext>,
    val expiresIn: Int,
    val action: app.pachli.core.network.model.Filter.Action,
    val keywords: List<NewContentFilterKeyword>,
) {
    fun toNewContentFilterV1() = this.keywords.map { keyword ->
        NewContentFilterV1(
            phrase = keyword.keyword,
            contexts = this.contexts,
            expiresIn = this.expiresIn,
            irreversible = false,
            wholeWord = keyword.wholeWord,
        )
    }

    companion object {
        fun from(contentFilter: ContentFilter) = NewContentFilter(
            title = contentFilter.title,
            contexts = contentFilter.contexts,
            expiresIn = -1,
            action = contentFilter.action,
            keywords = contentFilter.keywords.map {
                NewContentFilterKeyword(
                    keyword = it.keyword,
                    wholeWord = it.wholeWord,
                )
            },
        )
    }
}

/**
 * Represents a collection of edits to make to an existing content filter.
 *
 * @param id ID of the content filter to be changed
 * @param title New title, null if the title should not be changed
 * @param contexts New contexts, null if the contexts should not be changed
 * @param expiresIn New expiresIn, -1 if the expiry time should not be changed
 * @param action New action, null if the action should not be changed
 * @param keywordsToAdd One or more keywords to add to the content filter, null if none to add
 * @param keywordsToDelete One or more keywords to delete from the content filter, null if none to delete
 * @param keywordsToModify One or more keywords to modify in the content filter, null if none to modify
 */
data class ContentFilterEdit(
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
sealed interface ContentFiltersError : PachliError {
    /** Wraps errors from actions on the [ServerRepository]. */
    @JvmInline
    value class ServerRepositoryError(private val error: ServerRepository.Error) :
        ContentFiltersError, PachliError by error

    /** The user's server does not support filters. */
    data object ServerDoesNotFilter : ContentFiltersError {
        override val resourceId: Int = R.string.error_filter_server_does_not_filter
        override val formatArgs: Array<out Any>? = null
        override val cause: PachliError? = null
    }

    /** API error fetching a filter by ID. */
    @JvmInline
    value class GetContentFilterError(private val error: PachliError) : ContentFiltersError, PachliError by error

    /** API error fetching all filters. */
    @JvmInline
    value class GetContentFiltersError(@get:VisibleForTesting val error: PachliError) : ContentFiltersError, PachliError by error

    /** API error creating a filter. */
    @JvmInline
    value class CreateContentFilterError(private val error: PachliError) : ContentFiltersError, PachliError by error

    /** API error updating a filter. */
    @JvmInline
    value class UpdateContentFilterError(private val error: PachliError) : ContentFiltersError, PachliError by error

    /** API error deleting a filter. */
    @JvmInline
    value class DeleteContentFilterError(private val error: PachliError) : ContentFiltersError, PachliError by error
}

enum class ContentFilterVersion {
    V1,
    V2,
}

// Hack, so that FilterModel can know whether this is V1 or V2 content filters.
// See usage in:
// - TimelineViewModel.getFilters()
// - NotificationsViewModel.getFilters()
// Need to think about a better way to do this.
data class ContentFilters(
    val contentFilters: List<ContentFilter>,
    val version: ContentFilterVersion,
)

/** Repository for filter information */
@Singleton
class ContentFiltersRepository @Inject constructor(
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
    val contentFilters = reload.combine(serverRepository.flow) { _, server ->
        this.server = server.mapError { ServerRepositoryError(it) }
        server
            .mapError { GetContentFiltersError(it) }
            .andThen { getContentFilters(it) }
    }
        .stateIn(externalScope, SharingStarted.Lazily, Ok(null))

    suspend fun reload() = reload.emit(Unit)

    /** Get a specific content filter from the server, by [filterId]. */
    suspend fun getContentFilter(filterId: String): Result<ContentFilter, ContentFiltersError> = binding {
        val server = server.bind()

        when {
            server.canFilterV2() -> mastodonApi.getFilter(filterId).map { ContentFilter.from(it.body) }
            server.canFilterV1() -> mastodonApi.getFilterV1(filterId).map { ContentFilter.from(it.body) }
            else -> Err(ServerDoesNotFilter)
        }.mapError { GetContentFilterError(it) }.bind()
    }

    /** Get the current set of content filters. */
    private suspend fun getContentFilters(server: Server): Result<ContentFilters, ContentFiltersError> = binding {
        when {
            server.canFilterV2() -> mastodonApi.getContentFilters().map {
                ContentFilters(
                    contentFilters = it.body.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V2,
                )
            }

            server.canFilterV1() -> mastodonApi.getContentFiltersV1().map {
                ContentFilters(
                    contentFilters = it.body.map { ContentFilter.from(it) },
                    version = ContentFilterVersion.V1,
                )
            }
            else -> Err(ServerDoesNotFilter)
        }.mapError { GetContentFiltersError(it) }.bind()
    }

    /**
     * Creates the filter in [filter].
     *
     * Reloads filters whether or not an error occured.
     *
     * @return The newly created [ContentFilter], or a [ContentFiltersError].
     */
    suspend fun createContentFilter(filter: NewContentFilter): Result<ContentFilter, ContentFiltersError> = binding {
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
                        }.map { ContentFilter.from(response.body) }
                    }
                }

                server.canFilterV1() -> {
                    filter.toNewContentFilterV1().mapResult {
                        mastodonApi.createFilterV1(
                            phrase = it.phrase,
                            context = it.contexts,
                            irreversible = it.irreversible,
                            wholeWord = it.wholeWord,
                            expiresInSeconds = expiresInSeconds,
                        )
                    }.map {
                        ContentFilter.from(it.last().body)
                    }
                }

                else -> Err(ServerDoesNotFilter)
            }.mapError { CreateContentFilterError(it) }
                .also { reload.emit(Unit) }
        }.await().bind()
    }

    /**
     * Updates [originalContentFilter] on the server by applying the changes in
     * [contentFilterEdit].
     *
     * Reloads filters whether or not an error occured.
     */
    suspend fun updateContentFilter(originalContentFilter: ContentFilter, contentFilterEdit: ContentFilterEdit): Result<ContentFilter, ContentFiltersError> = binding {
        val server = server.bind()

        // Modify
        val expiresInSeconds = when (val expiresIn = contentFilterEdit.expiresIn) {
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

                    if (contentFilterEdit.title != null ||
                        contentFilterEdit.contexts != null ||
                        contentFilterEdit.action != null ||
                        expiresInSeconds != null
                    ) {
                        mastodonApi.updateFilter(
                            id = contentFilterEdit.id,
                            title = contentFilterEdit.title,
                            contexts = contentFilterEdit.contexts,
                            filterAction = contentFilterEdit.action,
                            expiresInSeconds = expiresInSeconds,
                        )
                    } else {
                        Ok(originalContentFilter)
                    }
                        .andThen {
                            contentFilterEdit.keywordsToDelete.orEmpty().mapResult {
                                mastodonApi.deleteFilterKeyword(it.id)
                            }
                        }
                        .andThen {
                            contentFilterEdit.keywordsToModify.orEmpty().mapResult {
                                mastodonApi.updateFilterKeyword(
                                    it.id,
                                    it.keyword,
                                    it.wholeWord,
                                )
                            }
                        }
                        .andThen {
                            contentFilterEdit.keywordsToAdd.orEmpty().mapResult {
                                mastodonApi.addFilterKeyword(
                                    contentFilterEdit.id,
                                    it.keyword,
                                    it.wholeWord,
                                )
                            }
                        }
                        .andThen {
                            mastodonApi.getFilter(originalContentFilter.id)
                        }
                        .map { ContentFilter.from(it.body) }
                }
                server.canFilterV1() -> {
                    mastodonApi.updateFilterV1(
                        id = contentFilterEdit.id,
                        phrase = contentFilterEdit.keywordsToModify?.firstOrNull()?.keyword ?: originalContentFilter.keywords.first().keyword,
                        wholeWord = contentFilterEdit.keywordsToModify?.firstOrNull()?.wholeWord,
                        contexts = contentFilterEdit.contexts ?: originalContentFilter.contexts,
                        irreversible = false,
                        expiresInSeconds = expiresInSeconds,
                    ).map { ContentFilter.from(it.body) }
                }
                else -> {
                    Err(ServerDoesNotFilter)
                }
            }.mapError { UpdateContentFilterError(it) }
                .also { reload() }
        }.await().bind()
    }

    /**
     * Deletes the content filter identified by [filterId] from the server.
     *
     * Reloads content filters whether or not an error occured.
     */
    suspend fun deleteContentFilter(filterId: String): Result<Unit, ContentFiltersError> = binding {
        val server = server.bind()

        externalScope.async {
            when {
                server.canFilterV2() -> mastodonApi.deleteFilter(filterId)
                server.canFilterV1() -> mastodonApi.deleteFilterV1(filterId)
                else -> Err(ServerDoesNotFilter)
            }.mapError { DeleteContentFilterError(it) }
                .also { reload() }
        }.await().bind()
    }
}

private fun Server.canFilterV1() = this.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint())
private fun Server.canFilterV2() = this.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
