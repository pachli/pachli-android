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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.pachli.core.database.AppDatabase
import app.pachli.core.database.Converters
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
@Module
object FakeDatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(moshi: Moshi): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
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
}
