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
import app.pachli.core.database.model.PachliAccount
import app.pachli.core.database.model.PachliAccountEntity
import app.pachli.core.model.AccountSource
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(Converters::class)
interface AccountDao {
    @Transaction
    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE id = :accountId
""",
    )
    suspend fun getPachliAccount(accountId: Long): PachliAccount?

    @Transaction
    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE id = :accountId
""",
    )
    fun getPachliAccountFlow(accountId: Long): Flow<PachliAccount?>

    @Transaction
    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActivePachliAccountFlow(): Flow<PachliAccount?>

    @Transaction
    @Query(
        """
SELECT *
FROM PachliAccountEntity
""",
    )
    fun loadAllPachliAccountFlow(): Flow<List<PachliAccount>>

    @Update
    suspend fun update(account: PachliAccountEntity)

    @Upsert
    suspend fun upsert(account: PachliAccountEntity): Long

    /**
     * Deletes [account].
     *
     * Through foreign key relationships all data in related tables for this account
     * is also deleted.
     */
    @Delete
    suspend fun delete(account: PachliAccountEntity)

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY id ASC
""",
    )
    fun loadAllFlow(): Flow<List<PachliAccountEntity>>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY id ASC
""",
    )
    suspend fun loadAll(): List<PachliAccountEntity>

    @Query(
        """
SELECT id
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActiveAccountId(): Flow<Long>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY isActive DESC, id ASC
""",
    )
    fun getAccountsOrderedByActive(): Flow<List<PachliAccountEntity>>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActiveAccountFlow(): Flow<PachliAccountEntity?>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    suspend fun getActiveAccount(): PachliAccountEntity?

    @Query(
        """
UPDATE PachliAccountEntity
SET
    isActive = 0
""",
    )
    suspend fun clearActiveAccount()

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE id = :id
""",
    )
    suspend fun getAccountById(id: Long): PachliAccountEntity?

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE domain = :domain AND accountId = :accountId
""",
    )
    suspend fun getAccountByIdAndDomain(accountId: String, domain: String): PachliAccountEntity?

    @Query(
        """
SELECT COUNT(id)
FROM PachliAccountEntity
WHERE notificationsEnabled = 1
""",
    )
    suspend fun countAccountsWithNotificationsEnabled(): Int

    @Query(
        """
UPDATE PachliAccountEntity
SET
    unifiedPushUrl = :unifiedPushUrl,
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
UPDATE PachliAccountEntity
SET
    alwaysShowSensitiveMedia = :value
WHERE id = :accountId
""",
    )
    suspend fun setAlwaysShowSensitiveMedia(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    alwaysOpenSpoiler = :value
WHERE id = :accountId
""",
    )
    suspend fun setAlwaysOpenSpoiler(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    mediaPreviewEnabled = :value
WHERE id = :accountId
""",
    )
    suspend fun setMediaPreviewEnabled(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    tabPreferences = :value
WHERE id = :accountId
""",
    )
    suspend fun setTabPreferences(accountId: Long, value: List<Timeline>)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationMarkerId = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationMarkerId(accountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFilter = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsFilter(accountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultPostPrivacy = :value
WHERE id = :accountId
""",
    )
    suspend fun setDefaultPostPrivacy(accountId: Long, value: Status.Visibility)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultMediaSensitivity = :value
WHERE id = :accountId
""",
    )
    suspend fun setDefaultMediaSensitivity(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultPostLanguage = :value
WHERE id = :accountId
""",
    )
    suspend fun setDefaultPostLanguage(accountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultQuotePolicy = :value
WHERE id = :accountId
""",
    )
    suspend fun setDefaultQuotePolicy(accountId: Long, value: AccountSource.QuotePolicy)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsEnabled = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsEnabled(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsMentioned = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsMentioned(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFollowed = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsFollowed(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFollowRequested = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsFollowRequested(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsReblogged = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsReblogged(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsQuotes = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsQuotes(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsQuotedUpdates = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsQuotedUpdate(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFavorited = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsFavorited(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsPolls = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsPolls(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSubscriptions = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsSubscriptions(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSignUps = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsSignUps(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsUpdates = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsUpdates(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsReports = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsReports(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSeveredRelationships = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationsSeveredRelationships(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsModerationWarnings = :value
WHERE id = :accountId
        """,
    )
    suspend fun setNotificationsModerationWarnings(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationSound = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationSound(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationVibration = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationVibration(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationLight = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationLight(accountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterNotFollowed = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationAccountFilterNotFollowed(accountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterYounger30d = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationAccountFilterYounger30d(accountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterLimitedByServer = :value
WHERE id = :accountId
""",
    )
    suspend fun setNotificationAccountFilterLimitedByServer(accountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterNotFollowed = :value
WHERE id = :accountId
""",
    )
    suspend fun setConversationAccountFilterNotFollowed(accountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterYounger30d = :value
WHERE id = :accountId
""",
    )
    suspend fun setConversationAccountFilterYounger30d(accountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterLimitedByServer = :value
WHERE id = :accountId
""",
    )
    suspend fun setConversationAccountFilterLimitedByServer(accountId: Long, value: FilterAction)
}
