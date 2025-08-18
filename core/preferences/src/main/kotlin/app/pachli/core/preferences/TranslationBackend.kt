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

/** Which backend to use for translation. */
enum class TranslationBackend(override val displayResource: Int, override val value: String? = null) :
    PreferenceEnum {
    /** Only translate using the user's server. */
    SERVER_ONLY(R.string.pref_translation_backend_option_server_only),

    /** Translate using the user's server first, if that fails try a local translation. */
    SERVER_FIRST(R.string.pref_translation_backend_option_server_first),

    /** Translate locally only. */
    LOCAL_ONLY(R.string.pref_translation_backend_option_local_only),
}
