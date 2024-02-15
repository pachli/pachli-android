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

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class DefaultIfNull

class DefaultIfNullAdapter(private val delegate: JsonAdapter<Any>) : JsonAdapter<Any>() {
    override fun fromJson(reader: JsonReader): Any? {
        val value = reader.readJsonValue()
        if (value is Map<*, *>) {
            val withoutNulls = value.filterValues { it != null }
            return delegate.fromJsonValue(withoutNulls)
        }
        return delegate.fromJsonValue(value)
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
        return delegate.toJson(writer, value)
    }

    companion object {
        class DefaultIfNullAdapterFactory : Factory {
            override fun create(
                type: Type,
                annotations: MutableSet<out Annotation>,
                moshi: Moshi,
            ): JsonAdapter<*>? {
                val delegateAnnotations = Types.nextAnnotations(
                    annotations,
                    DefaultIfNull::class.java,
                ) ?: return null
                val delegate = moshi.nextAdapter<Any>(
                    this,
                    type,
                    delegateAnnotations,
                )
                return DefaultIfNullAdapter(delegate)
            }
        }
    }
}
