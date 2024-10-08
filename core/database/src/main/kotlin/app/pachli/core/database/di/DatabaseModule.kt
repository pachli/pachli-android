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
        return appDatabase.withTransaction(block)

//        return try {
//            appDatabase.withTransaction(block)
//        } catch (e: Throwable) {
//            Timber.e(e, "Exception when starting database transaction")
//            throw (e)
//        } finally {
//            Timber.e("Finally block of withTransaction")
//        }
    }
}
