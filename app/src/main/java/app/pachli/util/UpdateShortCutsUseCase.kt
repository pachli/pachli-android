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
import android.graphics.Canvas
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.MainActivityIntent
import com.bumptech.glide.Glide
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateShortCutsUseCase @Inject constructor(
    @ApplicationContext val context: Context,
) {
    /**
     * Updates shortcuts to reflect [accounts].
     *
     * The first [N][ShortcutManagerCompat.getMaxShortcutCountPerActivity] accounts
     * are converted to shortcuts which launch [app.pachli.MainActivity]. The
     * active account is always included.
     */
    suspend operator fun invoke(accounts: List<AccountEntity>) = withContext(Dispatchers.IO) {
        val innerSize = context.resources.getDimensionPixelSize(DR.dimen.adaptive_bitmap_inner_size)
        val outerSize = context.resources.getDimensionPixelSize(DR.dimen.adaptive_bitmap_outer_size)

        val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)

        val shortcuts = accounts
            .sortedBy { it.isActive }
            .take(maxShortcuts)
            .mapNotNull { account ->
                val drawable = try {
                    if (TextUtils.isEmpty(account.profilePictureUrl)) {
                        AppCompatResources.getDrawable(context, DR.drawable.avatar_default)
                    } else {
                        Glide.with(context)
                            .asDrawable()
                            .load(account.profilePictureUrl)
                            .error(DR.drawable.avatar_default)
                            .submit(innerSize, innerSize)
                            .get()
                    }
                } catch (e: ExecutionException) {
                    // The `.error` handler isn't always used. For example, Glide throws
                    // ExecutionException if the URL does not point at an image. Fallback to
                    // the default avatar (https://github.com/bumptech/glide/issues/4672).
                    AppCompatResources.getDrawable(context, DR.drawable.avatar_default)
                } ?: return@mapNotNull null

                // inset the loaded bitmap inside a 108dp transparent canvas so it looks good as adaptive icon
                val outBmp = createBitmap(outerSize, outerSize)

                val canvas = Canvas(outBmp)
                val border = (outerSize - innerSize) / 2
                drawable.setBounds(border, border, border + innerSize, border + innerSize)
                drawable.draw(canvas)

                val icon = IconCompat.createWithAdaptiveBitmap(outBmp)

                val person = Person.Builder()
                    .setIcon(icon)
                    .setName(account.displayName)
                    .setKey(account.identifier)
                    .build()

                // This intent will be sent when the user clicks on one of the launcher shortcuts.
                // Intent from share sheet will be different
                val intent = MainActivityIntent.fromShortcut(context, account.id)

                ShortcutInfoCompat.Builder(context, account.id.toString())
                    .setIntent(intent)
                    .setCategories(setOf("app.pachli.Share"))
                    .setShortLabel(account.displayName.ifBlank { account.fullName })
                    .setPerson(person)
                    .setLongLived(true)
                    .setIcon(icon)
                    .build()
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
