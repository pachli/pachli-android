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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.model.Role
import app.pachli.core.ui.extensions.contentDescription
import com.google.android.material.chip.ChipGroup

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
    init {
        setChipIconResource(R.drawable.profile_role_badge)
    }

    fun bind(role: Role, domain: String? = null) {
        val sb = SpannableStringBuilder(role.name).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, role.name.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!domain.isNullOrBlank()) {
                append(" $domain")
            }
        }
        text = sb
        contentDescription = role.contentDescription(context, domain)
    }
}

/**
 * A [ChipGroup] for displaying zero or more [Role].
 *
 * Differs from [ChipGroup] by honouring the `android:importantForAccessibility`
 * attribute (see https://github.com/material-components/material-components-android/issues/4946)
 * and by providing [setRoles] to set the roles to show.
 */
class RoleChipGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ChipGroup(context, attrs) {

    init {
        attrs?.getAttributeIntValue(
            "http://schemas.android.com/apk/res/android",
            "importantForAccessibility",
            IMPORTANT_FOR_ACCESSIBILITY_YES,
        )?.let {
            importantForAccessibility = it
        }
    }

    /**
     * Clears chips from this [RoleChipGroup], sets them to [roles], and shows
     * the group.
     *
     * Hides the group if [roles] is empty.
     *
     * @param roles Roles to show.
     * @param domain Domain each role is for.
     */
    fun setRoles(roles: List<Role>, domain: String? = null) {
        removeAllViews()
        if (roles.isEmpty()) {
            hide()
            return
        }

        roles.forEach { role ->
            val roleChip = RoleChip(context).apply { bind(role, domain) }

            // Each chip should have the same accessibility importance as
            // the parent group. This allows them to be unimportant if
            // displayed as part of a status, but important (or default
            // behaviour) if displayed elsewhere (e.g., when viewing an
            // account).
            roleChip.importantForAccessibility = importantForAccessibility

            addView(roleChip)
        }
        show()
    }
}
