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

package app.pachli.core.network.extensions

import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Checks if this throwable indicates an error causes by a 4xx/5xx server response and
 * tries to retrieve the error message the server sent.
 *
 * @return the error message, or null if this is no server error or it had no error message
 */
fun Throwable.getServerErrorMessage(): String? {
    if (this !is HttpException) return null

    response()?.headers()?.get("content-type")?.startsWith("application/json") == true ||
        return null

    // Try and parse the body as JSON with `error` and an optional `description`
    // property.
    return response()?.errorBody()?.string()?.let { errorBody ->
        if (errorBody.isBlank()) return null

        val errorObj = try {
            JSONObject(errorBody)
        } catch (e: JSONException) {
            return@let "$errorBody ($e)"
        }

        val error = errorObj.optString("error")
        val description = errorObj.optString("description")

        if (error.isNullOrEmpty()) return null
        if (description.isNullOrEmpty()) return error

        return "$error: $description"
    }
}
