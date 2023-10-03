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

package app.pachli.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent

@InstallIn(FragmentComponent::class)
@Module
abstract class FragmentBuildersModule {
//    @ContributesAndroidInjector
//    abstract fun accountListFragment(): AccountListFragment

//    @ContributesAndroidInjector
//    abstract fun accountMediaFragment(): AccountMediaFragment

//    @ContributesAndroidInjector
//    abstract fun viewThreadFragment(): ViewThreadFragment

//    @ContributesAndroidInjector
//    abstract fun viewEditsFragment(): ViewEditsFragment

//    @ContributesAndroidInjector
//    abstract fun timelineFragment(): TimelineFragment

//    @ContributesAndroidInjector
//    abstract fun notificationsFragment(): NotificationsFragment

//    @ContributesAndroidInjector
//    abstract fun notificationPreferencesFragment(): NotificationPreferencesFragment

//    @ContributesAndroidInjector
//    abstract fun accountPreferencesFragment(): AccountPreferencesFragment

//    @ContributesAndroidInjector
//    abstract fun conversationsFragment(): ConversationsFragment

//    @ContributesAndroidInjector
//    abstract fun accountInListsFragment(): AccountsInListFragment

//    @ContributesAndroidInjector
//    abstract fun reportStatusesFragment(): ReportStatusesFragment

//    @ContributesAndroidInjector
//    abstract fun reportNoteFragment(): ReportNoteFragment

//    @ContributesAndroidInjector
//    abstract fun reportDoneFragment(): ReportDoneFragment

//    @ContributesAndroidInjector
//    abstract fun instanceListFragment(): InstanceListFragment

//    @ContributesAndroidInjector
//    abstract fun searchStatusesFragment(): SearchStatusesFragment

//    @ContributesAndroidInjector
//    abstract fun searchAccountFragment(): SearchAccountsFragment

//    @ContributesAndroidInjector
//    abstract fun searchHashtagsFragment(): SearchHashtagsFragment

//    @ContributesAndroidInjector
//    abstract fun preferencesFragment(): PreferencesFragment

//    @ContributesAndroidInjector
//    abstract fun listsForAccountFragment(): ListsForAccountFragment

//    @ContributesAndroidInjector
//    abstract fun trendingTagsFragment(): TrendingTagsFragment

//    @ContributesAndroidInjector
//    abstract fun trendingLinksFragment(): TrendingLinksFragment

//    @ContributesAndroidInjector
//    abstract fun viewVideoFragment(): ViewVideoFragment
}
