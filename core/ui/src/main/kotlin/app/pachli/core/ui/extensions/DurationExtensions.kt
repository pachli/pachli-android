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

import java.time.Duration

/**
 * @return The [Duration] formatted as `NNdNNhNNmNNs` (e.g., `01d23h15m23s`) with any leading
 *     zero components removed. So 34 minutes and 15 seconds is `34m15s` not `00d00h34m15s`.
 */
fun Duration.asDdHhMmSs(): String {
    val days = this.toDaysPart()
    val hours = this.toHoursPart()
    val minutes = this.toMinutesPart()
    val seconds = this.toSecondsPart()

    return if (this.isNegative) {
        when {
            days < 0 -> "%02dd%02dh%02dm%02ds".format(days, hours, minutes, seconds)
            hours < 0 -> "%02dh%02dm%02ds".format(hours, minutes, seconds)
            minutes < 0 -> "%02dm%02ds".format(minutes, seconds)
            else -> "%02ds".format(seconds)
        }
    } else {
        when {
            days > 0 -> "%02dd%02dh%02dm%02ds".format(days, hours, minutes, seconds)
            hours > 0 -> "%02dh%02dm%02ds".format(hours, minutes, seconds)
            minutes > 0 -> "%02dm%02ds".format(minutes, seconds)
            else -> "%02ds".format(seconds)
        }
    }
}
