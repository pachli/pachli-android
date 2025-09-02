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

// Preferences:
// - Show colorful gradients for hidden media
// Account preferences
// - Download media previews
// - Always show sensitive content

// Decisions:
//
// - Show
// - Hide, because:
//   - Download media previews is off
//   - Media is sensitive? (what does web app do, what is current behaviour?)
//   - Matches filter marked "blur"
//   - User tapped the eye icon to hide it
//
// - Blur isn't a decision show/hide decision, it's a rendering decision. "Hide" might
// hide the media completely, or it might replace it with the blur.

// Filter preference is badly named, shouldn't be blur, should be "Hide media"

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface AttachmentDisplayReason {
    /**
     * The status containing the attachment matched one or more filters marked
     * "blur"
     *
     * @property filters The [MatchingFilter]s
     */
    @TypeLabel(label = "blurFilter")
    @JsonClass(generateAdapter = true)
    data class BlurFilter(val filters: List<MatchingFilter>) : AttachmentDisplayReason

    /** The status is marked sensitive. */
    @TypeLabel(label = "sensitive")
    @JsonClass(generateAdapter = true)
    class Sensitive : AttachmentDisplayReason

    /** The user hid the attachment using the UI. */
    @TypeLabel("userAction")
    @JsonClass(generateAdapter = true)
    class UserAction : AttachmentDisplayReason
}

/**
 * The attachment filter decision, with the reason for the decision if
 * applicable.
 */
// TODO: AttachmentDisplayDecision is probably a better name, since whether
// to blur or not is a user preference.
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface AttachmentBlurDecision {
    /**
     * The attachment should be shown.
     *
     * @property originalDecision If this is overriding the previous decision,
     * this records the previous decision. Null if not overridden.
     */
    @TypeLabel("show")
    @JsonClass(generateAdapter = true)
    data class Show(val originalDecision: AttachmentBlurDecision? = null) : AttachmentBlurDecision

    /**
     * The attachment should be hidden.
     *
     * @property reason The reason why this attachment should be hidden.
     */
    @TypeLabel("hide")
    @JsonClass(generateAdapter = true)
    data class Hide(val reason: AttachmentDisplayReason) : AttachmentBlurDecision
//    /** The attachment should be shown. */
//    @TypeLabel("none")
//    @JsonClass(generateAdapter = true)
//    class None : AttachmentBlurDecision
//
//    /**
//     * The status containing the attachment matched one or more filter set to
//     * "blur".
//     *
//     * @property filters The [MatchingFilter]s
//     */
//    @TypeLabel("filter")
//    @JsonClass(generateAdapter = true)
//    class Filter(val filters: List<MatchingFilter>) : AttachmentBlurDecision
//
//    /** The status containing the attachment is marked "sensitive". */
//    //    class Sensitive(val x: Int = 0) : AttachmentBlurDecision()
//    @TypeLabel("sensitive")
//    @JsonClass(generateAdapter = true)
//    class Sensitive : AttachmentBlurDecision
//
//    /**
//     * The attachment was originally blurred because of [original], the user has
//     * overriden that to view the attachment anyway.
//     */
//    @TypeLabel("override")
//    @JsonClass(generateAdapter = true)
//    class Override(val original: AttachmentBlurDecision) : AttachmentBlurDecision
//
//    /** True if the attachment can be shown. */
//    val show: Boolean
//        get() = this is None || this is Override
}
