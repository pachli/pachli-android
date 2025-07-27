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

package app.pachli.core.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.StyleSpan
import android.util.AttributeSet

/**
 * A [ProfileChip] for showing an [app.pachli.core.model.Role].
 *
 * Do not use the [text] property to set the contents of the chip, set the
 * [role] and [domain] properties.
 *
 * The [role] is styled in bold, the [domain] (if present) is styled
 * normally.
 *
 * Example:
 *
 * ```kotlin
 * val roleChip = RoleChip(context).apply {
 *     role = "Staff",
 *     domain = "mastodon.social"
 * }
 * ```
 */
class RoleChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.chipStyle,
) : ProfileChip(context, attrs, defStyleAttr) {
    var role: String = ""
        set(value) {
            field = value
            updateText()
        }

    var domain: String = ""
        set(value) {
            field = value
            updateText()
        }

    init {
        setChipIconResource(R.drawable.profile_role_badge)
    }

    private fun updateText() {
        val sb = SpannableStringBuilder(role).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, role.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            if (domain.isNotBlank()) {
                append(" $domain")
            }
        }
        text = sb
    }
}
