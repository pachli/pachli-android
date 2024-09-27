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

package app.pachli.core.preferences

/** Where to save downloaded files. */
enum class DownloadLocation(override val displayResource: Int, override val value: String? = null) :
    PreferenceEnum {
    /** Save to the root of the "Downloads" directory. */
    DOWNLOADS(R.string.download_location_downloads),

    /** Save in per-account directories in the "Downloads" directory. */
    DOWNLOADS_PER_ACCOUNT(R.string.download_location_per_account),

    /** Save in per-sender-account directories in the "Downloads" directory. */
    DOWNLOADS_PER_SENDER(R.string.download_location_per_sender),
}
