/*
 * Copyright (c) 2026 Pachli Association
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

import android.os.Parcelable
import java.util.stream.IntStream
import kotlinx.parcelize.Parcelize

/**
 * Unique identifier for the user's account. Guaranteed unique irrespective of
 * the order the user logs in to their accounts.
 *
 * @property value Underlying string value for the identifier.
 * @constructor Creates a new [AccountIdentifier] from a string. Should not
 * normally be used, use the constructor that takes an [app.pachli.core.model.PachliAccount].
 */
// @Parcelize so this can be passed directly in an intent, instead of round
// tripping as a String.
@JvmInline
@Parcelize
value class AccountIdentifier internal constructor(val value: String) :
    CharSequence by value,
    Parcelable {
    /**
     * Creates a new [AccountIdentifier] from an [app.pachli.core.model.PachliAccount]. The
     * preferred way to construct an [AccountIdentifier], as it ensures the
     * value is constructed correctly.
     */
    constructor(pachliAccount: PachliAccount) : this("${pachliAccount.domain}:${pachliAccount.accountId}")

    override fun chars(): IntStream {
        return super.chars()
    }

    override fun codePoints(): IntStream {
        return super.codePoints()
    }

    override fun toString() = value

    companion object {
        /**
         * Unsafe way to construct an [AccountIdentifier]. It is the caller's
         * responsibility to ensure [value] is in the expected format.
         */
        fun unsafe(value: String) = AccountIdentifier(value)
    }
}
