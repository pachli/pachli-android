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

import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.model.Server
import app.pachli.core.data.repository.ContentFiltersError.ServerDoesNotFilter
import app.pachli.core.data.source.ContentFiltersLocalDataSource
import app.pachli.core.data.source.ContentFiltersRemoteDataSource
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import app.pachli.core.model.NewContentFilter
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.retrofit.apiresult.ApiResponse
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map

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

// Hack, so that FilterModel can know whether this is V1 or V2 content filters.
// See usage in:
// - TimelineViewModel.getFilters()
// - NotificationsViewModel.getFilters()
// Need to think about a better way to do this.
data class ContentFilters(
    val contentFilters: List<ContentFilter>,
    val version: ContentFilterVersion,
) {
    companion object {
        fun from(entity: ContentFiltersEntity?) = entity?.let {
            ContentFilters(
                contentFilters = it.contentFilters,
                version = it.version,
            )
        }
    }
}

/** Repository for filter information */
@Singleton
class OfflineFirstContentFiltersRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    private val localDataSource: ContentFiltersLocalDataSource,
    private val remoteDataSource: ContentFiltersRemoteDataSource,
    private val instanceDao: InstanceDao,
) : ContentFiltersRepository {
    /** Get a specific content filter from the server, by [contentFilterId]. */
    override suspend fun getContentFilter(pachliAccountId: Long, contentFilterId: String) =
        localDataSource.getContentFilter(pachliAccountId, contentFilterId)

    override suspend fun refresh(pachliAccountId: Long): Result<ContentFilters, ContentFiltersError> = externalScope.async {
        val server = instanceDao.getServer(pachliAccountId)?.let { Server.from(it) } ?: return@async Err(ServerDoesNotFilter)

        remoteDataSource.getContentFilters(pachliAccountId, server)
            .onSuccess {
                val entity = ContentFiltersEntity(
                    accountId = pachliAccountId,
                    contentFilters = it.contentFilters,
                    version = it.version,
                )
                localDataSource.replace(entity)
            }
    }.await()

    override suspend fun getContentFilters(pachliAccountId: Long) =
        localDataSource.getContentFilters(pachliAccountId)?.let { ContentFilters.from(it) }

    /** Get the current set of content filters. */
    override fun getContentFiltersFlow(pachliAccountId: Long) =
        localDataSource.getContentFiltersFlow(pachliAccountId).map { ContentFilters.from(it) }

    /**
     * Creates the filter in [filter].
     *
     * @return The newly created [ContentFilter], or a [ContentFiltersError].
     */
    override suspend fun createContentFilter(pachliAccountId: Long, filter: NewContentFilter): Result<ContentFilter, ContentFiltersError> = externalScope.async {
        // TODO: Return better error if server data not cached
        val server = instanceDao.getServer(pachliAccountId)?.let { Server.from(it) }
            ?: return@async Err(ServerDoesNotFilter)
        remoteDataSource.createContentFilter(pachliAccountId, server, filter)
            .onSuccess { localDataSource.saveContentFilter(pachliAccountId, it) }
    }.await()

    /**
     * Updates [originalContentFilter] on the server by applying the changes in
     * [contentFilterEdit].
     */
    override suspend fun updateContentFilter(pachliAccountId: Long, originalContentFilter: ContentFilter, contentFilterEdit: ContentFilterEdit): Result<ContentFilter, ContentFiltersError> = externalScope.async {
        val server = instanceDao.getServer(pachliAccountId)?.let { Server.from(it) }
            ?: return@async Err(ServerDoesNotFilter)
        remoteDataSource.updateContentFilter(server, originalContentFilter, contentFilterEdit)
            .onSuccess { localDataSource.updateContentFilter(pachliAccountId, it) }
    }.await()

    /**
     * Deletes the content filter identified by [contentFilterId] from the server.
     */
    override suspend fun deleteContentFilter(pachliAccountId: Long, contentFilterId: String): Result<ApiResponse<Unit>, ContentFiltersError> = externalScope.async {
        // TODO: Return better error if server data not cached
        val server = instanceDao.getServer(pachliAccountId)?.let { Server.from(it) }
            ?: return@async Err(ServerDoesNotFilter)
        remoteDataSource.deleteContentFilter(pachliAccountId, server, contentFilterId)
            .onSuccess { localDataSource.deleteContentFilter(pachliAccountId, contentFilterId) }
    }.await()
}

fun Server.canFilterV1() = this.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint())
fun Server.canFilterV2() = this.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
