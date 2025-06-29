/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

/** */
@JsonClass(generateAdapter = true)
data class MatchingFilter(
    val filterId: String,
    val title: String,
)

/**
 * The attachment filter decision, with the reason for the decision if
 * applicable.
 */
// TODO: AttachmentDisplayDecision is probably a better name, since whether
// to blur or not is a user preference.
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface AttachmentBlurDecision {
    /** The attachment should be shown. */
    @TypeLabel("none")
    data object None : AttachmentBlurDecision

    /**
     * The status containing the attachment matched one or more filter set to
     * "blur".
     *
     * @property filters The [MatchingFilter]s
     */
    @TypeLabel("filter")
    @JsonClass(generateAdapter = true)
    data class Filter(val filters: List<MatchingFilter>) : AttachmentBlurDecision

    /** The status containing the attachment is marked "sensitive". */
    @TypeLabel("sensitive")
//    data class Sensitive(val x: Int = 0) : AttachmentBlurDecision
    data object Sensitive : AttachmentBlurDecision

    /**
     * The attachment was originally blurred because of [original], the user has
     * overriden that to view the attachment anyway.
     */
    @TypeLabel("override")
    @JsonClass(generateAdapter = true)
    data class Override(val original: AttachmentBlurDecision) : AttachmentBlurDecision

    fun show(): Boolean = this is None || this is Override
}

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class SealedTest {
    @TypeLabel("default")
    data object Default : SealedTest()

    @TypeLabel("bar")
    @JsonClass(generateAdapter = true)
    data class Bar(val x: Int) : SealedTest()

    @TypeLabel("wrapper")
    @JsonClass(generateAdapter = true)
    data class Wrapper(val inner: SealedTest) : SealedTest()

//    @TypeLabel("baz")
//    object Baz : SealedTest()
}

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class SealedType2(val type: String) {
    @TypeLabel("void")
    data object VoidType : SealedType2("void")

    @TypeLabel("string")
    @JsonClass(generateAdapter = true)
    data class StringType(val s: String) : SealedType2("string")
}

// Works:
// @JsonClass(generateAdapter = true, generator = "sealed:type")
// sealed interface SealedType3 {
//    @TypeLabel("string")
//    @JsonClass(generateAdapter = true)
//    data class StringType(val s: String) : SealedType3
//
//    @TypeLabel("pointer")
//    @JsonClass(generateAdapter = true)
//    data class PointerType(val targetType: SealedType3) : SealedType3
// }

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class SealedType3(val type: String) {
    @TypeLabel("void")
    object VoidType : SealedType3("void")

    @TypeLabel("string")
    @JsonClass(generateAdapter = true)
    data class StringType(val s: String) : SealedType3("string")

    @TypeLabel("pointer")
    @JsonClass(generateAdapter = true)
    data class PointerType(val targetType: SealedType3) : SealedType3("pointer")
}
