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

package app.pachli.core.ui.extensions

import androidx.annotation.DrawableRes
import app.pachli.core.common.PachliError
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.network.retrofit.apiresult.HttpError
import app.pachli.core.network.retrofit.apiresult.IoError
import app.pachli.core.ui.R

/** @return A drawable resource to accompany the error message for this [PachliError]. */
@DrawableRes
fun PachliError.getDrawableRes(): Int = when (this) {
    is IoError -> R.drawable.errorphant_offline
    is HttpError -> when (this) {
        is ClientError.NotFound -> R.drawable.elephant_friend_empty
        else -> R.drawable.errorphant_offline
    }
    else -> R.drawable.errorphant_offline
}
