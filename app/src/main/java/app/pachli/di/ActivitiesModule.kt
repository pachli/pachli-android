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

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@InstallIn(ActivityComponent::class)
@Module
abstract class ActivitiesModule {

//    @ContributesAndroidInjector
//    abstract fun contributesBaseActivity(): BaseActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesMainActivity(): MainActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesAccountActivity(): AccountActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesListsActivity(): ListsActivity

//    @ContributesAndroidInjector
//    abstract fun contributesComposeActivity(): ComposeActivity

//    @ContributesAndroidInjector
//    abstract fun contributesEditProfileActivity(): EditProfileActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesAccountListActivity(): AccountListActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesViewThreadActivity(): ViewThreadActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesStatusListActivity(): StatusListActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesSearchActivity(): SearchActivity

//    @ContributesAndroidInjector
//    abstract fun contributesAboutActivity(): AboutActivity

//    @ContributesAndroidInjector
//    abstract fun contributesLoginActivity(): LoginActivity

//    @ContributesAndroidInjector
//    abstract fun contributesLoginWebViewActivity(): LoginWebViewActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesPreferencesActivity(): PreferencesActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesViewMediaActivity(): ViewMediaActivity

//    @ContributesAndroidInjector
//    abstract fun contributesLicenseActivity(): LicenseActivity

//    @ContributesAndroidInjector
//    abstract fun contributesTabPreferenceActivity(): TabPreferenceActivity

//    @ContributesAndroidInjector
//    abstract fun contributesFiltersActivity(): FiltersActivity

//    @ContributesAndroidInjector
//    abstract fun contributesFollowedTagsActivity(): FollowedTagsActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesReportActivity(): ReportActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesInstanceListActivity(): InstanceListActivity

//    @ContributesAndroidInjector
//    abstract fun contributesScheduledStatusActivity(): ScheduledStatusActivity

//    @ContributesAndroidInjector
//    abstract fun contributesAnnouncementsActivity(): AnnouncementsActivity

//    @ContributesAndroidInjector
//    abstract fun contributesDraftActivity(): DraftsActivity

//    @ContributesAndroidInjector
//    abstract fun contributesSplashActivity(): SplashActivity

//    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
//    abstract fun contributesTrendingActivity(): TrendingActivity

//    @ContributesAndroidInjector
//    abstract fun contributesEditFilterActivity(): EditFilterActivity

//    @ContributesAndroidInjector
//    abstract fun contributesPrivacyPolicyActivity(): PrivacyPolicyActivity
}
