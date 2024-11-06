/*
 * Copyright 2018 Conny Duck
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

package app.pachli.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.PachliAccount
import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Status
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface AccountDao {
    @Transaction
    @Query(
        """
        SELECT *
          FROM AccountEntity
         WHERE id = :accountId
    """,
    )
    suspend fun getPachliAccount(accountId: Long): PachliAccount?

    @Transaction
    @Query(
        """
        SELECT *
          FROM AccountEntity
         WHERE id = :accountId
        """,
    )
    fun getPachliAccountFlow(accountId: Long): Flow<PachliAccount?>

    @Transaction
    @Query(
        """
        SELECT *
         FROM AccountEntity
        WHERE isActive = 1
        """,
    )
    fun getActivePachliAccountFlow(): Flow<PachliAccount?>

    @Update
    suspend fun update(account: AccountEntity)

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM AccountEntity ORDER BY id ASC")
    fun loadAllFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM AccountEntity ORDER BY id ASC")
    suspend fun loadAll(): List<AccountEntity>

    @Query("SELECT id FROM AccountEntity WHERE isActive = 1")
    fun getActiveAccountId(): Flow<Long>

    @Query(
        """
        SELECT *
         FROM AccountEntity
        ORDER BY isActive, id ASC
        """,
    )
    fun getAccountsOrderedByActive(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT *
         FROM AccountEntity
        WHERE isActive = 1
        """,
    )
    fun getActiveAccountFlow(): Flow<AccountEntity?>

    @Query(
        """
        SELECT *
         FROM AccountEntity
        WHERE isActive = 1
        """,
    )
    suspend fun getActiveAccount(): AccountEntity?

    @Query(
        """
        UPDATE AccountEntity
           SET isActive = 0
    """,
    )
    suspend fun clearActiveAccount()

    @Query(
        """
        SELECT *
         FROM AccountEntity
        WHERE id = :id
    """,
    )
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query(
        """
        SELECT *
         FROM AccountEntity
        WHERE domain = :domain AND accountId = :accountId
    """,
    )
    suspend fun getAccountByIdAndDomain(accountId: String, domain: String): AccountEntity?

    @Query(
        """
        SELECT COUNT(id)
          FROM AccountEntity
         WHERE notificationsEnabled = 1
        """,
    )
    suspend fun countAccountsWithNotificationsEnabled(): Int

    @Query(
        """
            UPDATE AccountEntity
               SET unifiedPushUrl = :unifiedPushUrl,
                   pushServerKey = :pushServerKey,
                   pushAuth = :pushAuth,
                   pushPrivKey = :pushPrivKey,
                   pushPubKey = :pushPubKey
             WHERE id = :accountId
        """,
    )
    suspend fun setPushNotificationData(
        accountId: Long,
        unifiedPushUrl: String,
        pushServerKey: String,
        pushAuth: String,
        pushPrivKey: String,
        pushPubKey: String,
    )

    @Query(
        """
            UPDATE AccountEntity
               SET accessToken = "",
                   clientId = "",
                   clientSecret = ""
             WHERE id = :accountId
        """,
    )
    suspend fun clearLoginCredentials(accountId: Long)

    @Query(
        """
        UPDATE AccountEntity
           SET alwaysShowSensitiveMedia = :value
         WHERE id = :accountId
    """,
    )
    suspend fun setAlwaysShowSensitiveMedia(accountId: Long, value: Boolean)

    @Query(
        """
        UPDATE AccountEntity
           SET alwaysOpenSpoiler = :value
         WHERE id = :accountId
    """,
    )
    suspend fun setAlwaysOpenSpoiler(accountId: Long, value: Boolean)

    @Query(
        """
        UPDATE AccountEntity
           SET mediaPreviewEnabled = :value
         WHERE id = :accountId
    """,
    )
    suspend fun setMediaPreviewEnabled(accountId: Long, value: Boolean)

    @Query(
        """
        UPDATE AccountEntity
           SET tabPreferences = :value
         WHERE id = :accountId
        """,
    )
    suspend fun setTabPreferences(accountId: Long, value: List<Timeline>)

    @Query(
        """
        UPDATE AccountEntity
           SET notificationMarkerId = :value
         WHERE id = :accountId
        """,
    )
    suspend fun setNotificationMarkerId(accountId: Long, value: String)

    @Query(
        """
        UPDATE AccountEntity
           SET notificationsFilter = :value
         WHERE id = :accountId
        """,
    )
    suspend fun setNotificationsFilter(accountId: Long, value: String)

    @Query(
        """
        UPDATE AccountEntity
           SET lastNotificationId = :value
         WHERE id = :accountId
        """,
    )
    suspend fun setLastNotificationId(accountId: Long, value: String)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET defaultPostPrivacy = :value
         WHERE id = :accountId
        """,
    )
    fun setDefaultPostPrivacy(accountId: Long, value: Status.Visibility)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET defaultMediaSensitivity = :value
         WHERE id = :accountId
        """,
    )
    fun setDefaultMediaSensitivity(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET defaultPostLanguage = :value
         WHERE id = :accountId
        """,
    )
    fun setDefaultPostLanguage(accountId: Long, value: String)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsEnabled = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsEnabled(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsFollowed = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsFollowed(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsFollowRequested = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsFollowRequested(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsReblogged = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsReblogged(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsFavorited = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsFavorited(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsPolls = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsPolls(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsSubscriptions = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsSubscriptions(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsSignUps = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsSignUps(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsUpdates = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsUpdates(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationsReports = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationsReports(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationSound = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationSound(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationVibration = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationVibration(accountId: Long, value: Boolean)

    // TODO: Should be suspend
    @Query(
        """
        UPDATE AccountEntity
           SET notificationLight = :value
         WHERE id = :accountId
        """,
    )
    fun setNotificationLight(accountId: Long, value: Boolean)

    @Query(
        """
        UPDATE AccountEntity
           SET lastVisibleHomeTimelineStatusId = :value
         WHERE id = :accountId
        """,
    )
    suspend fun setLastVisibleHomeTimelineStatusId(accountId: Long, value: String?)
}
