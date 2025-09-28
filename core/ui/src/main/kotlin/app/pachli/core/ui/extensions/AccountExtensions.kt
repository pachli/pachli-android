/*
 * Copyright (c) 2025 Pachli Association
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

import android.content.Context
import app.pachli.core.model.Account

/** @see [app.pachli.core.model.TimelineAccount.nameContentDescription] */
fun Account.nameContentDescription(context: Context) = nameContentDescription(context, name)

/** @see [app.pachli.core.model.TimelineAccount.handleContentDescription] */
fun Account.handleContentDescription(context: Context) = handleContentDescription(context, username)
