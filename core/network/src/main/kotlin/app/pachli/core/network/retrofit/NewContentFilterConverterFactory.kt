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

package app.pachli.core.network.retrofit

import app.pachli.core.model.NewContentFilter
import app.pachli.core.network.json.EnumConstantConverterFactory
import java.lang.reflect.Type
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * Converts a [NewContentFilter] to a request body.
 *
 * Should be added to Retrofit using [addConverterFactory][retrofit2.Retrofit.Builder.addConverterFactory].
 */
// Retrofit can't do this natively because it can't handle fields like "keywords_attributes"
// that repeat multiple times with the same names.
object NewContentFilterConverterFactory : Converter.Factory() {
    object NewContentFilterConverter : Converter<NewContentFilter, RequestBody> {
        private val enumConverter = EnumConstantConverterFactory.EnumConstantConverter
        private val utf8 = StandardCharsets.UTF_8.toString()

        override fun convert(newContentFilter: NewContentFilter): RequestBody {
            return buildList {
                add("title=${encode(newContentFilter.title)}")

                newContentFilter.contexts.forEach {
                    add("context[]=${encode(enumConverter.convert(it))}")
                }

                add("filter_action=${encode(enumConverter.convert(newContentFilter.filterAction))}")

                if (newContentFilter.expiresIn != 0) {
                    add("expires_in=${newContentFilter.expiresIn}")
                }

                newContentFilter.keywords.forEach {
                    add("keywords_attributes[][keyword]=${encode(it.keyword)}")
                    add("keywords_attributes[][whole_word]=${it.wholeWord}")
                }
            }.joinToString("&").toRequestBody()
        }

        /** @return URL encoded [s] using UTF8 as the character encoding. */
        private fun encode(s: String) = URLEncoder.encode(s, utf8)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? {
        return if (type == NewContentFilter::class.java) NewContentFilterConverter else null
    }
}
