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

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlin.test.fail
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class HasDefaultTest {
    private val moshi = Moshi.Builder()
        .add(HasDefault.Factory())
        .build()

    enum class NoAnnotation { FOO, BAR }

    @HasDefault
    enum class Annotated {
        @Default
        FOO,
        BAR,
    }

    @Test
    fun `unannotated enum parses`() {
        assertThat(moshi.adapter<NoAnnotation>().fromJson("\"FOO\"")).isEqualTo(NoAnnotation.FOO)
    }

    @Test
    fun `unannotated enum throws an exception on unrecognised value`() {
        try {
            moshi.adapter<NoAnnotation>().fromJson("\"UNKNOWN\"")
            fail()
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(JsonDataException::class.java)
        }
    }

    @Test
    fun `annotated enum parses as normal`() {
        val adapter = moshi.adapter<Annotated>()
        assertThat(adapter.fromJson("\"FOO\"")).isEqualTo(Annotated.FOO)
        assertThat(adapter.fromJson("\"BAR\"")).isEqualTo(Annotated.BAR)
    }

    @Test
    fun `annotated enum with unknown value parses as FOO`() {
        val adapter = moshi.adapter<Annotated>()
        assertThat(adapter.fromJson("\"unknown\"")).isEqualTo(Annotated.FOO)
    }

    @Test
    fun `annotated enum emits correct JSON for valid values`() {
        val adapter = moshi.adapter<Annotated>()
        assertThat(adapter.toJson(Annotated.FOO)).isEqualTo("\"FOO\"")
        assertThat(adapter.toJson(Annotated.BAR)).isEqualTo("\"BAR\"")
    }

    @JsonClass(generateAdapter = true)
    data class Data(@Json(name = "some_enum") val someEnum: Annotated)

    @Test
    fun `annotated enum as property of class with unknown value parses as FOO`() {
        val adapter = moshi.adapter<Data>()
        assertThat(adapter.fromJson("{ \"some_enum\": \"unknown\" }")).isEqualTo(
            Data(someEnum = Annotated.FOO),
        )
    }

    // This definition has @HasDefault but no @Default constant, so should error
    @HasDefault
    enum class MissingDefaultAnnotation {
        FOO,
        BAR,
    }

    @Test
    fun `unannotated enum constant throws exception when creating adapter`() {
        try {
            assertThat(moshi.adapter<MissingDefaultAnnotation>().fromJson("\"FOO\"")).isEqualTo(
                MissingDefaultAnnotation.FOO,
            )
            fail()
        } catch (e: Error) {
            assertThat(e).isInstanceOf(AssertionError::class.java)
            assertThat(e.message).contains("Missing @Default")
        }
    }
}
