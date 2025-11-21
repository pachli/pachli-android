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
import app.pachli.core.data.model.NotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowNotificationViewData
import app.pachli.core.data.model.NotificationViewData.FollowRequestNotificationViewData
import app.pachli.core.data.model.NotificationViewData.UnknownNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.FavouriteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.MentionNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.PollNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuoteNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.QuotedUpdateNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.ReblogNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.StatusNotificationViewData
import app.pachli.core.data.model.NotificationViewData.WithStatus.UpdateNotificationViewData
import app.pachli.util.setDrawableTint

/**
 * @return An icon for the given [NotificationViewData], appropriately coloured.
 */
fun NotificationViewData.icon(context: Context) = when (this) {
    is UnknownNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_home_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is MentionNotificationViewData -> getIconWithColor(
        context,
        app.pachli.core.ui.R.drawable.ic_mention_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is ReblogNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_repeat_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is FavouriteNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_star_24dp,
        app.pachli.core.designsystem.R.attr.favoriteIconColor,
    )

    is FollowNotificationViewData -> getIconWithColor(
        context,
        app.pachli.core.ui.R.drawable.ic_person_add_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is FollowRequestNotificationViewData -> getIconWithColor(
        context,
        app.pachli.core.ui.R.drawable.ic_person_add_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is PollNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_poll_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is StatusNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_home_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is UpdateNotificationViewData -> getIconWithColor(
        context,
        R.drawable.ic_edit_24dp,
        androidx.appcompat.R.attr.colorPrimary,
    )

    is QuoteNotificationViewData,
    is QuotedUpdateNotificationViewData,
    -> getIconWithColor(
        context,
        R.drawable.outline_comment_24,
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
