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

package app.pachli.core.data.source

import app.pachli.core.data.model.Server
import app.pachli.core.data.model.from
import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.data.repository.ContentFiltersError
import app.pachli.core.data.repository.ContentFiltersError.CreateContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.DeleteContentFilterError
import app.pachli.core.data.repository.ContentFiltersError.GetContentFiltersError
import app.pachli.core.data.repository.ContentFiltersError.ServerDoesNotFilter
import app.pachli.core.data.repository.ContentFiltersError.UpdateContentFilterError
import app.pachli.core.data.repository.canFilterV1
import app.pachli.core.data.repository.canFilterV2
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.NewContentFilter
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapResult
import javax.inject.Inject

class ContentFiltersRemoteDataSource @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun getContentFilters(pachliAccountId: Long, server: Server) = when {
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
    }.mapError { GetContentFiltersError(it) }

    suspend fun createContentFilter(pachliAccountId: Long, server: Server, filter: NewContentFilter) = binding {
        val expiresInSeconds = when (val expiresIn = filter.expiresIn) {
            0 -> ""
            else -> expiresIn.toString()
        }

        when {
            server.canFilterV2() -> {
                mastodonApi.createFilter(filter).map { ContentFilter.from(it.body) }
            }

            server.canFilterV1() -> {
                val networkContexts =
                    filter.contexts.map { FilterContext.from(it) }.toSet()
                filter.toNewContentFilterV1().mapResult {
                    mastodonApi.createFilterV1(
                        phrase = it.phrase,
                        context = networkContexts,
                        irreversible = it.irreversible,
                        wholeWord = it.wholeWord,
                        expiresInSeconds = expiresInSeconds,
                    )
                }.map { ContentFilter.from(it.last().body) }
            }

            else -> Err(ServerDoesNotFilter)
        }.mapError { CreateContentFilterError(it) }.bind()
    }

    suspend fun deleteContentFilter(pachliAccountId: Long, server: Server, contentFilterId: String) = binding {
        when {
            server.canFilterV2() -> mastodonApi.deleteFilter(contentFilterId)
            server.canFilterV1() -> mastodonApi.deleteFilterV1(contentFilterId)
            else -> Err(ServerDoesNotFilter)
        }.mapError { DeleteContentFilterError(it) }.bind()
    }

    suspend fun updateContentFilter(server: Server, originalContentFilter: ContentFilter, contentFilterEdit: ContentFilterEdit): Result<ContentFilter, ContentFiltersError> = binding {
        // Modify
        val expiresInSeconds = when (val expiresIn = contentFilterEdit.expiresIn) {
            -1 -> null
            0 -> ""
            else -> expiresIn.toString()
        }

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
                    val networkContexts = contentFilterEdit.contexts?.map {
                        FilterContext.from(it)
                    }?.toSet()
                    val networkAction = contentFilterEdit.filterAction?.let {
                        FilterAction.from(it)
                    }

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
                val networkContexts = contentFilterEdit.contexts?.map {
                    FilterContext.from(it)
                }?.toSet() ?: originalContentFilter.contexts.map {
                    FilterContext.from(
                        it,
                    )
                }
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
        }.mapError { UpdateContentFilterError(it) }.bind()
    }
}
