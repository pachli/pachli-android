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

package app.pachli.core.network.json

import com.squareup.moshi.Json
import java.lang.reflect.Type
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * Retrofit [Converter.Factory] that converts enum constants to strings using the
 * value of any `@Json(name = ...)` annotation on the constant, falling back to
 * the enum's name if the annotation is not present.
 *
 * This ensures that the same string is used for an enum constant's value whether
 * it is sent/received as JSON or sent as a [retrofit2.http.FormUrlEncoded] value.
 *
 * To install in Retrofit call `.addConverterFactory(EnumConstantConverterFactory)`
 * on the Retrofit builder.
 */
object EnumConstantConverterFactory : Converter.Factory() {
    object EnumConstantConverter : Converter<Enum<*>, String> {
        override fun convert(enum: Enum<*>): String {
            return try {
                enum.javaClass.getField(enum.name).getAnnotation(Json::class.java)?.name
            } catch (_: Exception) {
                null
            } ?: enum.toString()
        }
    }

    override fun stringConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<Enum<*>, String>? {
        return if (type is Class<*> && type.isEnum) EnumConstantConverter else null
    }
}
