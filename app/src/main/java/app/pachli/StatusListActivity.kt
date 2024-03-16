/*
 * Copyright 2019 Tusky Contributors
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
 * see <https://www.gnu.org/licenses>.
 */

package app.pachli

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.pachli.appstore.EventHub
import app.pachli.appstore.FilterChangedEvent
import app.pachli.components.timeline.TimelineFragment
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.StatusListActivityIntent
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_CLIENT
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_FILTERS_SERVER
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.model.TimelineKind
import app.pachli.databinding.ActivityStatuslistBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.network.ServerRepository
import at.connyduck.calladapter.networkresult.fold
import com.github.michaelbull.result.getOrElse
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

/**
 * Show a list of statuses of a particular type; containing a particular hashtag,
 * the user's favourites, bookmarks, etc.
 */
@AndroidEntryPoint
class StatusListActivity : BottomSheetActivity(), AppBarLayoutHost, ActionButtonActivity {
    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var serverRepository: ServerRepository

    private val binding: ActivityStatuslistBinding by viewBinding(ActivityStatuslistBinding::inflate)
    private lateinit var timelineKind: TimelineKind

    override val appBarLayout: AppBarLayout
        get() = binding.includedToolbar.appbar

    override val actionButton: FloatingActionButton? by unsafeLazy { binding.composeButton }

    /**
     * If showing statuses with a hashtag, the hashtag being used, without the
     * leading `#`.
     */
    private var hashtag: String? = null
    private var followTagItem: MenuItem? = null
    private var unfollowTagItem: MenuItem? = null
    private var muteTagItem: MenuItem? = null
    private var unmuteTagItem: MenuItem? = null

    /** The filter muting hashtag, null if unknown or hashtag is not filtered */
    private var mutedFilterV1: FilterV1? = null
    private var mutedFilter: Filter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        timelineKind = StatusListActivityIntent.getKind(intent)

        val title = when (timelineKind) {
            is TimelineKind.Favourites -> getString(R.string.title_favourites)
            is TimelineKind.Bookmarks -> getString(R.string.title_bookmarks)
            is TimelineKind.Tag -> {
                hashtag = (timelineKind as TimelineKind.Tag).tags.first()
                getString(R.string.title_tag).format(hashtag)
            }
            is TimelineKind.UserList -> (timelineKind as TimelineKind.UserList).title
            else -> "Missing title!!!"
        }

        supportActionBar?.run {
            setTitle(title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.commit {
                val fragment = TimelineFragment.newInstance(timelineKind)
                replace(R.id.fragmentContainer, fragment)
            }
        }

        val composeIntent = when (timelineKind) {
            is TimelineKind.Tag -> {
                val tag = (timelineKind as TimelineKind.Tag).tags.first()
                ComposeActivityIntent(
                    this,
                    ComposeOptions(
                        content = getString(R.string.title_tag_with_initial_position).format(tag),
                        initialCursorPosition = ComposeOptions.InitialCursorPosition.START,
                    ),
                )
            }
            is TimelineKind.Bookmarks,
            is TimelineKind.Favourites,
            is TimelineKind.UserList,
            -> {
                ComposeActivityIntent(this, ComposeOptions())
            }
            else -> null
        }

        if (composeIntent == null) {
            binding.composeButton.hide()
        } else {
            binding.composeButton.setOnClickListener { startActivity(composeIntent) }
            binding.composeButton.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val tag = hashtag
        if (timelineKind is TimelineKind.Tag && tag != null) {
            lifecycleScope.launch {
                mastodonApi.tag(tag).fold(
                    { tagEntity ->
                        menuInflater.inflate(R.menu.view_hashtag_toolbar, menu)
                        followTagItem = menu.findItem(R.id.action_follow_hashtag)
                        unfollowTagItem = menu.findItem(R.id.action_unfollow_hashtag)
                        muteTagItem = menu.findItem(R.id.action_mute_hashtag)
                        unmuteTagItem = menu.findItem(R.id.action_unmute_hashtag)
                        followTagItem?.isVisible = tagEntity.following == false
                        unfollowTagItem?.isVisible = tagEntity.following == true
                        followTagItem?.setOnMenuItemClickListener { followTag() }
                        unfollowTagItem?.setOnMenuItemClickListener { unfollowTag() }
                        muteTagItem?.setOnMenuItemClickListener { muteTag() }
                        unmuteTagItem?.setOnMenuItemClickListener { unmuteTag() }
                        updateMuteTagMenuItems()
                    },
                    {
                        Timber.w(it, "Failed to query tag #%s", tag)
                    },
                )
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun followTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            lifecycleScope.launch {
                mastodonApi.followTag(tag).fold(
                    {
                        followTagItem?.isVisible = false
                        unfollowTagItem?.isVisible = true
                    },
                    {
                        Snackbar.make(binding.root, getString(R.string.error_following_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Timber.e(it, "Failed to follow #%s", tag)
                    },
                )
            }
        }

        return true
    }

    private fun unfollowTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            lifecycleScope.launch {
                mastodonApi.unfollowTag(tag).fold(
                    {
                        followTagItem?.isVisible = true
                        unfollowTagItem?.isVisible = false
                    },
                    {
                        Snackbar.make(binding.root, getString(R.string.error_unfollowing_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Timber.e(it, "Failed to unfollow #%s", tag)
                    },
                )
            }
        }

        return true
    }

    /**
     * Determine if the current hashtag is muted, and update the UI state accordingly.
     */
    private fun updateMuteTagMenuItems() {
        val tagWithHash = hashtag?.let { "#$it" } ?: return

        // If there's no server info, or the server can't filter then it's impossible
        // to mute hashtags, so disable the functionality.
        val server = serverRepository.flow.value.getOrElse { null }
        if (server == null || (
                !server.can(ORG_JOINMASTODON_FILTERS_CLIENT, ">=1.0.0".toConstraint()) &&
                    !server.can(ORG_JOINMASTODON_FILTERS_SERVER, ">=1.0.0".toConstraint())
                )
        ) {
            muteTagItem?.isVisible = false
            unmuteTagItem?.isVisible = false
            return
        }

        muteTagItem?.isVisible = true
        muteTagItem?.isEnabled = false
        unmuteTagItem?.isVisible = false

        lifecycleScope.launch {
            mastodonApi.getFilters().fold(
                { filters ->
                    mutedFilter = filters.firstOrNull { filter ->
                        filter.contexts.contains(FilterContext.HOME) && filter.keywords.any {
                            it.keyword == tagWithHash
                        }
                    }
                    updateTagMuteState(mutedFilter != null)
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        mastodonApi.getFiltersV1().fold(
                            { filters ->
                                mutedFilterV1 = filters.firstOrNull { filter ->
                                    tagWithHash == filter.phrase && filter.contexts.contains(FilterContext.HOME)
                                }
                                updateTagMuteState(mutedFilterV1 != null)
                            },
                            { throwable ->
                                Timber.e(throwable, "Error getting filters")
                            },
                        )
                    } else {
                        Timber.e(throwable, "Error getting filters")
                    }
                },
            )
        }
    }

    private fun updateTagMuteState(muted: Boolean) {
        if (muted) {
            muteTagItem?.isVisible = false
            muteTagItem?.isEnabled = false
            unmuteTagItem?.isVisible = true
        } else {
            unmuteTagItem?.isVisible = false
            muteTagItem?.isEnabled = true
            muteTagItem?.isVisible = true
        }
    }

    private fun muteTag(): Boolean {
        val tagWithHash = hashtag?.let { "#$it" } ?: return true

        lifecycleScope.launch {
            mastodonApi.createFilter(
                title = tagWithHash,
                context = listOf(FilterContext.HOME),
                filterAction = Filter.Action.WARN,
                expiresInSeconds = null,
            ).fold(
                { filter ->
                    if (mastodonApi.addFilterKeyword(filterId = filter.id, keyword = tagWithHash, wholeWord = true).isSuccess) {
                        mutedFilter = filter
                        updateTagMuteState(true)
                        eventHub.dispatch(FilterChangedEvent(filter.contexts[0]))
                        Snackbar.make(binding.root, getString(R.string.confirmation_hashtag_muted, hashtag), Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                        Timber.e("Failed to mute %s", tagWithHash)
                    }
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        mastodonApi.createFilterV1(
                            tagWithHash,
                            listOf(FilterContext.HOME),
                            irreversible = false,
                            wholeWord = true,
                            expiresInSeconds = null,
                        ).fold(
                            { filter ->
                                mutedFilterV1 = filter
                                updateTagMuteState(true)
                                eventHub.dispatch(FilterChangedEvent(filter.contexts[0]))
                                Snackbar.make(binding.root, getString(R.string.confirmation_hashtag_muted, hashtag), Snackbar.LENGTH_SHORT).show()
                            },
                            { throwable ->
                                Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                                Timber.e(throwable, "Failed to mute %s", tagWithHash)
                            },
                        )
                    } else {
                        Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                        Timber.e(throwable, "Failed to mute %s", tagWithHash)
                    }
                },
            )
        }

        return true
    }

    private fun unmuteTag(): Boolean {
        lifecycleScope.launch {
            val tagWithHash = hashtag?.let { "#$it" } ?: return@launch

            val result = if (mutedFilter != null) {
                val filter = mutedFilter!!
                if (filter.contexts.size > 1) {
                    // This filter exists in multiple contexts, just remove the home context
                    mastodonApi.updateFilter(
                        id = filter.id,
                        context = filter.contexts.filter { it != FilterContext.HOME },
                    )
                } else {
                    mastodonApi.deleteFilter(filter.id)
                }
            } else if (mutedFilterV1 != null) {
                mutedFilterV1?.let { filter ->
                    if (filter.contexts.size > 1) {
                        // This filter exists in multiple contexts, just remove the home context
                        mastodonApi.updateFilterV1(
                            id = filter.id,
                            phrase = filter.phrase,
                            context = filter.contexts.filter { it != FilterContext.HOME },
                            irreversible = null,
                            wholeWord = null,
                            expiresInSeconds = null,
                        )
                    } else {
                        mastodonApi.deleteFilterV1(filter.id)
                    }
                }
            } else {
                null
            }

            result?.fold(
                {
                    updateTagMuteState(false)
                    Snackbar.make(binding.root, getString(R.string.confirmation_hashtag_unmuted, hashtag), Snackbar.LENGTH_SHORT).show()
                    eventHub.dispatch(FilterChangedEvent(FilterContext.HOME))
                    mutedFilterV1 = null
                    mutedFilter = null
                },
                { throwable ->
                    Snackbar.make(binding.root, getString(R.string.error_unmuting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                    Timber.e(throwable, "Failed to unmute %s", tagWithHash)
                },
            )
        }

        return true
    }
}
