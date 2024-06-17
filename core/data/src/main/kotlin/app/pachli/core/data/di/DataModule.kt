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

package app.pachli.core.data.di

import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.data.repository.NetworkListsRepository
import app.pachli.core.data.repository.NetworkSuggestionsRepository
import app.pachli.core.data.repository.SuggestionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class DataModule {
    @Binds
    internal abstract fun bindsListsRepository(
        listsRepository: NetworkListsRepository,
    ): ListsRepository

    @Binds
    internal abstract fun bindsSuggestionsRepository(
        suggestionsRepository: NetworkSuggestionsRepository,
    ): SuggestionsRepository
}
