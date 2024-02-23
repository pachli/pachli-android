/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.common.di

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object SystemServiceModule {
    @Provides
    @Singleton
    fun providesPowerManager(application: Application) = application.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides
    @Singleton
    fun providesUsageStatsManager(application: Application) = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
}
