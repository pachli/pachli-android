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
import app.pachli.core.data.R
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.NewContentFilter
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

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

interface ContentFiltersRepository {
    /** @return Known content filters for [pachliAccountId]. */
    suspend fun getContentFilters(pachliAccountId: Long): ContentFilters

    /** @return Flow of known content filters for [pachliAccountId]. */
    fun getContentFiltersFlow(
        pachliAccountId: Long,
    ): Flow<ContentFilters>

    /**
     * @return The content filter with [contentFilterId] in [pachliAccountId] or
     * null if no such content filter exists.
     */
    suspend fun getContentFilter(
        pachliAccountId: Long,
        contentFilterId: String,
    ): ContentFilter?

    /**
     * Refreshes the list of content filters for [pachliAccountId] and emits into
     * the flow returned by [getContentFiltersFlow].
     *
     * @return The latest set of content filters, or an error.
     */
    suspend fun refresh(
        pachliAccountId: Long,
    ): Result<ContentFilters, ContentFiltersError>

    /**
     * Creates a new content filter.
     *
     * @param pachliAccountId Account the new content filter is saved to.
     * @param filter The new content filter.
     * @return The newly created filter, or an error.
     */
    suspend fun createContentFilter(
        pachliAccountId: Long,
        filter: NewContentFilter,
    ): Result<ContentFilter, ContentFiltersError>

    /**
     * Updates an existing content filter.
     *
     * @param pachliAccountId Account that owns the content filter to update.
     * @param originalContentFilter
     * @param contentFilterEdit
     * @return
     */
    suspend fun updateContentFilter(
        pachliAccountId: Long,
        originalContentFilter: ContentFilter,
        contentFilterEdit: ContentFilterEdit,
    ): Result<ContentFilter, ContentFiltersError>

    /**
     * Deletes an existing content filter.
     *
     * @param pachliAccountId Account that owns the content filters.
     * @param contentFilterId ID of the content filter to delete
     * @return Unit, or an error.
     */
    suspend fun deleteContentFilter(
        pachliAccountId: Long,
        contentFilterId: String,
    ): Result<ApiResponse<Unit>, ContentFiltersError>
}
