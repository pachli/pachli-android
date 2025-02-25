/*
 * Copyright 2023 Pachli Association
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

package app.pachli.core.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.core.database.getStringOrNull
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.AnnouncementsDao
import app.pachli.core.database.dao.ContentFiltersDao
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.database.dao.FollowingAccountDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.dao.ListsDao
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.dao.NotificationDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.AnnouncementEntity
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.ConversationViewDataEntity
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.FollowingAccountEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.LogEntryEntity
import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.database.model.NotificationEntity
import app.pachli.core.database.model.NotificationRelationshipSeveranceEventEntity
import app.pachli.core.database.model.NotificationReportEntity
import app.pachli.core.database.model.NotificationViewDataEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.ServerEntity
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.model.ContentFilterVersion
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Suppress("ClassName")
@Database(
    entities = [
        DraftEntity::class,
        AccountEntity::class,
        InstanceInfoEntity::class,
        EmojisEntity::class,
        StatusEntity::class,
        TimelineAccountEntity::class,
        ConversationEntity::class,
        RemoteKeyEntity::class,
        StatusViewDataEntity::class,
        TranslatedStatusEntity::class,
        LogEntryEntity::class,
        MastodonListEntity::class,
        ServerEntity::class,
        ContentFiltersEntity::class,
        AnnouncementEntity::class,
        FollowingAccountEntity::class,
        NotificationEntity::class,
        NotificationReportEntity::class,
        NotificationViewDataEntity::class,
        NotificationRelationshipSeveranceEventEntity::class,
        TimelineStatusEntity::class,
        ConversationViewDataEntity::class,
    ],
    version = 21,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.MIGRATE_1_2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = AppDatabase.MIGRATE_6_7::class),
        AutoMigration(from = 7, to = 8, spec = AppDatabase.MIGRATE_7_8::class),
        // 8 -> 9 is a custom migration
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12, spec = AppDatabase.MIGRATE_11_12::class),
        // 12 -> 13 is a custom migration
        AutoMigration(from = 13, to = 14, spec = AppDatabase.MIGRATE_13_14::class),
        AutoMigration(from = 14, to = 15, spec = AppDatabase.MIGRATE_14_15::class),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18, spec = AppDatabase.MIGRATE_17_18::class),
        // 18 -> 19 is a custom migration
        AutoMigration(from = 19, to = 20, spec = AppDatabase.MIGRATE_19_20::class),
        AutoMigration(from = 20, to = 21),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun instanceDao(): InstanceDao
    abstract fun conversationDao(): ConversationsDao
    abstract fun timelineDao(): TimelineDao
    abstract fun draftDao(): DraftDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun translatedStatusDao(): TranslatedStatusDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun contentFiltersDao(): ContentFiltersDao
    abstract fun listsDao(): ListsDao
    abstract fun announcementsDao(): AnnouncementsDao
    abstract fun followingAccountDao(): FollowingAccountDao
    abstract fun notificationDao(): NotificationDao
    abstract fun statusDao(): StatusDao

    @DeleteColumn("TimelineStatusEntity", "expanded")
    @DeleteColumn("TimelineStatusEntity", "contentCollapsed")
    @DeleteColumn("TimelineStatusEntity", "contentShowing")
    class MIGRATE_1_2 : AutoMigrationSpec

    /**
     * Part one of migrating [DraftEntity.scheduledAt] from String to Long.
     *
     * Copies existing data from `scheduledAt` into `scheduledAtLong`.
     */
    class MIGRATE_6_7 : AutoMigrationSpec {
        @SuppressLint("ConstantLocale")
        private val iso8601 = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.getDefault(),
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()

            val draftCursor = db.query("SELECT id, scheduledAt FROM DraftEntity")
            with(draftCursor) {
                while (moveToNext()) {
                    val scheduledAt = getStringOrNull(1) ?: continue

                    // Parse the string representation to a Date. Ignore errors, they
                    // shouldn't be possible.
                    val scheduledDate = runCatching { iso8601.parse(scheduledAt) }.getOrNull()
                        ?: continue

                    // Dates are stored as Long, see Converters.dateToLong.
                    val values = ContentValues().apply {
                        put("scheduledAtLong", scheduledDate.time)
                    }

                    val draftId = getInt(0)
                    db.update("DraftEntity", CONFLICT_ABORT, values, "id = ?", arrayOf(draftId))
                }
            }

            db.setTransactionSuccessful()
            db.endTransaction()
        }
    }

    /**
     * Completes the migration started in [MIGRATE_6_7]. SQLite on Android can't
     * drop/rename columns, so use Room's annotations to generate the code to do this.
     */
    @DeleteColumn("DraftEntity", "scheduledAt")
    @RenameColumn("DraftEntity", "scheduledAtLong", "scheduledAt")
    class MIGRATE_7_8 : AutoMigrationSpec

    // lastNotificationId removed in favour of the REFRESH key in RemoteKeyEntity.
    @DeleteColumn("AccountEntity", "lastNotificationId")
    class MIGRATE_11_12 : AutoMigrationSpec

    // lastVisibleHomeTimelineStatusId removed in favour of the REFRESH key in RemoteKeyEntity.
    @DeleteColumn("AccountEntity", "lastVisibleHomeTimelineStatusId")
    class MIGRATE_13_14 : AutoMigrationSpec

    @RenameTable("TimelineStatusEntity", "StatusEntity")
    class MIGRATE_14_15 : AutoMigrationSpec

    @RenameColumn("StatusViewDataEntity", "timelineUserId", "pachliAccountId")
    class MIGRATE_17_18 : AutoMigrationSpec

    // Rename for consistency with other code.
    @RenameColumn("ConversationEntity", "accountId", "pachliAccountId")
    // Conversations are now ordered by date of most recent status.
    @DeleteColumn("ConversationEntity", "order")
    // Removing the embedded status from ConversationStatusEntity, and joining
    // on StatusEntity.
    @DeleteColumn("ConversationEntity", "s_id")
    @DeleteColumn("ConversationEntity", "s_url")
    @DeleteColumn("ConversationEntity", "s_inReplyToId")
    @DeleteColumn("ConversationEntity", "s_inReplyToAccountId")
    @DeleteColumn("ConversationEntity", "s_account")
    @DeleteColumn("ConversationEntity", "s_content")
    @DeleteColumn("ConversationEntity", "s_createdAt")
    @DeleteColumn("ConversationEntity", "s_editedAt")
    @DeleteColumn("ConversationEntity", "s_emojis")
    @DeleteColumn("ConversationEntity", "s_favouritesCount")
    @DeleteColumn("ConversationEntity", "s_repliesCount")
    @DeleteColumn("ConversationEntity", "s_favourited")
    @DeleteColumn("ConversationEntity", "s_bookmarked")
    @DeleteColumn("ConversationEntity", "s_sensitive")
    @DeleteColumn("ConversationEntity", "s_spoilerText")
    @DeleteColumn("ConversationEntity", "s_attachments")
    @DeleteColumn("ConversationEntity", "s_mentions")
    @DeleteColumn("ConversationEntity", "s_tags")
    @DeleteColumn("ConversationEntity", "s_showingHiddenContent")
    @DeleteColumn("ConversationEntity", "s_expanded")
    @DeleteColumn("ConversationEntity", "s_collapsed")
    @DeleteColumn("ConversationEntity", "s_muted")
    @DeleteColumn("ConversationEntity", "s_poll")
    @DeleteColumn("ConversationEntity", "s_language")
    @DeleteColumn("ConversationEntity", "s_showingHiddenContent")
    @DeleteColumn("ConversationEntity", "s_collapsed")
    @DeleteColumn("ConversationEntity", "s_expanded")
    class MIGRATE_19_20 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            super.onPostMigrate(db)
        }
    }
}

val MIGRATE_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // clientId and clientSecret were made non-nullable. Before migrating data convert
        // any existing NULL values to the empty string.
        db.execSQL("UPDATE `AccountEntity` SET `clientId` = '' WHERE `clientId` IS NULL")
        db.execSQL("UPDATE `AccountEntity` SET `clientSecret` = '' WHERE `clientSecret` IS NULL")

        // Migrate the tables.
        //
        // - Mark AccountEntity.clientId and .clientSecret as NON NULL
        // - Delete InstanceEntity.emojiList
        // - Rename InstanceEntity.maximumTootCharacters to .maxPostCharacters
        // - Rename InstanceEntity to InstanceInfoEntity
        db.execSQL("CREATE TABLE IF NOT EXISTS `EmojisEntity` (`accountId` INTEGER NOT NULL, `emojiList` TEXT NOT NULL, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `MastodonListEntity` (`accountId` INTEGER NOT NULL, `listId` TEXT NOT NULL, `title` TEXT NOT NULL, `repliesPolicy` TEXT NOT NULL, `exclusive` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `listId`), FOREIGN KEY(`accountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ServerEntity` (`accountId` INTEGER NOT NULL, `serverKind` TEXT NOT NULL, `version` TEXT NOT NULL, `capabilities` TEXT NOT NULL, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ContentFiltersEntity` (`accountId` INTEGER NOT NULL, `version` TEXT NOT NULL, `contentFilters` TEXT NOT NULL, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `AnnouncementEntity` (`accountId` INTEGER NOT NULL, `announcementId` TEXT NOT NULL, `announcement` TEXT NOT NULL, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_AccountEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `domain` TEXT NOT NULL, `accessToken` TEXT NOT NULL, `clientId` TEXT NOT NULL, `clientSecret` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `accountId` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, `profilePictureUrl` TEXT NOT NULL, `profileHeaderPictureUrl` TEXT NOT NULL DEFAULT '', `notificationsEnabled` INTEGER NOT NULL, `notificationsMentioned` INTEGER NOT NULL, `notificationsFollowed` INTEGER NOT NULL, `notificationsFollowRequested` INTEGER NOT NULL, `notificationsReblogged` INTEGER NOT NULL, `notificationsFavorited` INTEGER NOT NULL, `notificationsPolls` INTEGER NOT NULL, `notificationsSubscriptions` INTEGER NOT NULL, `notificationsSignUps` INTEGER NOT NULL, `notificationsUpdates` INTEGER NOT NULL, `notificationsReports` INTEGER NOT NULL, `notificationsSeveredRelationships` INTEGER NOT NULL DEFAULT true, `notificationSound` INTEGER NOT NULL, `notificationVibration` INTEGER NOT NULL, `notificationLight` INTEGER NOT NULL, `defaultPostPrivacy` INTEGER NOT NULL, `defaultMediaSensitivity` INTEGER NOT NULL, `defaultPostLanguage` TEXT NOT NULL, `alwaysShowSensitiveMedia` INTEGER NOT NULL, `alwaysOpenSpoiler` INTEGER NOT NULL, `mediaPreviewEnabled` INTEGER NOT NULL, `lastNotificationId` TEXT NOT NULL, `notificationMarkerId` TEXT NOT NULL DEFAULT '0', `emojis` TEXT NOT NULL, `tabPreferences` TEXT NOT NULL, `notificationsFilter` TEXT NOT NULL, `oauthScopes` TEXT NOT NULL, `unifiedPushUrl` TEXT NOT NULL, `pushPubKey` TEXT NOT NULL, `pushPrivKey` TEXT NOT NULL, `pushAuth` TEXT NOT NULL, `pushServerKey` TEXT NOT NULL, `lastVisibleHomeTimelineStatusId` TEXT, `locked` INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "INSERT INTO `_new_AccountEntity` (`id`,`domain`,`accessToken`,`clientId`,`clientSecret`,`isActive`,`accountId`,`username`,`displayName`,`profilePictureUrl`,`notificationsEnabled`,`notificationsMentioned`,`notificationsFollowed`,`notificationsFollowRequested`,`notificationsReblogged`,`notificationsFavorited`,`notificationsPolls`,`notificationsSubscriptions`,`notificationsSignUps`,`notificationsUpdates`,`notificationsReports`,`notificationsSeveredRelationships`,`notificationSound`,`notificationVibration`,`notificationLight`,`defaultPostPrivacy`,`defaultMediaSensitivity`,`defaultPostLanguage`,`alwaysShowSensitiveMedia`,`alwaysOpenSpoiler`,`mediaPreviewEnabled`,`lastNotificationId`,`notificationMarkerId`,`emojis`,`tabPreferences`,`notificationsFilter`,`oauthScopes`,`unifiedPushUrl`,`pushPubKey`,`pushPrivKey`,`pushAuth`,`pushServerKey`,`lastVisibleHomeTimelineStatusId`,`locked`) SELECT `id`,`domain`,`accessToken`,`clientId`,`clientSecret`,`isActive`,`accountId`,`username`,`displayName`,`profilePictureUrl`,`notificationsEnabled`,`notificationsMentioned`,`notificationsFollowed`,`notificationsFollowRequested`,`notificationsReblogged`,`notificationsFavorited`,`notificationsPolls`,`notificationsSubscriptions`,`notificationsSignUps`,`notificationsUpdates`,`notificationsReports`,`notificationsSeveredRelationships`,`notificationSound`,`notificationVibration`,`notificationLight`,`defaultPostPrivacy`,`defaultMediaSensitivity`,`defaultPostLanguage`,`alwaysShowSensitiveMedia`,`alwaysOpenSpoiler`,`mediaPreviewEnabled`,`lastNotificationId`,`notificationMarkerId`,`emojis`,`tabPreferences`,`notificationsFilter`,`oauthScopes`,`unifiedPushUrl`,`pushPubKey`,`pushPrivKey`,`pushAuth`,`pushServerKey`,`lastVisibleHomeTimelineStatusId`,`locked` FROM `AccountEntity`",
        )
        db.execSQL("DROP TABLE `AccountEntity`")
        db.execSQL("ALTER TABLE `_new_AccountEntity` RENAME TO `AccountEntity`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_AccountEntity_domain_accountId` ON `AccountEntity` (`domain`, `accountId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `_new_InstanceInfoEntity` (`instance` TEXT NOT NULL, `maxPostCharacters` INTEGER, `maxPollOptions` INTEGER, `maxPollOptionLength` INTEGER, `minPollDuration` INTEGER, `maxPollDuration` INTEGER, `charactersReservedPerUrl` INTEGER, `version` TEXT, `videoSizeLimit` INTEGER, `imageSizeLimit` INTEGER, `imageMatrixLimit` INTEGER, `maxMediaAttachments` INTEGER, `maxFields` INTEGER, `maxFieldNameLength` INTEGER, `maxFieldValueLength` INTEGER, `enabledTranslation` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`instance`))")
        db.execSQL("INSERT INTO `_new_InstanceInfoEntity` (`instance`,`maxPostCharacters`,`maxPollOptions`,`maxPollOptionLength`,`minPollDuration`,`maxPollDuration`,`charactersReservedPerUrl`,`version`,`videoSizeLimit`,`imageSizeLimit`,`imageMatrixLimit`,`maxMediaAttachments`,`maxFields`,`maxFieldNameLength`,`maxFieldValueLength`) SELECT `instance`,`maximumTootCharacters`,`maxPollOptions`,`maxPollOptionLength`,`minPollDuration`,`maxPollDuration`,`charactersReservedPerUrl`,`version`,`videoSizeLimit`,`imageSizeLimit`,`imageMatrixLimit`,`maxMediaAttachments`,`maxFields`,`maxFieldNameLength`,`maxFieldValueLength` FROM `InstanceEntity`")
        db.execSQL("DROP TABLE `InstanceEntity`")
        db.execSQL("ALTER TABLE `_new_InstanceInfoEntity` RENAME TO `InstanceInfoEntity`")

        // Populate the new tables with default data for existing accounts.
        //
        // Sets up:
        //
        // - InstanceInfoEntity
        // - ServerEntity
        // - ContentFiltersEntity
        val accountCursor = db.query("SELECT id, domain FROM AccountEntity")
        with(accountCursor) {
            while (moveToNext()) {
                val accountId = getLong(0)
                val domain = getString(1)

                val instanceInfoEntityValues = ContentValues().apply {
                    put("instance", domain)
                    put("enabledTranslation", 0)
                }
                db.insert("InstanceInfoEntity", CONFLICT_IGNORE, instanceInfoEntityValues)

                val serverEntityValues = ContentValues().apply {
                    put("accountId", accountId)
                    put("serverKind", "UNKNOWN")
                    put("version", "0.0.1")
                    put("capabilities", "{}")
                }
                db.insert("ServerEntity", CONFLICT_IGNORE, serverEntityValues)

                val contentFiltersEntityValues = ContentValues().apply {
                    put("accountId", accountId)
                    put("version", ContentFilterVersion.V1.name)
                    put("contentFilters", "[]")
                }
                db.insert("ContentFiltersEntity", CONFLICT_IGNORE, contentFiltersEntityValues)
            }
        }
    }
}

/**
 * Adds the FK constraint between NotificationEntity and TimelineAccountEntity.
 *
 * The relevant TimelineAccountEntity rows may not exist in the database, so
 * recreate NotificationEntity with the new constraint but do not migrate the
 * data. It's a cache, so the app will refetch notifications on launch.
 */
val MIGRATE_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `_new_NotificationEntity` (`pachliAccountId` INTEGER NOT NULL, `serverId` TEXT NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `accountServerId` TEXT NOT NULL, `statusServerId` TEXT, PRIMARY KEY(`pachliAccountId`, `serverId`), FOREIGN KEY(`pachliAccountId`) REFERENCES `AccountEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`accountServerId`, `pachliAccountId`) REFERENCES `TimelineAccountEntity`(`serverId`, `timelineUserId`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("DROP TABLE `NotificationEntity`")
        db.execSQL("ALTER TABLE `_new_NotificationEntity` RENAME TO `NotificationEntity`")
    }
}

/**
 * Removes any StatusEntity that reference a non-existent [AccountEntity.id] in
 * [StatusEntity.timelineUserId].
 *
 * Version 20 introduces that as an FK constraint, this ensures that any statuses
 * in the cache that break that constraint are removed.
 */
val MIGRATE_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
DELETE
FROM StatusEntity
WHERE timelineUserId NOT IN (SELECT id FROM AccountEntity)
            """.trimIndent(),
        )
    }
}
