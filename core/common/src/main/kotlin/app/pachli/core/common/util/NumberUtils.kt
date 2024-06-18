/*
 * Copyright 2023 Pachli Association
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

package app.pachli.core.common.util

import androidx.core.os.LocaleListCompat
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

private val numberFormatter: NumberFormat = NumberFormat.getInstance()
private val ln_1k = ln(1000.0)

/**
 * Format numbers according to the current locale. Numbers < min have
 * separators (',', '.', etc) inserted according to the locale.
 *
 * Numbers >= min are scaled down to that by multiples of 1,000, and
 * a suffix appropriate to the scaling is appended.
 */
fun formatNumber(num: Long, min: Int = 100000): String {
    val absNum = abs(num)
    if (absNum < min) return numberFormatter.format(num)

    val exp = (ln(absNum.toDouble()) / ln_1k).toInt()

    // Formatting of the number is locale-specific, but the suffixes
    // are locale-agnostic.
    val locale = LocaleListCompat.getAdjustedDefault()[0]
    return String.format(locale, "%.1f%c", num / 1000.0.pow(exp.toDouble()), "KMGTPE"[exp - 1])
}
