/* Copyright 2018 charlag
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.di

import app.pachli.AccountsInListFragment
import app.pachli.components.account.list.ListsForAccountFragment
import app.pachli.components.account.media.AccountMediaFragment
import app.pachli.components.accountlist.AccountListFragment
import app.pachli.components.conversation.ConversationsFragment
import app.pachli.components.instancemute.fragment.InstanceListFragment
import app.pachli.components.notifications.NotificationsFragment
import app.pachli.components.preference.AccountPreferencesFragment
import app.pachli.components.preference.NotificationPreferencesFragment
import app.pachli.components.preference.PreferencesFragment
import app.pachli.components.report.fragments.ReportDoneFragment
import app.pachli.components.report.fragments.ReportNoteFragment
import app.pachli.components.report.fragments.ReportStatusesFragment
import app.pachli.components.search.fragments.SearchAccountsFragment
import app.pachli.components.search.fragments.SearchHashtagsFragment
import app.pachli.components.search.fragments.SearchStatusesFragment
import app.pachli.components.timeline.TimelineFragment
import app.pachli.components.trending.TrendingLinksFragment
import app.pachli.components.trending.TrendingTagsFragment
import app.pachli.components.viewthread.ViewThreadFragment
import app.pachli.components.viewthread.edits.ViewEditsFragment
import app.pachli.fragment.ViewVideoFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class FragmentBuildersModule {
    @ContributesAndroidInjector
    abstract fun accountListFragment(): AccountListFragment

    @ContributesAndroidInjector
    abstract fun accountMediaFragment(): AccountMediaFragment

    @ContributesAndroidInjector
    abstract fun viewThreadFragment(): ViewThreadFragment

    @ContributesAndroidInjector
    abstract fun viewEditsFragment(): ViewEditsFragment

    @ContributesAndroidInjector
    abstract fun timelineFragment(): TimelineFragment

    @ContributesAndroidInjector
    abstract fun notificationsFragment(): NotificationsFragment

    @ContributesAndroidInjector
    abstract fun notificationPreferencesFragment(): NotificationPreferencesFragment

    @ContributesAndroidInjector
    abstract fun accountPreferencesFragment(): AccountPreferencesFragment

    @ContributesAndroidInjector
    abstract fun conversationsFragment(): ConversationsFragment

    @ContributesAndroidInjector
    abstract fun accountInListsFragment(): AccountsInListFragment

    @ContributesAndroidInjector
    abstract fun reportStatusesFragment(): ReportStatusesFragment

    @ContributesAndroidInjector
    abstract fun reportNoteFragment(): ReportNoteFragment

    @ContributesAndroidInjector
    abstract fun reportDoneFragment(): ReportDoneFragment

    @ContributesAndroidInjector
    abstract fun instanceListFragment(): InstanceListFragment

    @ContributesAndroidInjector
    abstract fun searchStatusesFragment(): SearchStatusesFragment

    @ContributesAndroidInjector
    abstract fun searchAccountFragment(): SearchAccountsFragment

    @ContributesAndroidInjector
    abstract fun searchHashtagsFragment(): SearchHashtagsFragment

    @ContributesAndroidInjector
    abstract fun preferencesFragment(): PreferencesFragment

    @ContributesAndroidInjector
    abstract fun listsForAccountFragment(): ListsForAccountFragment

    @ContributesAndroidInjector
    abstract fun trendingTagsFragment(): TrendingTagsFragment

    @ContributesAndroidInjector
    abstract fun trendingLinksFragment(): TrendingLinksFragment

    @ContributesAndroidInjector
    abstract fun viewVideoFragment(): ViewVideoFragment
}
