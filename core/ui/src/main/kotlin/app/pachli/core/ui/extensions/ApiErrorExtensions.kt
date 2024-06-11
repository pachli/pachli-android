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

package app.pachli.core.ui.extensions

import android.content.Context
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.network.retrofit.apiresult.ApiError
import app.pachli.core.network.retrofit.apiresult.ApiError.Unknown
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.network.retrofit.apiresult.HttpError
import app.pachli.core.network.retrofit.apiresult.IO
import app.pachli.core.network.retrofit.apiresult.JsonParse
import app.pachli.core.network.retrofit.apiresult.NetworkError
import app.pachli.core.network.retrofit.apiresult.ServerError
import app.pachli.core.ui.R

/** @return Formatted error message for this [ApiError]. */
fun ApiError.fmt(context: Context) = when (this) {
    is HttpError -> when (this) {
        is ClientError.BadRequest -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ClientError.Gone -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ClientError.NotFound -> String.format(context.getString(R.string.error_404_not_found_fmt), cause.localizedMessage)
        is ClientError.Unauthorized -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ClientError.UnknownClientError -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ServerError.BadGateway -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ServerError.Internal -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ServerError.NotImplemented -> String.format(context.getString(R.string.error_404_not_found_fmt), cause.localizedMessage)
        is ServerError.ServiceUnavailable -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
        is ServerError.UnknownServerError -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
    }
    is JsonParse -> String.format(context.getString(R.string.error_json_data_fmt), cause.localizedMessage)
    is NetworkError -> when (this) {
        is IO -> String.format(context.getString(R.string.error_network_fmt), cause.localizedMessage)
    }
    is Unknown -> String.format(context.getString(R.string.error_generic_fmt), cause.localizedMessage)
}.unicodeWrap()
