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

package app.pachli.components.notifications

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import app.pachli.R
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.network.model.Notification
import app.pachli.util.setDrawableTint

/**
 * @return An icon for the given [Notification.Type], appropriately coloured.
 */
fun NotificationEntity.Type.icon(context: Context) = when (this) {
    NotificationEntity.Type.UNKNOWN -> getIconWithColor(
        context,
        R.drawable.ic_home_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.MENTION -> getIconWithColor(
        context,
        R.drawable.ic_mention_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.REBLOG -> getIconWithColor(
        context,
        R.drawable.ic_repeat_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.FAVOURITE -> getIconWithColor(
        context,
        R.drawable.ic_star_24dp,
        app.pachli.core.designsystem.R.attr.favoriteIconColor,
    )

    NotificationEntity.Type.FOLLOW -> getIconWithColor(
        context,
        app.pachli.core.ui.R.drawable.ic_person_add_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.FOLLOW_REQUEST -> getIconWithColor(
        context,
        app.pachli.core.ui.R.drawable.ic_person_add_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.POLL -> getIconWithColor(
        context,
        R.drawable.ic_poll_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.STATUS -> getIconWithColor(
        context,
        R.drawable.ic_home_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    NotificationEntity.Type.UPDATE -> getIconWithColor(
        context,
        R.drawable.ic_edit_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )
    else -> getIconWithColor(
        context,
        R.drawable.ic_home_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )
}

private fun getIconWithColor(
    context: Context,
    @DrawableRes drawable: Int,
    @AttrRes color: Int,
): Drawable? {
    return AppCompatResources.getDrawable(context, drawable)?.apply {
        setDrawableTint(context, this, color)
    }
}
