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

package app.pachli.components.timeline.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.paging.LoadType
import app.pachli.BuildConfig
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.Status
import java.util.LinkedList
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

/** A page of data from the Mastodon API */
data class Page(
    /** Loaded data */
    val data: MutableList<Status>,
    /**
     * Key for previous page (newer results, PREPEND operation) if more data can be loaded in
     * that direction, `null` otherwise.
     */
    val prevKey: String? = null,
    /**
     * Key for next page (older results, APPEND operation) if more data can be loaded in that
     * direction, `null` otherwise.
     */
    val nextKey: String? = null,
) {
    override fun toString() = "size: ${"%2d".format(data.size)}, range: ${data.firstOrNull()?.id}..${data.lastOrNull()?.id}, prevKey: $prevKey, nextKey: $nextKey"

    companion object {
        fun tryFrom(response: Response<List<Status>>): Result<Page> {
            val statuses = response.body()
            if (!response.isSuccessful || statuses == null) {
                return failure(HttpException(response))
            }

            val links = Links.from(response.headers()["link"])
            Timber.d("  link: %s", response.headers()["link"])
            Timber.d("  %d - # statuses loaded", statuses.size)

            return success(
                Page(
                    data = statuses.toMutableList(),
                    nextKey = links.next,
                    prevKey = links.prev,
                ),
            )
        }
    }
}

/**
 * Cache of pages from the Mastodon API.
 *
 * Add pages to the cache with [add].
 *
 * To get a page from the cache you can either:
 *
 * - Get the first page (contains newest items) with [firstPage]
 * - Get the last page (contains oldest items) with [lastPage]
 * - Get the page that contains an item with a given ID with [getPageById]
 *
 * If you have a page and want to get the immediately previous (newer) page use
 * [getPrevPage] passing the `prevKey` of the original page.
 *
 * ```kotlin
 * val page = pageCache.getPageById("some_id")
 * val previousPage = pageCache.getPageBefore(page.prevKey)
 * ```
 *
 * If you have a page and want to get the immediately next (older) page use
 * [getNextPage] passing the `nextKey` of the original page.
 *
 * ```kotlin
 * val page = pageCache.getPageById("some_id")
 * val nextPage = pageCache.getPageAfter(page.nextKey)
 * ```
 *
 */
// This is more complicated than I'd like.
//
// A naive approach would model a cache of pages as an ordered map, where the map key is an
// ID of the oldest status in the cache.
//
// This does not work because not all timelines of statuses are ordered by status ID. E.g.,
//
// - Trending statuses is ordered by server-determined popularity
// - Bookmarks and favourites are ordered by the time the user performed the bookmark or
//   favourite operation
//
// So a page of data returned from the Mastodon API does not have an intrinsic ID that can
// be used as a cache key.
//
// In addition, we generally want to find a page using one of three identifiers:
//
// - The item ID of an item that is in the page (e.g., status ID)
// - The `prevKey` value of another page
// - The `nextKey` value of another page
//
// So a single map with a single key doesn't work either.
//
// The three identifiers (status ID, `prevKey`, and `nextKey`) can be in different
// namespaces. For some timelines (e.g., the local timeline) they are status IDs. But in
// other timelines, like bookmarks or favourites, they are opaque tokens.
//
// For example, bookmarks might have item keys that look like 110542553707722778
// but prevKey / nextKey values that look like 1480606 / 1229303.
//
// `prevKey` and `nextKey` values are not guaranteed to match on either side. So if you
// have three pages like this
//
//       <-- newer             older -->
//           Page1    Page2    Page3
//
// Page1 might point back to Page2 with `nextKey = xxx`. But Page3 **does not** have to
// point back to Page2 with `prevKey = xxx`, if can use a different value. And if all
// you have is Page2 you can't ask "What prevKey value does Page3 use to point back?"
class PageCache {
    /** Map from item identifier (e.g,. status ID) to the page that contains this item */
    @VisibleForTesting
    val idToPage = mutableMapOf<String, Page>()

    /**
     * List of all pages, in display order. Pages at the front of the list are
     * newer results from the API and are displayed first.
     */
    private val pages = LinkedList<Page>()

    /** The first page in the cache (i.e., the top / newest entry in the timeline) */
    val firstPage: Page?
        get() = pages.firstOrNull()

    /** The last page in the cache (i.e., the bottom / oldest entry in the timeline) */
    val lastPage: Page?
        get() = pages.lastOrNull()

    /** The size of the cache, in pages */
    val size
        get() = pages.size

    /** The values in the cache */
    val values
        get() = idToPage.values

    /** Adds [page] to the cache with the given [loadType] */
    fun add(page: Page, loadType: LoadType) {
        // Refreshing clears the cache then adds the page. Prepend and Append
        // only have to add the page at the appropriate position
        when (loadType) {
            LoadType.REFRESH -> {
                clear()
                pages.add(page)
            }
            LoadType.PREPEND -> pages.addFirst(page)
            LoadType.APPEND -> pages.addLast(page)
        }

        // Insert the items from the page in to the cache
        page.data.forEach { status ->
            // There should never be duplicate items across all pages. Enforce this in debug mode
            if (BuildConfig.DEBUG) {
                if (idToPage.containsKey(status.id)) {
                    debug()
                    throw IllegalStateException("Duplicate item ID ${status.id} in pagesById")
                }
            }
            idToPage[status.id] = page
        }
    }

    /** @return page that contains [statusId], null if that [statusId] is not in the cache */
    fun getPageById(statusId: String?) = idToPage[statusId]

    /** @return page after the page that has the given [nextKey] value */
    fun getNextPage(nextKey: String?): Page? {
        return synchronized(pages) {
            val index = pages.indexOfFirst { it.nextKey == nextKey }.takeIf { it != -1 } ?: return null
            pages.getOrNull(index + 1)
        }
    }

    /** @return page before the page that has the given [prevKey] value */
    fun getPrevPage(prevKey: String?): Page? {
        return synchronized(pages) {
            val index = pages.indexOfFirst { it.prevKey == prevKey }.takeIf { it != -1 } ?: return null
            pages.getOrNull(index - 1)
        }
    }

    /** @return true if the page cache is empty */
    fun isEmpty() = idToPage.isEmpty()

    /** Clear the cache */
    fun clear() {
        idToPage.clear()
        pages.clear()
    }

    /** @return the number of **items** in the pages **before** the page identified by [prevKey] */
    fun itemsBefore(prevKey: String?): Int {
        prevKey ?: return 0

        val index = pages.indexOfFirst { it.prevKey == prevKey }
        if (index <= 0) return 0

        return pages.subList(0, index).fold(0) { sum, page -> sum + page.data.size }
    }

    /**
     * @return the number of **items** in the pages **after** the page identified by [nextKey]
     */
    fun itemsAfter(nextKey: String?): Int {
        nextKey ?: return 0
        val index = pages.indexOfFirst { it.nextKey == nextKey }
        if (index == -1 || index == pages.size) return 0

        return pages.subList(index + 1, pages.size).fold(0) { sum, page -> sum + page.data.size }
    }

    /** Logs debug information when [BuildConfig.DEBUG] is true */
    fun debug() {
        if (!BuildConfig.DEBUG) return

        Timber.d("Page cache state:")
        if (idToPage.isEmpty()) {
            Timber.d("  ** empty **")
            return
        }

        idToPage.values.groupBy { it.prevKey }.values.forEachIndexed { index, pages ->
            Timber.d("  %d: %s", index, pages.first())
        }
    }
}
