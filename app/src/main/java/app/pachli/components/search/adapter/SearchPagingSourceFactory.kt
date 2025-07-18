/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.search.adapter

import app.pachli.components.search.SearchType
import app.pachli.core.model.SearchResult
import app.pachli.core.network.retrofit.MastodonApi

class SearchPagingSourceFactory<T : Any>(
    private val mastodonApi: MastodonApi,
    private val searchType: SearchType,
    private val initialItems: List<T>? = null,
    private val parser: (SearchResult) -> List<T>,
) : () -> SearchPagingSource<T> {

    private var searchRequest: String = ""

    private var currentSource: SearchPagingSource<T>? = null

    override fun invoke(): SearchPagingSource<T> {
        return SearchPagingSource(
            mastodonApi = mastodonApi,
            searchType = searchType,
            searchRequest = searchRequest,
            initialItems = initialItems,
            parser = parser,
        ).also { source ->
            currentSource = source
        }
    }

    fun newSearch(newSearchRequest: String) {
        this.searchRequest = newSearchRequest
        currentSource?.invalidate()
    }

    fun invalidate() {
        currentSource?.invalidate()
    }
}
