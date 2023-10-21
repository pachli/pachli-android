/* Copyright 2018 Conny Duck
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import app.pachli.components.account.AccountActivity
import app.pachli.components.viewthread.ViewThreadActivity
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.looksLikeMastodonUrl
import app.pachli.util.openLink
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import javax.inject.Inject

/** this is the base class for all activities that open links
 *  links are checked against the api if they are mastodon links so they can be opened in Tusky
 *  Subclasses must have a bottom sheet with Id item_status_bottom_sheet in their layout hierarchy
 */

abstract class BottomSheetActivity : BaseActivity() {

    lateinit var bottomSheet: BottomSheetBehavior<LinearLayout>
    var searchUrl: String? = null

    @Inject
    lateinit var mastodonApi: MastodonApi

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val bottomSheetLayout: LinearLayout = findViewById(R.id.item_status_bottom_sheet)
        bottomSheet = BottomSheetBehavior.from(bottomSheetLayout)
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        cancelActiveSearch()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            },
        )
    }

    open fun viewUrl(url: String, lookupFallbackBehavior: PostLookupFallbackBehavior = PostLookupFallbackBehavior.OPEN_IN_BROWSER) {
        if (!looksLikeMastodonUrl(url)) {
            openLink(url)
            return
        }

        onBeginSearch(url)

        lifecycleScope.launch {
            mastodonApi.search(query = url, resolve = true).fold({ searchResult ->
                val (accounts, statuses) = searchResult
                if (getCancelSearchRequested(url)) return@fold
                onEndSearch(url)

                statuses.firstOrNull()?.let {
                    viewThread(it.id, it.url)
                    return@fold
                }

                // Some servers return (unrelated) accounts for url searches (#2804)
                // Verify that the account's url matches the query
                accounts.firstOrNull { it.url.equals(url, ignoreCase = true) }?.let {
                    viewAccount(it.id)
                    return@fold
                }

                performUrlFallbackAction(url, lookupFallbackBehavior)
            }, {
                if (!getCancelSearchRequested(url)) {
                    onEndSearch(url)
                    performUrlFallbackAction(url, lookupFallbackBehavior)
                }
            },)
        }
    }

    open fun viewThread(statusId: String, url: String?) {
        if (!isSearching()) {
            val intent = Intent(this, ViewThreadActivity::class.java)
            intent.putExtra("id", statusId)
            intent.putExtra("url", url)
            startActivityWithSlideInAnimation(intent)
        }
    }

    open fun viewAccount(id: String) {
        val intent = AccountActivity.getIntent(this, id)
        startActivityWithSlideInAnimation(intent)
    }

    protected open fun performUrlFallbackAction(url: String, fallbackBehavior: PostLookupFallbackBehavior) {
        when (fallbackBehavior) {
            PostLookupFallbackBehavior.OPEN_IN_BROWSER -> openLink(url)
            PostLookupFallbackBehavior.DISPLAY_ERROR -> Toast.makeText(this, getString(R.string.post_lookup_error_format, url), Toast.LENGTH_SHORT).show()
        }
    }

    @VisibleForTesting
    fun onBeginSearch(url: String) {
        searchUrl = url
        showQuerySheet()
    }

    @VisibleForTesting
    fun getCancelSearchRequested(url: String) = url != searchUrl

    @VisibleForTesting
    fun isSearching() = searchUrl != null

    @VisibleForTesting
    fun onEndSearch(url: String?) {
        if (url == searchUrl) {
            // Don't clear query if there's no match,
            // since we might just now be getting the response for a canceled search
            searchUrl = null
            hideQuerySheet()
        }
    }

    @VisibleForTesting
    fun cancelActiveSearch() {
        if (isSearching()) {
            onEndSearch(searchUrl)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun openLink(url: String) {
        (this as Context).openLink(url)
    }

    private fun showQuerySheet() {
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideQuerySheet() {
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
    }
}

enum class PostLookupFallbackBehavior {
    OPEN_IN_BROWSER,
    DISPLAY_ERROR,
}
