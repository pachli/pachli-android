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
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.NotificationConfig
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.extensions.awaitSingleChoiceItem
import app.pachli.util.CryptoUtil
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.PREF_MASTER
import org.unifiedpush.android.connector.PREF_MASTER_DISTRIBUTOR
import org.unifiedpush.android.connector.PREF_MASTER_DISTRIBUTOR_ACK
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

/** The notification method an account is using. */
enum class AccountNotificationMethod {
    /** Notifications are pushed. */
    PUSH,

    /** Notifications are pulled. */
    PULL,
}

/** The overall app notification method. */
enum class AppNotificationMethod {
    /** All accounts are configured to use UnifiedPush, and are registered with the distributor. */
    ALL_PUSH,

    /**
     * Some accounts are configured to use UnifiedPush, and are registered with the distributor.
     * For other accounts either registration failed, or their server does not support push, and
     * notifications are pulled.
     */
    MIXED,

    /** All accounts are configured to pull notifications. */
    ALL_PULL,
}

/** The account's [AccountNotificationMethod]. */
val AccountEntity.notificationMethod: AccountNotificationMethod
    get() {
        if (unifiedPushUrl.isBlank()) return AccountNotificationMethod.PULL
        return AccountNotificationMethod.PUSH
    }

/** True if the account has the `push` OAuth scope, false otherwise. */
val AccountEntity.hasPushScope: Boolean
    get() = oauthScopes.contains("push")

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

/** @return The app level [AppNotificationMethod]. */
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
 * @param context
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

/**
 * Disables all notifications.
 *
 * - Cancels notification workers
 * - Unregisters instances from the UnifiedPush distributor
 */
suspend fun disableAllNotifications(context: Context, api: MastodonApi, accountManager: AccountManager) {
    Timber.d("Disabling all notifications")
    disablePushNotifications(context, api, accountManager)
    disablePullNotifications(context)
}

/**
 * Disables push push notifications for each account.
 */
private suspend fun disablePushNotifications(context: Context, api: MastodonApi, accountManager: AccountManager) {
    accountManager.accounts.forEach { disablePushNotificationsForAccount(context, api, accountManager, it) }
}

/**
 * Disables UnifiedPush notifications for [account].
 *
 * - Clears UnifiedPush related data from the account's data
 * - Calls the server to disable push notifications
 * - Unregisters from the UnifiedPush provider
 */
suspend fun disablePushNotificationsForAccount(context: Context, api: MastodonApi, accountManager: AccountManager, account: AccountEntity) {
    if (account.notificationMethod != AccountNotificationMethod.PUSH) return

    // Clear the push notification from the account.
    account.unifiedPushUrl = ""
    account.pushServerKey = ""
    account.pushAuth = ""
    account.pushPrivKey = ""
    account.pushPubKey = ""
    accountManager.saveAccount(account)
    NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.Pull

    // Try and unregister the endpoint from the server. Nothing we can do if this fails, and no
    // need to wait for it to complete.
    withContext(Dispatchers.IO) {
        launch {
            api.unsubscribePushNotifications("Bearer ${account.accessToken}", account.domain)
        }
    }

    // Unregister from the UnifiedPush provider.
    //
    // UnifiedPush.unregisterApp will try and remove the user's distributor choice (including
    // whether or not instances had acked it). Work around this bug by saving the values, and
    // restoring them afterwards.
    val prefs = context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)
    val savedDistributor = UnifiedPush.getSavedDistributor(context)
    val savedDistributorAck = prefs.getBoolean(PREF_MASTER_DISTRIBUTOR_ACK, false)

    UnifiedPush.unregisterApp(context, account.unifiedPushInstance)

    prefs.edit().apply {
        putString(PREF_MASTER_DISTRIBUTOR, savedDistributor)
        putBoolean(PREF_MASTER_DISTRIBUTOR_ACK, savedDistributorAck)
    }.apply()
}

/**
 * Subscription data for [MastodonApi.subscribePushNotifications]. Fetches all user visible
 * notifications.
 */
val subscriptionData = buildMap {
    Notification.Type.visibleTypes.forEach {
        put("data[alerts][${it.presentation}]", true)
    }
}

/**
 * Finishes Unified Push distributor registration
 *
 * Called from [app.pachli.receiver.UnifiedPushBroadcastReceiver.onNewEndpoint] after
 * the distributor has set the endpoint.
 */
suspend fun registerUnifiedPushEndpoint(
    context: Context,
    api: MastodonApi,
    accountManager: AccountManager,
    account: AccountEntity,
    endpoint: String,
) = withContext(Dispatchers.IO) {
    // Generate a prime256v1 key pair for WebPush
    // Decryption is unimplemented for now, since Mastodon uses an old WebPush
    // standard which does not send needed information for decryption in the payload
    // This makes it not directly compatible with UnifiedPush
    // As of now, we use it purely as a way to trigger a pull
    val keyPair = CryptoUtil.generateECKeyPair(CryptoUtil.CURVE_PRIME256_V1)
    val auth = CryptoUtil.secureRandomBytesEncoded(16)

    api.subscribePushNotifications(
        "Bearer ${account.accessToken}",
        account.domain,
        endpoint,
        keyPair.pubkey,
        auth,
        subscriptionData,
    ).onFailure { error ->
        Timber.w("Error setting push endpoint for account %s %d: %s", account, account.id, error.fmt(context))
        NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.PushError(error.throwable)
        disablePushNotificationsForAccount(context, api, accountManager, account)
    }.onSuccess {
        Timber.d("UnifiedPush registration succeeded for account %d", account.id)

        account.pushPubKey = keyPair.pubkey
        account.pushPrivKey = keyPair.privKey
        account.pushAuth = auth
        account.pushServerKey = it.body.serverKey
        account.unifiedPushUrl = endpoint
        accountManager.saveAccount(account)

        NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.Push
    }
}
