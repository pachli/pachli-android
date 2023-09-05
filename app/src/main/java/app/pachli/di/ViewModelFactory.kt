/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

// from https://proandroiddev.com/viewmodel-with-dagger2-architecture-components-2e06f06c9455

package app.pachli.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.pachli.components.account.AccountViewModel
import app.pachli.components.account.list.ListsForAccountViewModel
import app.pachli.components.account.media.AccountMediaViewModel
import app.pachli.components.announcements.AnnouncementsViewModel
import app.pachli.components.compose.ComposeViewModel
import app.pachli.components.conversation.ConversationsViewModel
import app.pachli.components.drafts.DraftsViewModel
import app.pachli.components.filters.EditFilterViewModel
import app.pachli.components.filters.FiltersViewModel
import app.pachli.components.followedtags.FollowedTagsViewModel
import app.pachli.components.login.LoginWebViewViewModel
import app.pachli.components.notifications.NotificationsViewModel
import app.pachli.components.report.ReportViewModel
import app.pachli.components.scheduled.ScheduledStatusViewModel
import app.pachli.components.search.SearchViewModel
import app.pachli.components.timeline.viewmodel.CachedTimelineViewModel
import app.pachli.components.timeline.viewmodel.NetworkTimelineViewModel
import app.pachli.components.trending.viewmodel.TrendingLinksViewModel
import app.pachli.components.trending.viewmodel.TrendingTagsViewModel
import app.pachli.components.viewthread.ViewThreadViewModel
import app.pachli.components.viewthread.edits.ViewEditsViewModel
import app.pachli.viewmodel.AccountsInListViewModel
import app.pachli.viewmodel.EditProfileViewModel
import app.pachli.viewmodel.ListsViewModel
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class ViewModelFactory @Inject constructor(private val viewModels: MutableMap<Class<out ViewModel>, Provider<ViewModel>>) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Module
abstract class ViewModelModule {

    @Binds
    internal abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(AccountViewModel::class)
    internal abstract fun accountViewModel(viewModel: AccountViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EditProfileViewModel::class)
    internal abstract fun editProfileViewModel(viewModel: EditProfileViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationsViewModel::class)
    internal abstract fun conversationsViewModel(viewModel: ConversationsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ListsViewModel::class)
    internal abstract fun listsViewModel(viewModel: ListsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AccountsInListViewModel::class)
    internal abstract fun accountsInListViewModel(viewModel: AccountsInListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReportViewModel::class)
    internal abstract fun reportViewModel(viewModel: ReportViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SearchViewModel::class)
    internal abstract fun searchViewModel(viewModel: SearchViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ComposeViewModel::class)
    internal abstract fun composeViewModel(viewModel: ComposeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ScheduledStatusViewModel::class)
    internal abstract fun scheduledStatusViewModel(viewModel: ScheduledStatusViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AnnouncementsViewModel::class)
    internal abstract fun announcementsViewModel(viewModel: AnnouncementsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DraftsViewModel::class)
    internal abstract fun draftsViewModel(viewModel: DraftsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CachedTimelineViewModel::class)
    internal abstract fun cachedTimelineViewModel(viewModel: CachedTimelineViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(NetworkTimelineViewModel::class)
    internal abstract fun networkTimelineViewModel(viewModel: NetworkTimelineViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ViewThreadViewModel::class)
    internal abstract fun viewThreadViewModel(viewModel: ViewThreadViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ViewEditsViewModel::class)
    internal abstract fun viewEditsViewModel(viewModel: ViewEditsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AccountMediaViewModel::class)
    internal abstract fun accountMediaViewModel(viewModel: AccountMediaViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(LoginWebViewViewModel::class)
    internal abstract fun loginWebViewViewModel(viewModel: LoginWebViewViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FollowedTagsViewModel::class)
    internal abstract fun followedTagsViewModel(viewModel: FollowedTagsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ListsForAccountViewModel::class)
    internal abstract fun listsForAccountViewModel(viewModel: ListsForAccountViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(NotificationsViewModel::class)
    internal abstract fun notificationsViewModel(viewModel: NotificationsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TrendingTagsViewModel::class)
    internal abstract fun trendingTagsViewModel(viewModel: TrendingTagsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TrendingLinksViewModel::class)
    internal abstract fun trendingLinksViewModel(viewModel: TrendingLinksViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FiltersViewModel::class)
    internal abstract fun filtersViewModel(viewModel: FiltersViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EditFilterViewModel::class)
    internal abstract fun editFilterViewModel(viewModel: EditFilterViewModel): ViewModel

    // Add more ViewModels here
}
