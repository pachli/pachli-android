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

package app.pachli.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.Converters
import app.pachli.core.database.MIGRATE_10_11
import app.pachli.core.database.MIGRATE_12_13
import app.pachli.core.database.MIGRATE_18_19
import app.pachli.core.database.MIGRATE_22_23
import app.pachli.core.database.MIGRATE_8_9
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext appContext: Context,
        converters: Converters,
    ): AppDatabase {
        return Room.databaseBuilder(appContext, AppDatabase::class.java, "pachliDB")
            .addTypeConverter(converters)
            .allowMainThreadQueries()
            .addMigrations(MIGRATE_8_9)
            .addMigrations(MIGRATE_10_11)
            .addMigrations(MIGRATE_12_13)
            .addMigrations(MIGRATE_18_19)
            .addMigrations(MIGRATE_22_23)
            .build()
    }

    @Provides
    @Singleton
    fun provideTransactionProvider(appDatabase: AppDatabase) = TransactionProvider(appDatabase)

    @Provides
    fun provideAccountDao(appDatabase: AppDatabase) = appDatabase.accountDao()

    @Provides
    fun provideInstanceDao(appDatabase: AppDatabase) = appDatabase.instanceDao()

    @Provides
    fun provideConversationsDao(appDatabase: AppDatabase) = appDatabase.conversationDao()

    @Provides
    fun provideTimelineDao(appDatabase: AppDatabase) = appDatabase.timelineDao()

    @Provides
    fun provideDraftDao(appDatabase: AppDatabase) = appDatabase.draftDao()

    @Provides
    fun provideRemoteKeyDao(appDatabase: AppDatabase) = appDatabase.remoteKeyDao()

    @Provides
    fun providesTranslatedStatusDao(appDatabase: AppDatabase) = appDatabase.translatedStatusDao()

    @Provides
    fun providesLogEntryDao(appDatabase: AppDatabase) = appDatabase.logEntryDao()

    @Provides
    fun providesContentFiltersDao(appDatabase: AppDatabase) = appDatabase.contentFiltersDao()

    @Provides
    fun providesListsDao(appDatabase: AppDatabase) = appDatabase.listsDao()

    @Provides
    fun providesAnnouncementsDao(appDatabase: AppDatabase) = appDatabase.announcementsDao()

    @Provides
    fun providesFollowingAccountDao(appDatabase: AppDatabase) = appDatabase.followingAccountDao()

    @Provides
    fun providesNotificationDao(appDatabase: AppDatabase) = appDatabase.notificationDao()

    @Provides
    fun providesStatusDao(appDatabase: AppDatabase) = appDatabase.statusDao()
}

/**
 * Provides `operator` [invoke] function that can be used by classes that
 * need to run operations across multiple DAOs in a single transaction without
 * needing to inject the full [AppDatabase] in to the class.
 *
 * ```
 * class FooRepository @Inject constructor(
 *     private val transactionProvider: TransactionProvider,
 *     private val fooDao: FooDao,
 *     private val barDao: BarDao,
 * ) {
 *     suspend fun doSomething() = transactionProvider {
 *         fooDao.doSomethingWithFoo()
 *         barDao.doSomethingWithBar()
 *     }
 * }
 * ```
 */
class TransactionProvider(private val appDatabase: AppDatabase) {
    /** Runs the given block in a database transaction */
    suspend operator fun <R> invoke(block: suspend () -> R): R {
        return if (appDatabase.inTransaction()) block() else appDatabase.withTransaction(block)
    }

    /** @return True if the current thread is in a transaction. */
    fun inTransaction() = appDatabase.inTransaction()
}
