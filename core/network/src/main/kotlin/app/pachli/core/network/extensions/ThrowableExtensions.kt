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
 * checks if this throwable indicates an error causes by a 4xx/5xx server response and
 * tries to retrieve the error message the server sent
 * @return the error message, or null if this is no server error or it had no error message
 */
fun Throwable.getServerErrorMessage(): String? {
    if (this is HttpException) {
        val errorResponse = response()?.errorBody()?.string()
        return if (!errorResponse.isNullOrBlank()) {
            try {
                JSONObject(errorResponse).getString("error")
            } catch (e: JSONException) {
                null
            }
        } else {
            null
        }
    }
    return null
}
