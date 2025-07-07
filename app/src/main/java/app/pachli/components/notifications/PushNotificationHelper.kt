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

package app.pachli.components.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.domain.notifications.AccountNotificationMethod
import app.pachli.core.domain.notifications.AppNotificationMethod
import app.pachli.core.domain.notifications.hasPushScope
import app.pachli.core.domain.notifications.notificationMethod
import app.pachli.core.ui.extensions.awaitSingleChoiceItem
import org.unifiedpush.android.connector.PREF_MASTER
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

/**
 * Logs the current state of UnifiedPush preferences for debugging.
 *
 * @param context
 * @param msg Optional message to log before the preferences.
 */
fun logUnifiedPushPreferences(context: Context, msg: String? = null) {
    msg?.let { Timber.d(it) }

    context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE).all.entries.forEach {
        Timber.d("  ${it.key} -> ${it.value}")
    }
}

/** @return The app level [app.pachli.core.domain.notifications.AppNotificationMethod]. */
fun notificationMethod(context: Context, accountManager: AccountManager): AppNotificationMethod {
    UnifiedPush.getAckDistributor(context) ?: return AppNotificationMethod.ALL_PULL

    val notificationMethods = accountManager.accounts.map { it.notificationMethod }

    // All pull?
    if (notificationMethods.all { it == AccountNotificationMethod.PULL }) return AppNotificationMethod.ALL_PULL

    // At least one is PUSH. If any are PULL then it's mixed, otherwise all must be push
    if (notificationMethods.any { it == AccountNotificationMethod.PULL }) return AppNotificationMethod.MIXED

    return AppNotificationMethod.ALL_PUSH
}

/** @return True if the active account does not have the `push` Oauth scope, false otherwise. */
fun activeAccountNeedsPushScope(accountManager: AccountManager) = accountManager.activeAccount?.hasPushScope == false

/**
 * Choose the [UnifiedPush] distributor to register with.
 *
 * If no distributor is installed on the device, returns null.
 *
 * If one distributor is installed on the device, returns that.
 *
 * If multiple distributors are installed on the device, and the user has previously chosen
 * a distributor, and that distributor is still installed on the device, and
 * [usePreviousDistributor] is true, return that.
 *
 * Otherwise, show the user list of distributors and allows them to choose one. Returns
 * their choice, unless they cancelled the dialog, in which case return null.
 *
 * @param context This must by an Activity context so it can be used to show a dialog.
 * @param usePreviousDistributor
 */
suspend fun chooseUnifiedPushDistributor(context: Context, usePreviousDistributor: Boolean = true): String? {
    val distributors = UnifiedPush.getDistributors(context)

    Timber.d("Available distributors:")
    distributors.forEach {
        Timber.d("  %s", it)
    }

    return when (distributors.size) {
        0 -> null
        1 -> distributors.first()
        else -> {
            val distributor = UnifiedPush.getSavedDistributor(context)
            if (usePreviousDistributor && distributors.contains(distributor)) {
                Timber.d("Re-using user's previous distributor choice, %s", distributor)
                return distributor
            }

            val distributorLabels = distributors.mapNotNull { getApplicationLabel(context, it) }
            val result = AlertDialog.Builder(context)
                .setTitle("Choose UnifiedPush distributor")
                .awaitSingleChoiceItem(
                    distributorLabels,
                    -1,
                    android.R.string.ok,
                )
            if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                distributors[result.index]
            } else {
                null
            }
        }
    }
}

/**
 * @return The application label of [packageName], or null if the package name
 * is not among installed packages.
 */
fun getApplicationLabel(context: Context, packageName: String): String? {
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        context.packageManager.getApplicationLabel(info)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } as String?
}
