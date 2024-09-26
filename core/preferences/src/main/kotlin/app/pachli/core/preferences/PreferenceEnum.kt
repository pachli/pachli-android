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

import androidx.annotation.StringRes

/**
 * Interface for enums that can be saved/restored from [SharedPreferencesRepository].
 */
interface PreferenceEnum {
    /** String resource for the enum's value. */
    @get:StringRes
    val displayResource: Int

    /**
     * The value to persist in [SharedPreferencesRepository].
     *
     * If null the enum's [name][Enum.name] property is used.
     */
    val value: String?

    companion object {
        /**
         * @return The enum identified by [s], or null if the enum does not have [s] as
         * a string representation.
         */
        inline fun <reified T : Enum<T>> from(s: String?): T? {
            s ?: return null

            return try {
                enumValueOf<T>(s)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
