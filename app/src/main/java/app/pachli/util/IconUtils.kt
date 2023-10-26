/* Copyright 2022 Tusky Contributors
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

package app.pachli.util

import android.content.Context
import android.graphics.Color
import androidx.annotation.Px
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizePx

fun makeIcon(context: Context, icon: GoogleMaterial.Icon, @Px iconSize: Int): IconicsDrawable {
    return IconicsDrawable(context, icon).apply {
        sizePx = iconSize
        colorInt = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorControlNormal, Color.BLACK)
    }
}
