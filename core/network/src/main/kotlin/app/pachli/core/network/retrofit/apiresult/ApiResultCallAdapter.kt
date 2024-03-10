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

import java.lang.reflect.Type
import retrofit2.Call
import retrofit2.CallAdapter

internal class ApiResultCallAdapter<R : Any>(
    private val successType: Type,
) : CallAdapter<R, Call<ApiResult<R>>> {
    override fun responseType(): Type = successType

    override fun adapt(call: Call<R>): Call<ApiResult<R>> {
        return ApiResultCall(call, successType)
    }
}
