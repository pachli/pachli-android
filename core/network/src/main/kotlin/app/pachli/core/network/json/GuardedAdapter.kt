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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * Deserialize this field as the given type, or null if the field value is not
 * this type.
 */
@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Guarded {
    class Factory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            val delegateAnnotations = Types.nextAnnotations(
                annotations,
                Guarded::class.java,
            ) ?: return null
            val delegate = moshi.nextAdapter<Any>(
                this,
                type,
                delegateAnnotations,
            )
            return GuardedAdapter(delegate)
        }

        private class GuardedAdapter(private val delegate: JsonAdapter<*>) : JsonAdapter<Any>() {
            override fun fromJson(reader: JsonReader): Any? {
                val peeked = reader.peekJson()
                val result = try {
                    delegate.fromJson(peeked)
                } catch (_: JsonDataException) {
                    null
                } finally {
                    peeked.close()
                }
                reader.skipValue()
                return result
            }

            override fun toJson(writer: JsonWriter, value: Any?) {
                throw UnsupportedOperationException("@Guarded is only used to desererialize objects")
            }
        }
    }
}
