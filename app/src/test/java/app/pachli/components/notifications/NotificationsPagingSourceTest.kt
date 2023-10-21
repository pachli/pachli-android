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

package app.pachli.components.notifications

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.core.network.retrofit.MastodonApi
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class NotificationsPagingSourceTest {
    @Test
    fun `load() returns error message on HTTP error`() = runTest {
        // Given
        val jsonError = "{error: 'This is an error'}".toResponseBody()
        val mockApi: MastodonApi = mock {
            onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(429, jsonError)
            onBlocking { notification(any()) } doReturn Response.error(429, jsonError)
        }

        val filter = emptySet<Notification.Type>()
        val gson = Gson()
        val pagingSource = NotificationsPagingSource(mockApi, gson, filter)
        val loadingParams = PagingSource.LoadParams.Refresh("0", 5, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertTrue(loadResult is PagingSource.LoadResult.Error)
        assertEquals(
            "HTTP 429: This is an error",
            (loadResult as PagingSource.LoadResult.Error).throwable.message,
        )
    }

    // As previous, but with `error_description` field as well.
    @Test
    fun `load() returns extended error message on HTTP error`() = runTest {
        // Given
        val jsonError = "{error: 'This is an error', error_description: 'Description of the error'}".toResponseBody()
        val mockApi: MastodonApi = mock {
            onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(429, jsonError)
            onBlocking { notification(any()) } doReturn Response.error(429, jsonError)
        }

        val filter = emptySet<Notification.Type>()
        val gson = Gson()
        val pagingSource = NotificationsPagingSource(mockApi, gson, filter)
        val loadingParams = PagingSource.LoadParams.Refresh("0", 5, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertTrue(loadResult is PagingSource.LoadResult.Error)
        assertEquals(
            "HTTP 429: This is an error: Description of the error",
            (loadResult as PagingSource.LoadResult.Error).throwable.message,
        )
    }

    // As previous, but no error JSON, so expect default response
    @Test
    fun `load() returns default error message on empty HTTP error`() = runTest {
        // Given
        val jsonError = "{}".toResponseBody()
        val mockApi: MastodonApi = mock {
            onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(429, jsonError)
            onBlocking { notification(any()) } doReturn Response.error(429, jsonError)
        }

        val filter = emptySet<Notification.Type>()
        val gson = Gson()
        val pagingSource = NotificationsPagingSource(mockApi, gson, filter)
        val loadingParams = PagingSource.LoadParams.Refresh("0", 5, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertTrue(loadResult is PagingSource.LoadResult.Error)
        assertEquals(
            "HTTP 429: no reason given",
            (loadResult as PagingSource.LoadResult.Error).throwable.message,
        )
    }

    // As previous, but malformed JSON, so expect response with enough information to troubleshoot
    @Test
    fun `load() returns useful error message on malformed HTTP error`() = runTest {
        // Given
        val jsonError = "{'malformedjson}".toResponseBody()
        val mockApi: MastodonApi = mock {
            onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(429, jsonError)
            onBlocking { notification(any()) } doReturn Response.error(429, jsonError)
        }

        val filter = emptySet<Notification.Type>()
        val gson = Gson()
        val pagingSource = NotificationsPagingSource(mockApi, gson, filter)
        val loadingParams = PagingSource.LoadParams.Refresh("0", 5, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertTrue(loadResult is PagingSource.LoadResult.Error)
        assertEquals(
            "HTTP 429: {'malformedjson} (com.google.gson.JsonSyntaxException: com.google.gson.stream.MalformedJsonException: Unterminated string at line 1 column 17 path \$.)",
            (loadResult as PagingSource.LoadResult.Error).throwable.message,
        )
    }
}
