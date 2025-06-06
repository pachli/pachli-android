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

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token.NULL
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException

class InstantJsonAdapter : JsonAdapter<Instant?>() {
    var fmt: DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_DATE_TIME)
        .optionalStart().appendOffsetId()
        .toFormatter().withZone(ZoneOffset.UTC)

    /**
     * Parse an [Instant] from JSON.
     *
     * Handles the case where the incoming JSON date might not include the
     * timezone (missing from some buggy servers). Fallback to parsing as
     * [LocalDateTime] and then converting that to an [Instant].
     */
    @FromJson
    override fun fromJson(reader: JsonReader): Instant? {
        if (reader.peek() == NULL) {
            return reader.nextNull()
        }
        val string = reader.nextString()
        val parsed = fmt.parseBest(string, Instant::from, LocalDateTime::from)
        return when (parsed) {
            is Instant -> parsed
            is LocalDateTime -> Instant.from(parsed)
            else -> {
                // Shouldn't happen, parseBest should already have thrown rather
                // than return an unexpected class.
                throw (DateTimeParseException("unexpected class from parseBest, $parsed", string, 0))
            }
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Instant?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.jsonValue(value.toString())
        }
    }
}
