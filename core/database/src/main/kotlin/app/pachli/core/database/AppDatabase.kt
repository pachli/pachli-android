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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.ContentFiltersDao
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.ContentFiltersEntity
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.database.model.EmojisEntity
import app.pachli.core.database.model.InstanceInfoEntity
import app.pachli.core.database.model.LogEntryEntity
import app.pachli.core.database.model.MastodonListEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.ServerCapabilitiesEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TranslatedStatusEntity
import app.pachli.core.model.ContentFilterVersion

@Suppress("ClassName")
@Database(
    entities = [
        DraftEntity::class,
        AccountEntity::class,
        InstanceInfoEntity::class,
        EmojisEntity::class,
        TimelineStatusEntity::class,
        TimelineAccountEntity::class,
        ConversationEntity::class,
        RemoteKeyEntity::class,
        StatusViewDataEntity::class,
        TranslatedStatusEntity::class,
        LogEntryEntity::class,
        MastodonListEntity::class,
        ServerCapabilitiesEntity::class,
        ContentFiltersEntity::class,
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.MIGRATE_1_2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = AppDatabase.MIGRATE_5_6::class),
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

    @DeleteColumn("TimelineStatusEntity", "expanded")
    @DeleteColumn("TimelineStatusEntity", "contentCollapsed")
    @DeleteColumn("TimelineStatusEntity", "contentShowing")
    class MIGRATE_1_2 : AutoMigrationSpec

    @DeleteColumn("InstanceEntity", "emojiList")
    @RenameColumn(
        "InstanceEntity",
        fromColumnName = "maximumTootCharacters",
        toColumnName = "maxPostCharacters",
    )
    @RenameTable(fromTableName = "InstanceEntity", toTableName = "InstanceInfoEntity")
    class MIGRATE_5_6 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()

            // Create InstanceInfoEntity and ServerCapabilitiesEntity for each account.
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

                    val serverCapabilitiesEntityValues = ContentValues().apply {
                        put("accountId", accountId)
                        put("capabilities", "{}")
                    }
                    db.insert("ServerCapabilitiesEntity", CONFLICT_IGNORE, serverCapabilitiesEntityValues)

                    val contentFiltersEntityValues = ContentValues().apply {
                        put("accountId", accountId)
                        put("version", ContentFilterVersion.V1.name)
                        put("contentFilters", "[]")
                    }
                    db.insert("ContentFiltersEntity", CONFLICT_IGNORE, contentFiltersEntityValues)
                }
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }
    }
}
