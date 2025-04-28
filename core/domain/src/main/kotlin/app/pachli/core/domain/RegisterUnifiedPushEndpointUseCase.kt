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

package app.pachli.core.domain

import android.content.Context
import app.pachli.core.common.util.CryptoUtil
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.domain.notifications.DisablePushNotificationsForAccountUseCase
import app.pachli.core.domain.notifications.NotificationConfig
import app.pachli.core.network.model.Notification
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class RegisterUnifiedPushEndpointUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val disablePushNotificationsForAccount: DisablePushNotificationsForAccountUseCase,
) {
    /**
     * Subscription data. Fetches all user visible
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
    suspend operator fun invoke(
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
            account.authHeader,
            account.domain,
            endpoint,
            keyPair.pubkey,
            auth,
            subscriptionData,
        ).onFailure { error ->
            Timber.w("Error setting push endpoint for account %s %d: %s", account, account.id, error.fmt(context))
            NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.PushError(error.throwable)
            disablePushNotificationsForAccount(account)
        }.onSuccess {
            Timber.d("UnifiedPush registration succeeded for account %d", account.id)

            accountManager.setPushNotificationData(
                account.id,
                unifiedPushUrl = endpoint,
                pushServerKey = it.body.serverKey,
                pushAuth = auth,
                pushPrivKey = keyPair.privKey,
                pushPubKey = keyPair.pubkey,
            )

            NotificationConfig.notificationMethodAccount[account.fullName] = NotificationConfig.Method.Push
        }
    }
}
