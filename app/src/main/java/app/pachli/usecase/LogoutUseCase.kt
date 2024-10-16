package app.pachli.usecase

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import app.pachli.components.drafts.DraftHelper
import app.pachli.components.notifications.deleteNotificationChannelsForAccount
import app.pachli.components.notifications.disablePushNotificationsForAccount
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.LogoutError
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
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
     *
     * @return [Result] of the [AccountEntity] that should be logged in next, null if there are no
     * other accounts to log in to. Or the error that occurred during logout.
     */
    suspend operator fun invoke(): Result<AccountEntity?, LogoutError> {
        val activeAccount = accountManager.activeAccount ?: return Err(LogoutError.NoActiveAccount)

        // disable push notifications
        disablePushNotificationsForAccount(context, api, accountManager, activeAccount)

        api.revokeOAuthToken(
            clientId = activeAccount.clientId,
            clientSecret = activeAccount.clientSecret,
            token = activeAccount.accessToken,
        )
            .onFailure { return Err(LogoutError.Api(it)) }

        // clear notification channels
        deleteNotificationChannelsForAccount(activeAccount, context)

        // remove account from local AccountManager
        val nextAccount = accountManager.logActiveAccountOut()
            .onFailure { return Err(it) }

        // Clear the database.
        // TODO: This should be handled with foreign key constraints.
        timelineDao.removeAll(activeAccount.id)
        timelineDao.removeAllStatusViewData(activeAccount.id)
        remoteKeyDao.delete(activeAccount.id)
        conversationsDao.deleteForAccount(activeAccount.id)
        draftHelper.deleteAllDraftsAndAttachmentsForAccount(activeAccount.id)

        // remove shortcut associated with the account
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(activeAccount.id.toString()))

        return nextAccount
    }
}
