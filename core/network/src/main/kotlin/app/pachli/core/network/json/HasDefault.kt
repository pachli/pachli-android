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
 * Marks the enum constant to use as the default value if the parsed JSON
 * contains an invalid value.
 *
 * The `enum class` must be annotated with [HasDefault].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Default

/**
 * A [JsonQualifier] for use with [Enum] declarations to indicate that incoming
 * JSON values that are not valid enum constants should be mapped to a default
 * value instead of throwing a [JsonDataException].
 *
 * Usage:
 * ```
 * val moshi = Moshi.Builder()
 *     .add(HasDefault.Factory())
 *     .build()
 *
 * @HasDefault
 * enum class SomeEnum {
 *     @Default
 *     FOO,
 *     BAR
 * }
 *
 * @JsonClass(generateAdapter = true)
 * data class Data(
 *     @Json(name = "some_enum") someEnum: SomeEnum
 * )
 * ```
 *
 * JSON of the form `{ "some_enum": "unknown" }` will parse to a
 *
 * ```
 * Data(someEnum = SomeEnum.FOO)
 * ```
 *
 * because `SomeEnum.FOO` has the `@Default` annotation. Move it to another constant
 * to change it.
 *
 * This is similar to Moshi's existing [com.squareup.moshi.adapters.EnumJsonAdapter]
 * which also supports fallbacks. The primary difference is that you define the
 * default value at the point where the `enum class` is declared, not at the point
 * where the Moshi instance is created.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@JsonQualifier
annotation class HasDefault() {
    class Factory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            val rawType = Types.getRawType(type)
            if (!rawType.isEnum) return null

            rawType.getAnnotation(HasDefault::class.java) ?: return null

            val delegateAnnotations = Types.nextAnnotations(
                annotations,
                HasDefault::class.java,
            ) ?: emptySet()

            val delegate = moshi.nextAdapter<Any>(
                this,
                type,
                delegateAnnotations,
            )

            val enumType = rawType as Class<out Enum<*>>

            val default = enumType.enumConstants.firstOrNull {
                it::class.java.getField(it.name).getAnnotation(Default::class.java) != null
            } ?: throw AssertionError("Missing @Default on ${enumType.name}")

            return Adapter(delegate, default)
        }

        private class Adapter<T : Enum<T>>(
            private val delegate: JsonAdapter<Any>,
            val default: Enum<*>,
        ) : JsonAdapter<T>() {
            override fun fromJson(reader: JsonReader): T {
                val peeked = reader.peekJson()
                val result = try {
                    delegate.fromJson(peeked) as T
                } catch (_: JsonDataException) {
                    default
                } finally {
                    peeked.close()
                }
                reader.skipValue()
                return result as T
            }

            override fun toJson(writer: JsonWriter, value: T?) = delegate.toJson(writer, value)
        }
    }
}
