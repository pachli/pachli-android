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

import app.pachli.AboutActivity
import app.pachli.BaseActivity
import app.pachli.EditProfileActivity
import app.pachli.LicenseActivity
import app.pachli.ListsActivity
import app.pachli.MainActivity
import app.pachli.PrivacyPolicyActivity
import app.pachli.SplashActivity
import app.pachli.StatusListActivity
import app.pachli.TabPreferenceActivity
import app.pachli.ViewMediaActivity
import app.pachli.components.account.AccountActivity
import app.pachli.components.accountlist.AccountListActivity
import app.pachli.components.announcements.AnnouncementsActivity
import app.pachli.components.compose.ComposeActivity
import app.pachli.components.drafts.DraftsActivity
import app.pachli.components.filters.EditFilterActivity
import app.pachli.components.filters.FiltersActivity
import app.pachli.components.followedtags.FollowedTagsActivity
import app.pachli.components.instancemute.InstanceListActivity
import app.pachli.components.login.LoginActivity
import app.pachli.components.login.LoginWebViewActivity
import app.pachli.components.preference.PreferencesActivity
import app.pachli.components.report.ReportActivity
import app.pachli.components.scheduled.ScheduledStatusActivity
import app.pachli.components.search.SearchActivity
import app.pachli.components.trending.TrendingActivity
import app.pachli.components.viewthread.ViewThreadActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivitiesModule {

    @ContributesAndroidInjector
    abstract fun contributesBaseActivity(): BaseActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesMainActivity(): MainActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesAccountActivity(): AccountActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesListsActivity(): ListsActivity

    @ContributesAndroidInjector
    abstract fun contributesComposeActivity(): ComposeActivity

    @ContributesAndroidInjector
    abstract fun contributesEditProfileActivity(): EditProfileActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesAccountListActivity(): AccountListActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesViewThreadActivity(): ViewThreadActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesStatusListActivity(): StatusListActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesSearchActivity(): SearchActivity

    @ContributesAndroidInjector
    abstract fun contributesAboutActivity(): AboutActivity

    @ContributesAndroidInjector
    abstract fun contributesLoginActivity(): LoginActivity

    @ContributesAndroidInjector
    abstract fun contributesLoginWebViewActivity(): LoginWebViewActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesPreferencesActivity(): PreferencesActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesViewMediaActivity(): ViewMediaActivity

    @ContributesAndroidInjector
    abstract fun contributesLicenseActivity(): LicenseActivity

    @ContributesAndroidInjector
    abstract fun contributesTabPreferenceActivity(): TabPreferenceActivity

    @ContributesAndroidInjector
    abstract fun contributesFiltersActivity(): FiltersActivity

    @ContributesAndroidInjector
    abstract fun contributesFollowedTagsActivity(): FollowedTagsActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesReportActivity(): ReportActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesInstanceListActivity(): InstanceListActivity

    @ContributesAndroidInjector
    abstract fun contributesScheduledStatusActivity(): ScheduledStatusActivity

    @ContributesAndroidInjector
    abstract fun contributesAnnouncementsActivity(): AnnouncementsActivity

    @ContributesAndroidInjector
    abstract fun contributesDraftActivity(): DraftsActivity

    @ContributesAndroidInjector
    abstract fun contributesSplashActivity(): SplashActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesTrendingActivity(): TrendingActivity

    @ContributesAndroidInjector
    abstract fun contributesEditFilterActivity(): EditFilterActivity

    @ContributesAndroidInjector
    abstract fun contributesPrivacyPolicyActivity(): PrivacyPolicyActivity
}
