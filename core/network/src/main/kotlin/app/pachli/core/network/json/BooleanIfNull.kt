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

/**
 * A [JsonQualifier] for use with [Boolean] properties to indicate that their
 * value be set to the given [value] if the JSON property is `null`.
 *
 * Absent properties use the property's default value as normal.
 *
 * Usage:
 * ```
 * val moshi = Moshi.Builder()
 *   .add(BooleanIfNull.Factory())
 *   .build()
 *
 * @JsonClass(generateAdapter = true)
 * data class Foo(
 *   @BooleanIfNull(false) val data: Boolean
 * )
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class BooleanIfNull(val value: Boolean) {
    class Factory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            val delegateAnnotations = Types.nextAnnotations(
                annotations,
                BooleanIfNull::class.java,
            ) ?: return null
            val delegate = moshi.nextAdapter<Any>(
                this,
                type,
                delegateAnnotations,
            )

            val annotation = annotations.first { it is BooleanIfNull } as BooleanIfNull
            return Adapter(delegate, annotation.value)
        }

        private class Adapter(private val delegate: JsonAdapter<Any>, val default: Boolean) : JsonAdapter<Any>() {
            override fun fromJson(reader: JsonReader): Any {
                val value = reader.readJsonValue()
                return value as? Boolean ?: default
            }

            override fun toJson(writer: JsonWriter, value: Any?) = delegate.toJson(writer, value)
        }
    }
}
