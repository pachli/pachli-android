package app.pachli.usecase

import android.content.Context
import app.pachli.components.drafts.DraftHelper
import app.pachli.components.notifications.androidNotificationsAreEnabled
import app.pachli.components.notifications.deleteNotificationChannelsForAccount
import app.pachli.components.notifications.disablePullNotifications
import app.pachli.components.notifications.disableUnifiedPushNotificationsForAccount
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.removeShortcut
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LogoutUsecase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MastodonApi,
    private val timelineDao: TimelineDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val conversationsDao: ConversationsDao,
    private val accountManager: AccountManager,
    private val draftHelper: DraftHelper,
) {

    /**
     * Logs the current account out and clears all caches associated with it
     * @return true if the user is logged in with other accounts, false if it was the only one
     */
    suspend fun logout(): Boolean {
        accountManager.activeAccount?.let { activeAccount ->

            // invalidate the oauth token, if we have the client id & secret
            // (could be missing if user logged in with a previous version of the app)
            val clientId = activeAccount.clientId
            val clientSecret = activeAccount.clientSecret
            if (clientId != null && clientSecret != null) {
                api.revokeOAuthToken(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    token = activeAccount.accessToken,
                )
            }

            // disable push notifications
            disableUnifiedPushNotificationsForAccount(context, activeAccount)

            // disable pull notifications
            if (!androidNotificationsAreEnabled(context, accountManager)) {
                disablePullNotifications(context)
            }

            // clear notification channels
            deleteNotificationChannelsForAccount(activeAccount, context)

            // remove account from local AccountManager
            val otherAccountAvailable = accountManager.logActiveAccountOut() != null

            // clear the database - this could trigger network calls so do it last when all tokens are gone
            timelineDao.removeAll(activeAccount.id)
            timelineDao.removeAllStatusViewData(activeAccount.id)
            remoteKeyDao.delete(activeAccount.id)
            conversationsDao.deleteForAccount(activeAccount.id)
            draftHelper.deleteAllDraftsAndAttachmentsForAccount(activeAccount.id)

            // remove shortcut associated with the account
            removeShortcut(context, activeAccount)

            return otherAccountAvailable
        }
        return false
    }
}
