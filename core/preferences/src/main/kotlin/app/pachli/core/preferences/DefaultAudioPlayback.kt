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

package app.pachli.core.preferences

/** Where to save downloaded files. */
enum class DefaultAudioPlayback(override val displayResource: Int, override val value: String? = null) :
    PreferenceEnum {
    /** Play audio/video unmuted by default. */
    UNMUTED(R.string.pref_default_audio_playback_option_unmuted),

    /** Play audio/video muted by default. */
    MUTED(R.string.pref_default_audio_playback_option_muted),
}
