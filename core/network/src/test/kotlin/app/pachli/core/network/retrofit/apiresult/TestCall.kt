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

package app.pachli.core.network.retrofit.apiresult

import java.io.InterruptedIOException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TestCall<T> : Call<T> {
    private var executed = false
    private var canceled = false
    private var callback: Callback<T>? = null

    /**
     * Request. Includes a path and query parameter to test that these are shown
     * correctly in error messages.
     */
    private var request = Request.Builder().url("https://example.com/foo?x=1").build()

    fun completeWithException(t: Throwable) {
        synchronized(this) {
            callback?.onFailure(this, t)
        }
    }

    fun complete(response: Response<T>) {
        synchronized(this) {
            callback?.onResponse(this, response)
        }
    }

    override fun enqueue(callback: Callback<T>) {
        synchronized(this) {
            this.callback = callback
        }
    }

    override fun isExecuted(): Boolean = synchronized(this) { executed }

    override fun isCanceled(): Boolean = synchronized(this) { canceled }

    override fun clone(): TestCall<T> = TestCall()

    override fun cancel() {
        synchronized(this) {
            if (canceled) return
            canceled = true

            val exception = InterruptedIOException("canceled")
            callback?.onFailure(this, exception)
        }
    }

    override fun execute(): Response<T> {
        throw UnsupportedOperationException("TestCall does not support synchronous execution")
    }

    override fun request(): Request = request

    override fun timeout(): Timeout = Timeout()
}
