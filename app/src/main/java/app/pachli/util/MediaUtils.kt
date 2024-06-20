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
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.util

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper methods for obtaining and resizing media files
 */

const val MEDIA_TEMP_PREFIX = "Pachli_Share_Media"
const val MEDIA_SIZE_UNKNOWN = -1L

/**
 * Fetches the size of the media represented by the given URI, assuming it is openable and
 * the ContentResolver is able to resolve it.
 *
 * @return the size of the media in bytes or {@link MediaUtils#MEDIA_SIZE_UNKNOWN}
 */
fun getMediaSize(contentResolver: ContentResolver, uri: Uri?): Long {
    uri ?: return MEDIA_SIZE_UNKNOWN

    var mediaSize = MEDIA_SIZE_UNKNOWN
    val cursor: Cursor?
    try {
        cursor = contentResolver.query(uri, null, null, null, null)
    } catch (e: SecurityException) {
        return MEDIA_SIZE_UNKNOWN
    }
    if (cursor != null) {
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()
        mediaSize = cursor.getLong(sizeIndex)
        cursor.close()
    }
    return mediaSize
}

@Throws(FileNotFoundException::class)
fun getImageSquarePixels(contentResolver: ContentResolver, uri: Uri): Int {
    val options = BitmapFactory.Options()
    contentResolver.openInputStream(uri).use { input ->
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(input, null, options)
    }

    return options.outWidth * options.outHeight
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

fun reorientBitmap(bitmap: Bitmap?, orientation: Int): Bitmap? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> return bitmap
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1.0f, 1.0f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180.0f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180.0f)
            matrix.postScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90.0f)
            matrix.postScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90.0f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90.0f)
            matrix.postScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90.0f)
        else -> return bitmap
    }

    bitmap ?: return null

    return try {
        val result = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
        if (!bitmap.sameAs(result)) {
            bitmap.recycle()
        }
        result
    } catch (e: OutOfMemoryError) {
        null
    }
}

fun getTemporaryMediaFilename(extension: String): String {
    return "${MEDIA_TEMP_PREFIX}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$extension"
}
