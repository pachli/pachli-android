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

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import app.pachli.core.database.dao.AccountDao
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.database.dao.InstanceDao
import app.pachli.core.database.dao.LogEntryDao
import app.pachli.core.database.dao.RemoteKeyDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.dao.TranslatedStatusDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.database.model.InstanceEntity
import app.pachli.core.database.model.LogEntryEntity
import app.pachli.core.database.model.RemoteKeyEntity
import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TranslatedStatusEntity

@Suppress("ClassName")
@Database(
    entities = [
        DraftEntity::class,
        AccountEntity::class,
        InstanceEntity::class,
        TimelineStatusEntity::class,
        TimelineAccountEntity::class,
        ConversationEntity::class,
        RemoteKeyEntity::class,
        StatusViewDataEntity::class,
        TranslatedStatusEntity::class,
        LogEntryEntity::class,
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.MIGRATE_1_2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
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

    @DeleteColumn("TimelineStatusEntity", "expanded")
    @DeleteColumn("TimelineStatusEntity", "contentCollapsed")
    @DeleteColumn("TimelineStatusEntity", "contentShowing")
    class MIGRATE_1_2 : AutoMigrationSpec
}
