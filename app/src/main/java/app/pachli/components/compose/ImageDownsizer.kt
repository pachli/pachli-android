/* Copyright 2022 Tusky contributors
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

package app.pachli.components.compose

import android.content.ContentResolver
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.pachli.util.calculateInSampleSize
import app.pachli.util.reorientBitmap
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * @param uri             the uri pointing to the input file
 * @param sizeLimit       the maximum number of bytes the output image is allowed to have
 * @param contentResolver to resolve the specified input uri
 * @param tempFile        the file where the result will be stored
 * @throws FileNotFoundException if [uri] could not be opened.
 */
fun downsizeImage(
    uri: Uri,
    sizeLimit: Long,
    contentResolver: ContentResolver,
    tempFile: File,
) {
    // Initially, just get the image dimensions.
    val options = BitmapFactory.Options()
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("openInputStream returned null")
    inputStream.use { input ->
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(input, null, options)
    }

    // Get EXIF data, for orientation info.
    val orientation = getImageOrientation(uri, contentResolver)

    /* Unfortunately, there isn't a determined worst case compression ratio for image
     * formats. So, the only way to tell if they're too big is to compress them and
     * test, and keep trying at smaller sizes. The initial estimate should be good for
     * many cases, so it should only iterate once, but the loop is used to be absolutely
     * sure it gets downsized to below the limit. */
    var scaledImageSize = 1024
    do {
        val outputStream = FileOutputStream(tempFile)
        val decodeBitmapInputStream = contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("openInputStream returned null")
        options.inSampleSize = calculateInSampleSize(options, scaledImageSize, scaledImageSize)
        options.inJustDecodeBounds = false

        val scaledBitmap = decodeBitmapInputStream.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return

        val reorientedBitmap = reorientBitmap(scaledBitmap, orientation)
        if (reorientedBitmap == null) {
            scaledBitmap.recycle()
            return
        }
        /* Retain transparency if there is any by encoding as png */
        val format: CompressFormat = if (!reorientedBitmap.hasAlpha()) {
            CompressFormat.JPEG
        } else {
            CompressFormat.PNG
        }
        reorientedBitmap.compress(format, 85, outputStream)
        reorientedBitmap.recycle()
        scaledImageSize /= 2
    } while (tempFile.length() > sizeLimit)
}

/**
 * @return The EXIF orientation of the image at the local [uri].
 * @throws FileNotFoundException if [uri] could not be opened.
 */
private fun getImageOrientation(uri: Uri, contentResolver: ContentResolver): Int {
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("openInputStream returned null")

    return inputStream.use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }
}
