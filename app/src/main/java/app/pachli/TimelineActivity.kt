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
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.pachli.appstore.EventHub
import app.pachli.appstore.MainTabsChangedEvent
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.NewContentFilterKeyword
import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.data.repository.ContentFiltersError
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.data.repository.NewContentFilter
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.databinding.ActivityTimelineBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import at.connyduck.calladapter.networkresult.fold
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Show a single timeline.
 */
@AndroidEntryPoint
class TimelineActivity : BottomSheetActivity(), AppBarLayoutHost, ActionButtonActivity, MenuProvider {
    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var contentFiltersRepository: ContentFiltersRepository

    private val binding: ActivityTimelineBinding by viewBinding(ActivityTimelineBinding::inflate)
    private lateinit var timeline: Timeline

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
    private var mutedContentFilter: ContentFilter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        addMenuProvider(this)

        timeline = TimelineActivityIntent.getTimeline(intent)
        hashtag = (timeline as? Timeline.Hashtags)?.tags?.firstOrNull()

        val viewData = TabViewData.from(timeline)

        supportActionBar?.run {
            title = viewData.title(this@TimelineActivity)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.commit {
                val fragment = viewData.fragment()
                replace(R.id.fragmentContainer, fragment)
                binding.composeButton.show()
            }
        }

        viewData.composeIntent?.let { intent ->
            binding.composeButton.setOnClickListener { startActivity(intent(this@TimelineActivity)) }
            binding.composeButton.show()
        } ?: binding.composeButton.hide()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_timeline, menu)

        hashtag?.let { tag ->
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
                        updateMuteTagMenuItems(tag)
                    },
                    {
                        Timber.w(it, "Failed to query tag #%s", tag)
                    },
                )
            }
        }

        return super.onCreateMenu(menu, menuInflater)
    }

    override fun onPrepareMenu(menu: Menu) {
        // Check if this timeline is in a tab; if not, enable the add_to_tab menu item
        val currentTabs = accountManager.activeAccount?.tabPreferences.orEmpty()
        val hideMenu = currentTabs.contains(timeline)
        menu.findItem(R.id.action_add_to_tab)?.setVisible(!hideMenu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_add_to_tab -> {
                addToTab()
                Toast.makeText(this, getString(R.string.action_add_to_tab_success, supportActionBar?.title), Toast.LENGTH_LONG).show()
                menuItem.setVisible(false)
                true
            }
            R.id.action_follow_hashtag -> {
                followTag()
                true
            }
            R.id.action_unfollow_hashtag -> {
                unfollowTag()
                true
            }
            R.id.action_mute_hashtag -> {
                muteTag()
                true
            }
            R.id.action_unmute_hashtag -> {
                unmuteTag()
                true
            }
            else -> super.onMenuItemSelected(menuItem)
        }
    }

    private fun addToTab() {
        accountManager.activeAccount?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                accountManager.setTabPreferences(it.id, it.tabPreferences + timeline)
                eventHub.dispatch(MainTabsChangedEvent(it.tabPreferences))
            }
        }
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
     * Determine if the given hashtag is muted, and update the UI state accordingly.
     */
    private fun updateMuteTagMenuItems(tag: String) {
        val tagWithHash = "#$tag"

        muteTagItem?.isVisible = true
        muteTagItem?.isEnabled = false
        unmuteTagItem?.isVisible = false

        lifecycleScope.launch {
            contentFiltersRepository.contentFilters.collect { result ->
                result.onSuccess { filters ->
                    mutedContentFilter = filters?.contentFilters?.firstOrNull { filter ->
                        filter.contexts.contains(FilterContext.HOME) &&
                            filter.keywords.any { it.keyword == tagWithHash }
                    }
                    updateTagMuteState(mutedContentFilter != null)
                }
                result.onFailure { error ->
                    // If the server can't filter then it's impossible to mute hashtags,
                    // so disable the functionality.
                    if (error is ContentFiltersError.ServerDoesNotFilter) {
                        muteTagItem?.isVisible = false
                        unmuteTagItem?.isVisible = false
                    }
                }
            }
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

    private fun muteTag() {
        val tagWithHash = hashtag?.let { "#$it" } ?: return

        lifecycleScope.launch {
            val newContentFilter = NewContentFilter(
                title = tagWithHash,
                contexts = setOf(FilterContext.HOME),
                filterAction = FilterAction.WARN,
                expiresIn = 0,
                keywords = listOf(
                    NewContentFilterKeyword(
                        keyword = tagWithHash,
                        wholeWord = true,
                    ),
                ),
            )

            contentFiltersRepository.createContentFilter(newContentFilter)
                .onSuccess {
                    mutedContentFilter = it
                    updateTagMuteState(true)
                    Snackbar.make(binding.root, getString(R.string.confirmation_hashtag_muted, hashtag), Snackbar.LENGTH_SHORT).show()
                }
                .onFailure {
                    Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                    Timber.e("Failed to mute %s: %s", tagWithHash, it.fmt(this@TimelineActivity))
                }
        }
    }

    private fun unmuteTag() {
        lifecycleScope.launch {
            val tagWithHash = hashtag?.let { "#$it" } ?: return@launch

            val result = mutedContentFilter?.let { filter ->
                val newContexts = filter.contexts.filter { it != FilterContext.HOME }
                if (newContexts.isEmpty()) {
                    contentFiltersRepository.deleteContentFilter(filter.id)
                } else {
                    contentFiltersRepository.updateContentFilter(filter, ContentFilterEdit(filter.id, contexts = newContexts))
                }
            }

            result?.onSuccess {
                updateTagMuteState(false)
                Snackbar.make(binding.root, getString(R.string.confirmation_hashtag_unmuted, hashtag), Snackbar.LENGTH_SHORT).show()
                mutedContentFilter = null
            }?.onFailure { e ->
                Snackbar.make(binding.root, getString(R.string.error_unmuting_hashtag_format, hashtag), Snackbar.LENGTH_SHORT).show()
                Timber.e("Failed to unmute %s: %s", tagWithHash, e.fmt(this@TimelineActivity))
            }
        }
    }
}
