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
import app.pachli.BuildConfig
import app.pachli.core.model.Status
import app.pachli.core.network.model.Links
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import java.util.LinkedList
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/** A page of data from the Mastodon API */
// This is the external representation that consumers of the cache use.
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
    override fun toString() = "size: ${"%2d".format(data.size)}, range: ${data.firstOrNull()?.statusId}..${data.lastOrNull()?.statusId}, prevKey: $prevKey, nextKey: $nextKey"

    companion object {
        fun tryFrom(response: ApiResult<List<app.pachli.core.network.model.Status>>): Result<Page, ApiError> = response.map {
            val links = Links.from(it.headers["link"])
            Timber.d("  link: %s", links)
            Timber.d("  %d - # statuses loaded", it.body.size)

            Page(
                data = it.body.asModel().toMutableList(),
                nextKey = links.next,
                prevKey = links.prev,
            )
        }
    }
}

/**
 * Internal representation of a page.
 *
 * @property data List of server IDs of statuses in this page, in presentation order.
 * @property prevKey See [Page.prevKey]
 * @property nextKey See [Page.nextKey].
 */
@VisibleForTesting
data class InternalPage(
    val data: List<String>,
    val prevKey: String? = null,
    val nextKey: String? = null,
)

/**
 * Cache of pages from the Mastodon API.
 *
 * Add pages to the cache with [add], [prepend], or [append].
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
 * val previousPage = pageCache.getPrevPage(page.prevKey)
 * ```
 *
 * If you have a page and want to get the immediately next (older) page use
 * [getNextPage] passing the `nextKey` of the original page.
 *
 * ```kotlin
 * val page = pageCache.getPageById("some_id")
 * val nextPage = pageCache.getNextPage(page.nextKey)
 * ```
 *
 * # Mutex
 *
 * [PageCache] keeps an internal [Mutex] to ensure operations on the underlying
 * data is synchronised across threads and coroutines. Most methods on this class
 * should be called with that mutex locked. In debug builds calling these methods
 * when the mutex is unlocked will throw [IllegalStateException].
 *
 * [PageCache] implements [Mutex] so the easiest way to do this is to use
 * [Mutex.withLock][kotlinx.coroutines.sync.withLock] like this:
 *
 * ```kotlin
 * val cache = PageCache()
 * val page = getPage()
 * cache.withLock {
 *     cache.clear()
 *     cache.add(page)
 * }
 * ```
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
//
// Further, we need to be able to mutate the cached data to reflect user actions, such
// showing a filtered status, or showing a filtered *quoted* status.
//
// To achieve this the cache contents are split over several data structures.
//
// `pages` is the distinct pages. Each status in a page is represented by its ID.
//
// `statuses` map is every referenced status. This includes the top-level statuses
// in each page, as well as every quoted status. This allows the mutation of a
// quoted status.
//
//
class PageCache private constructor(private val mutex: Mutex) : Mutex by mutex {
    constructor() : this(Mutex())

    /** Map from item identifier (e.g,. status ID) to the page that contains this item */
    @VisibleForTesting
    val idToPage = mutableMapOf<String, InternalPage>()

    /**
     * List of all pages, in display order. Pages at the front of the list are
     * newer results from the API and are displayed first.
     */
    private val pages = LinkedList<InternalPage>()

    /**
     * Every status in the page, identified by its ID.
     *
     * This has an entry for every top-level status in the page, and an entry for
     * every quoted status in the page.
     */
    val statuses = mutableMapOf<String, Status>()

    /**
     * Map from the server ID of a quoted status to the server IDs of the one or
     * more statuses that quote this.
     *
     * This allows updates of a quoted status (e.g., changing its filter state)
     * to be propogated to the statuses that quote it.
     */
    private val quoteToQuoters = mutableMapOf<String, Set<String>>()

    /** The first page in the cache (i.e., the top / newest entry in the timeline) */
    val firstPage: Page?
        get() = pages.firstOrNull()?.asPage()

    /** The last page in the cache (i.e., the bottom / oldest entry in the timeline) */
    val lastPage: Page?
        get() = pages.lastOrNull()?.asPage()

    /** The size of the cache, in pages. */
    val size
        get() = pages.size

    fun itemCount() = pages.fold(0) { sum, page -> sum + page.data.size }

    /**
     * Converts an [InternalPage] to an external [Page].
     *
     * Populates the returned page with the most recent content for each status from
     * [statuses].
     */
    private fun InternalPage.asPage() = Page(
        data = data.mapNotNull { statuses[it] }.toMutableList(),
        nextKey = nextKey,
        prevKey = prevKey,
    )

    /**
     * Converts an external [Page] to an [InternalPage].
     *
     * Populates the internal data structures as necessary.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    private fun Page.asInternalPage(): InternalPage {
        assertLocked()

        data.forEach { status ->
            statuses.putIfAbsent(status.statusId, status)
            statuses.putIfAbsent(status.actionableId, status.actionableStatus)

            // If this contains a full quote then add it to `statuses` and
            // update `quoteToQuoters`.
            (status.quote as? Status.Quote.FullQuote?)?.let { quote ->
                statuses.putIfAbsent(quote.statusId, quote.status)
                statuses.putIfAbsent(quote.status.actionableId, quote.status.actionableStatus)

                quoteToQuoters.merge(quote.statusId, setOf(status.statusId, status.actionableId)) { oldValue, value ->
                    oldValue + value
                }
            }
        }

        val internalPage = InternalPage(
            data = data.map { it.statusId },
            nextKey = nextKey,
            prevKey = prevKey,
        )

        internalPage.data.forEach { statusId ->
            // There should never be duplicate items across all pages. Enforce this in debug mode
            if (BuildConfig.DEBUG) {
                if (idToPage.containsKey(statusId)) {
                    debug()
                    // Downgraded to a log rather than a throw, because Mastodon servers
                    // can break this contract, see https://github.com/mastodon/mastodon/issues/30172.
                    Timber.wtf("Duplicate item ID $statusId in idToPage")
//                    throw IllegalStateException("Duplicate item ID ${statusId} in idToPage")
                }
            }
            idToPage[statusId] = internalPage
        }

        return internalPage
    }

    private fun assertLocked() {
        if (BuildConfig.DEBUG) {
            if (!mutex.isLocked) {
                throw IllegalStateException("Call to function that requires locked mutex")
            }
        }
    }

    /**
     * Clears the cache.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun clear() {
        assertLocked()
        pages.clear()
        idToPage.clear()
        statuses.clear()
        quoteToQuoters.clear()
    }

    /**
     * Adds [page] to the cache.
     *
     * Does not clear the cache first, use [clear] for that.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun add(page: Page) {
        assertLocked()
        pages.add(page.asInternalPage())
    }

    /**
     * Prepends [page] to the cache.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun prepend(page: Page) {
        assertLocked()
        pages.addFirst(page.asInternalPage())
    }

    /**
     *  Appends [page] to the cache.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun append(page: Page) {
        assertLocked()
        pages.addLast(page.asInternalPage())
    }

    /** @return page that contains [statusId], null if that [statusId] is not in the cache */
    fun getPageById(statusId: String?) = idToPage[statusId]?.asPage()

    /**
     * @return page after the page that has the given [nextKey] value.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun getNextPage(nextKey: String?): Page? {
        assertLocked()
        val index = pages.indexOfFirst { it.nextKey == nextKey }.takeIf { it != -1 } ?: return null
        return pages.getOrNull(index + 1)?.asPage()
    }

    /**
     * @return page before the page that has the given [prevKey] value.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun getPrevPage(prevKey: String?): Page? {
        assertLocked()
        val index = pages.indexOfFirst { it.prevKey == prevKey }.takeIf { it != -1 } ?: return null
        return pages.getOrNull(index - 1)?.asPage()
    }

    /** @return true if the page cache is empty */
    fun isEmpty() = pages.isEmpty()

    /**
     * Finds [statusId] in the cache and calls [updater] on the status (if found),
     * saves the result back to the cache.
     *
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun updateStatusById(statusId: String, updater: (Status) -> Status) {
        assertLocked()

        statuses.computeIfPresent(statusId) { statusId, status -> updater(status) }

        // If the status being updated is quoted by other statuses then propogate the
        // change to the quoting statuses.
        quoteToQuoters[statusId]?.let { quoters ->
            val updatedQuote = statuses[statusId] ?: return

            quoters.forEach { quoterId ->
                statuses.computeIfPresent(quoterId) { id, status ->
                    status.copy(
                        quote = Status.Quote.FullQuote(
                            state = status.quote!!.state,
                            status = updatedQuote,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Finds [statusId] in the cache and calls [updater] on the *actionable* status
     * for that status (if found), saves the result back to the cache.
     *
     * Note: [statusId] is **not** the ID of the actionable status, it's the ID
     * of the status that (possibly) contains it.
     *
     * @param statusId
     * @param updater Function to call, receives a copy of the actionable status
     * and returns the modified version.
     * @throws IllegalStateException if the cache is unlocked.
     */
    fun updateActionableStatusById(statusId: String, updater: (Status) -> Status) {
        assertLocked()
        statuses.computeIfPresent(statusId) { statusId, status ->
            updater(status.actionableStatus)
        }

        // If the status being updated is quoted by other statuses then propogate the
        // change to the quoting statuses.
        quoteToQuoters[statusId]?.let { quoters ->
            val updatedQuote = statuses[statusId] ?: return

            quoters.forEach { quoterId ->
                statuses.computeIfPresent(quoterId) { id, status ->
                    status.copy(
                        quote = Status.Quote.FullQuote(
                            state = status.quote!!.state,
                            status = updatedQuote,
                        ),
                    )
                }
            }
        }
    }

    /** Logs debug information when [BuildConfig.DEBUG] is true */
    fun debug() {
        if (!BuildConfig.DEBUG) return

        Timber.d("Page cache state:")
        if (idToPage.isEmpty()) {
            Timber.d("  ** empty **")
            return
        }

        pages.forEachIndexed { index, page ->
            Timber.d("  %d: %s", index, page)
        }
    }
}
