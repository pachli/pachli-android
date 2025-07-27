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

package app.pachli.core.ui.extensions

import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.model.Role
import app.pachli.core.ui.RoleChip
import com.google.android.material.chip.ChipGroup

/**
 * Clears chips from this [ChipGroup], sets them to [roles], and shows
 * the group.
 *
 * Hides the group if [roles] is empty.
 */
fun ChipGroup.setRoles(roles: List<Role>) {
    removeAllViews()
    if (roles.isEmpty()) {
        hide()
        return
    }

    roles.forEach { role ->
        val roleChip = RoleChip(context).apply { this.role = role.name }
        addView(roleChip)
    }
    show()
}
