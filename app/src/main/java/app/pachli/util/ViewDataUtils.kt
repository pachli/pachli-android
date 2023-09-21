/*
 * Copyright 2023 Tusky Contributors
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

@file:JvmName("ViewDataUtils")

/* Copyright 2017 Andrew Dawson
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */
package app.pachli.util

import app.pachli.entity.TrendingTag
import app.pachli.viewdata.TrendingViewData

fun List<TrendingTag>.toViewData(): List<TrendingViewData.Tag> {
    val maxTrendingValue = flatMap { tag -> tag.history }
        .mapNotNull { it.uses.toLongOrNull() }
        .maxOrNull() ?: 1

    return map { tag ->

        val reversedHistory = tag.history.asReversed()

        TrendingViewData.Tag(
            name = tag.name,
            usage = reversedHistory.mapNotNull { it.uses.toLongOrNull() },
            accounts = reversedHistory.mapNotNull { it.accounts.toLongOrNull() },
            maxTrendingValue = maxTrendingValue,
        )
    }
}
