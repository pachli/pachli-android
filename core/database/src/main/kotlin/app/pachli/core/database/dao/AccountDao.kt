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

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import app.pachli.core.database.Converters
import app.pachli.core.database.model.PachliAccountEntity
import app.pachli.core.database.model.PachliAccountWithRelations
import app.pachli.core.model.AccountSource
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import kotlinx.coroutines.flow.Flow

@Dao
@ColumnTypeConverters(Converters::class)
interface AccountDao {
    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun getPachliAccount(pachliAccountId: Long): PachliAccountWithRelations?

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    fun getPachliAccountFlow(pachliAccountId: Long): Flow<PachliAccountWithRelations?>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActivePachliAccountFlow(): Flow<PachliAccountWithRelations?>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActivePachliAccount(): PachliAccountWithRelations?

    @Query(
        """
SELECT *
FROM PachliAccountEntity
""",
    )
    fun loadAllPachliAccountFlow(): Flow<List<PachliAccountWithRelations>>

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
DELETE
FROM PachliAccountEntity
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun deleteAccountById(pachliAccountId: Long)

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY pachliAccountId ASC
""",
    )
    fun loadAllFlow(): Flow<List<PachliAccountEntity>>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY pachliAccountId ASC
""",
    )
    suspend fun loadAll(): List<PachliAccountEntity>

    @Query(
        """
SELECT pachliAccountId
FROM PachliAccountEntity
WHERE isActive = 1
""",
    )
    fun getActiveAccountId(): Flow<Long>

    @Query(
        """
SELECT *
FROM PachliAccountEntity
ORDER BY isActive DESC, pachliAccountId ASC
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
    suspend fun getActivePachliAccountEntity(): PachliAccountEntity?

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
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun getPachliAccountEntityById(pachliAccountId: Long): PachliAccountEntity?

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
SELECT COUNT(pachliAccountId)
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
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setPushNotificationData(
        pachliAccountId: Long,
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
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setAlwaysShowSensitiveMedia(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    alwaysOpenSpoiler = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setAlwaysOpenSpoiler(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    mediaPreviewEnabled = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setMediaPreviewEnabled(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    tabPreferences = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setTabPreferences(pachliAccountId: Long, value: List<Timeline>)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationMarkerId = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationMarkerId(pachliAccountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFilter = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsFilter(pachliAccountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultPostPrivacy = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setDefaultPostPrivacy(pachliAccountId: Long, value: Status.Visibility)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultMediaSensitivity = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setDefaultMediaSensitivity(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultPostLanguage = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setDefaultPostLanguage(pachliAccountId: Long, value: String)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    defaultQuotePolicy = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setDefaultQuotePolicy(pachliAccountId: Long, value: AccountSource.QuotePolicy)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsEnabled = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsEnabled(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsMentioned = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsMentioned(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFollowed = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsFollowed(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFollowRequested = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsFollowRequested(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsReblogged = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsReblogged(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsQuotes = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsQuotes(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsQuotedUpdates = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsQuotedUpdate(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsFavorited = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsFavorited(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsPolls = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsPolls(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSubscriptions = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsSubscriptions(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSignUps = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsSignUps(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsUpdates = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsUpdates(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsReports = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsReports(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsSeveredRelationships = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationsSeveredRelationships(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationsModerationWarnings = :value
WHERE pachliAccountId = :pachliAccountId
        """,
    )
    suspend fun setNotificationsModerationWarnings(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationSound = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationSound(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationVibration = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationVibration(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationLight = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationLight(pachliAccountId: Long, value: Boolean)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterNotFollowed = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationAccountFilterNotFollowed(pachliAccountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterYounger30d = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationAccountFilterYounger30d(pachliAccountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    notificationAccountFilterLimitedByServer = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setNotificationAccountFilterLimitedByServer(pachliAccountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterNotFollowed = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setConversationAccountFilterNotFollowed(pachliAccountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterYounger30d = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setConversationAccountFilterYounger30d(pachliAccountId: Long, value: FilterAction)

    @Query(
        """
UPDATE PachliAccountEntity
SET
    conversationAccountFilterLimitedByServer = :value
WHERE pachliAccountId = :pachliAccountId
""",
    )
    suspend fun setConversationAccountFilterLimitedByServer(pachliAccountId: Long, value: FilterAction)
}
