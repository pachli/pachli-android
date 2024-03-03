/* Copyright 2019 Tusky Contributors
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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.MainActivityIntent
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun updateShortcut(context: Context, account: AccountEntity) = withContext(Dispatchers.IO) {
    val innerSize = context.resources.getDimensionPixelSize(DR.dimen.adaptive_bitmap_inner_size)
    val outerSize = context.resources.getDimensionPixelSize(DR.dimen.adaptive_bitmap_outer_size)

    val bmp = if (TextUtils.isEmpty(account.profilePictureUrl)) {
        Glide.with(context)
            .asBitmap()
            .load(DR.drawable.avatar_default)
            .submit(innerSize, innerSize)
            .get()
    } else {
        Glide.with(context)
            .asBitmap()
            .load(account.profilePictureUrl)
            .error(DR.drawable.avatar_default)
            .submit(innerSize, innerSize)
            .get()
    }

    // inset the loaded bitmap inside a 108dp transparent canvas so it looks good as adaptive icon
    val outBmp = Bitmap.createBitmap(outerSize, outerSize, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(outBmp)
    canvas.drawBitmap(bmp, (outerSize - innerSize).toFloat() / 2f, (outerSize - innerSize).toFloat() / 2f, null)

    val icon = IconCompat.createWithAdaptiveBitmap(outBmp)

    val person = Person.Builder()
        .setIcon(icon)
        .setName(account.displayName)
        .setKey(account.identifier)
        .build()

    // This intent will be sent when the user clicks on one of the launcher shortcuts. Intent from share sheet will be different
    val intent = MainActivityIntent(context).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, account.id.toString())
    }

    val shortcutInfo = ShortcutInfoCompat.Builder(context, account.id.toString())
        .setIntent(intent)
        .setCategories(setOf("app.pachli.Share"))
        .setShortLabel(account.displayName)
        .setPerson(person)
        .setLongLived(true)
        .setIcon(icon)
        .build()

    ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcutInfo))
}

fun removeShortcut(context: Context, account: AccountEntity) {
    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(account.id.toString()))
}
