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

import app.pachli.core.database.dao.ContentFiltersDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.model.ContentFilter
import javax.inject.Inject

/**
 * Local data source for content filters that exclusively reads/writes to
 * the database.
 */
class ContentFiltersLocalDataSource @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val contentFiltersDao: ContentFiltersDao,
) {
    /**
     * Replaces all content filters for an account with [contentFilters].
     */
    suspend fun replace(contentFilters: ContentFiltersEntity) = contentFiltersDao.upsert(contentFilters)

    /**
     * Gets the content filter in [pachliAccountId] with [contentFilterId].
     *
     * @return The content filter, or null if no filter exists with [contentFilterId].
     */
    suspend fun getContentFilter(pachliAccountId: Long, contentFilterId: String) =
        contentFiltersDao.getByAccount(pachliAccountId)?.contentFilters?.find { it.id == contentFilterId }

    /**
     * Gets all content filters in [pachliAccountId].
     *
     * @return The content filter, or null if no filters exist.
     */
    suspend fun getContentFilters(pachliAccountId: Long) = contentFiltersDao.getByAccount(pachliAccountId)

    /**
     * @return Flow of content filters for [pachliAccountId].
     */
    fun getContentFiltersFlow(pachliAccountId: Long) = contentFiltersDao.flowByAccount(pachliAccountId)

    /**
     * Saves [contentFilter] to [pachliAccountId].
     */
    suspend fun saveContentFilter(pachliAccountId: Long, contentFilter: ContentFilter) {
        transactionProvider {
            val contentFilters = contentFiltersDao.getByAccount(pachliAccountId) ?: return@transactionProvider
            val newContentFilters = contentFilters.copy(
                contentFilters = contentFilters.contentFilters + contentFilter,
            )
            contentFiltersDao.upsert(newContentFilters)
        }
    }

    /**
     * Updates the content filters in [pachliAccountId], the existing content filter with
     * [contentFilter.id][ContentFilter.id] is replaced with [contentFilter].
     */
    suspend fun updateContentFilter(pachliAccountId: Long, contentFilter: ContentFilter) {
        transactionProvider {
            val contentFilters = contentFiltersDao.getByAccount(pachliAccountId) ?: return@transactionProvider
            val newContentFilters = contentFilters.copy(
                contentFilters = contentFilters.contentFilters.map {
                    if (it.id != contentFilter.id) it else contentFilter
                },
            )
            contentFiltersDao.upsert(newContentFilters)
        }
    }

    /**
     * Deletes the content filter with [contentFilterId] from [pachliAccountId].
     */
    suspend fun deleteContentFilter(pachliAccountId: Long, contentFilterId: String) {
        transactionProvider {
            val contentFilters = contentFiltersDao.getByAccount(pachliAccountId) ?: return@transactionProvider
            val newContentFilters = contentFilters.copy(
                contentFilters = contentFilters.contentFilters.filterNot { it.id == contentFilterId },
            )
            contentFiltersDao.upsert(newContentFilters)
        }
    }
}
