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

class InstantJsonAdapter : JsonAdapter<Instant?>() {
    @FromJson
    override fun fromJson(reader: JsonReader): Instant? {
        if (reader.peek() == NULL) {
            return reader.nextNull()
        }
        val string = reader.nextString()
        return Instant.parse(string)
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
