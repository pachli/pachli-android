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
import app.pachli.core.data.model.Server
import app.pachli.core.data.model.from
import app.pachli.core.data.repository.ContentFiltersError.CreateContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.DeleteContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.GetContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.GetContentFiltersError
import app.pachli.core.data.repository.ContentFiltersError.ServerDoesNotFilter
import app.pachli.core.data.repository.ContentFiltersError.UpdateContentFilterError
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import app.pachli.core.model.NewContentFilter
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
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

/**
 * Represents a collection of edits to make to an existing content filter.
 *
 * @param id ID of the content filter to be changed
 * @param title New title, null if the title should not be changed
 * @param contexts New contexts, null if the contexts should not be changed
 * @param expiresIn New expiresIn, -1 if the expiry time should not be changed
 * @param filterAction New action, null if the action should not be changed
 * @param keywordsToAdd One or more keywords to add to the content filter, null if none to add
 * @param keywordsToDelete One or more keywords to delete from the content filter, null if none to delete
 * @param keywordsToModify One or more keywords to modify in the content filter, null if none to modify
 */
data class ContentFilterEdit(
    val id: String,
    val title: String? = null,
    val contexts: Collection<FilterContext>? = null,
    val expiresIn: Int = -1,
    val filterAction: FilterAction? = null,
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
) {
    /** Get a specific content filter from the server, by [filterId]. */
    suspend fun getContentFilter(server: Server, filterId: String): Result<ContentFilter, ContentFiltersError> = binding {
        when {
            server.canFilterV2() -> mastodonApi.getFilter(filterId).map { ContentFilter.from(it.body) }
            server.canFilterV1() -> mastodonApi.getFilterV1(filterId).map { ContentFilter.from(it.body) }
            else -> Err(ServerDoesNotFilter)
        }.mapError { GetContentFilterError(it) }.bind()
    }

    /** Get the current set of content filters. */
    suspend fun getContentFilters(server: Server): Result<ContentFilters, ContentFiltersError> = binding {
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
    suspend fun createContentFilter(server: Server, filter: NewContentFilter): Result<ContentFilter, ContentFiltersError> = binding {
        val expiresInSeconds = when (val expiresIn = filter.expiresIn) {
            0 -> ""
            else -> expiresIn.toString()
        }

        externalScope.async {
            when {
                server.canFilterV2() -> {
                    mastodonApi.createFilter(filter).map {
                        ContentFilter.from(it.body)
                    }
                }

                server.canFilterV1() -> {
                    val networkContexts = filter.contexts.map { app.pachli.core.network.model.FilterContext.from(it) }.toSet()
                    filter.toNewContentFilterV1().mapResult {
                        mastodonApi.createFilterV1(
                            phrase = it.phrase,
                            context = networkContexts,
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
        }.await().bind()
    }

    /**
     * Updates [originalContentFilter] on the server by applying the changes in
     * [contentFilterEdit].
     *
     * Reloads filters whether or not an error occured.
     */
    suspend fun updateContentFilter(server: Server, originalContentFilter: ContentFilter, contentFilterEdit: ContentFilterEdit): Result<ContentFilter, ContentFiltersError> = binding {
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
                        contentFilterEdit.filterAction != null ||
                        expiresInSeconds != null
                    ) {
                        val networkContexts = contentFilterEdit.contexts?.map { app.pachli.core.network.model.FilterContext.from(it) }?.toSet()
                        val networkAction = contentFilterEdit.filterAction?.let { app.pachli.core.network.model.FilterAction.from(it) }

                        mastodonApi.updateFilter(
                            id = contentFilterEdit.id,
                            title = contentFilterEdit.title,
                            contexts = networkContexts,
                            filterAction = networkAction,
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
                    val networkContexts = contentFilterEdit.contexts?.map { app.pachli.core.network.model.FilterContext.from(it) }?.toSet() ?: originalContentFilter.contexts.map { app.pachli.core.network.model.FilterContext.from(it) }

                    mastodonApi.updateFilterV1(
                        id = contentFilterEdit.id,
                        phrase = contentFilterEdit.keywordsToModify?.firstOrNull()?.keyword ?: originalContentFilter.keywords.first().keyword,
                        wholeWord = contentFilterEdit.keywordsToModify?.firstOrNull()?.wholeWord,
                        contexts = networkContexts,
                        irreversible = false,
                        expiresInSeconds = expiresInSeconds,
                    ).map { ContentFilter.from(it.body) }
                }
                else -> {
                    Err(ServerDoesNotFilter)
                }
            }.mapError { UpdateContentFilterError(it) }
        }.await().bind()
    }

    /**
     * Deletes the content filter identified by [filterId] from the server.
     *
     * Reloads content filters whether or not an error occured.
     */
    suspend fun deleteContentFilter(server: Server, filterId: String): Result<Unit, ContentFiltersError> = binding {
        externalScope.async {
            when {
                server.canFilterV2() -> mastodonApi.deleteFilter(filterId)
                server.canFilterV1() -> mastodonApi.deleteFilterV1(filterId)
                else -> Err(ServerDoesNotFilter)
            }.mapError { DeleteContentFilterError(it) }
        }.await().bind()
    }
}

fun Server.canFilterV1() = this.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint())
fun Server.canFilterV2() = this.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
