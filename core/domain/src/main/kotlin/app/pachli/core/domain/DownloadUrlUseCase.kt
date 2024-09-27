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

package app.pachli.core.domain

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import app.pachli.core.preferences.DownloadLocation.DOWNLOADS
import app.pachli.core.preferences.DownloadLocation.DOWNLOADS_PER_ACCOUNT
import app.pachli.core.preferences.DownloadLocation.DOWNLOADS_PER_SENDER
import app.pachli.core.preferences.SharedPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Downloads a URL respecting the user's preferences.
 *
 * @see [invoke]
 */
class DownloadUrlUseCase @Inject constructor(
    @ApplicationContext val context: Context,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) {
    /**
     * Enqueues a [DownloadManager] request to download [url].
     *
     * The downloaded file is named after the URL's last path segment, and saved
     * according to the user's
     * [downloadLocation][SharedPreferencesRepository.downloadLocation] preference.
     *
     * @param url URL to download
     * @param recipient Username of the account downloading the URL. Is expected
     * to start with an "@"
     * @param sender Username of the account supplying the URL. May or may not
     * start with an "@", one is prepended to the download directory if missing.
     */
    operator fun invoke(url: String, recipient: String, sender: String) {
        val uri = Uri.parse(url)
        val filename = uri.lastPathSegment ?: return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(uri)

        val locationPref = sharedPreferencesRepository.downloadLocation

        val path = when (locationPref) {
            DOWNLOADS -> filename
            DOWNLOADS_PER_ACCOUNT -> File(recipient, filename).toString()
            DOWNLOADS_PER_SENDER -> {
                val finalSender = if (sender.startsWith("@")) sender else "@$sender"
                File(finalSender, filename).toString()
            }
        }

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path)
        downloadManager.enqueue(request)
    }
}
