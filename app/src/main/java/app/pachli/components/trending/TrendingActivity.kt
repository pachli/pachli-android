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

package app.pachli.components.trending

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import app.pachli.R
import app.pachli.TabViewData
import app.pachli.appstore.EventHub
import app.pachli.appstore.MainTabsChangedEvent
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.model.Timeline
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.databinding.ActivityTrendingBinding
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.pager.MainPagerAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrendingActivity : BottomSheetActivity(), AppBarLayoutHost, MenuProvider {
    @Inject
    lateinit var eventHub: EventHub

    private val binding: ActivityTrendingBinding by viewBinding(ActivityTrendingBinding::inflate)

    override val appBarLayout: AppBarLayout
        get() = binding.appBar

    private lateinit var adapter: MainPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        addMenuProvider(this)

        setSupportActionBar(binding.toolbar)

        supportActionBar?.run {
            setTitle(R.string.title_public_trending)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        adapter = MainPagerAdapter(
            listOf(
                TabViewData.from(Timeline.TrendingHashtags),
                TabViewData.from(Timeline.TrendingLinks),
                TabViewData.from(Timeline.TrendingStatuses),
            ),
            this,
        )

        binding.pager.adapter = adapter
        binding.pager.reduceSwipeSensitivity()

        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            // Use a shorter tab label, as "Trending" is already in the toolbar
            tab.text = when (position) {
                0 -> getString(R.string.title_tab_public_trending_hashtags)
                1 -> getString(R.string.title_tab_public_trending_links)
                2 -> getString(R.string.title_tab_public_trending_statuses)
                else -> throw IllegalStateException()
            }
        }.attach()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.pager.currentItem != 0) binding.pager.currentItem = 0 else finish()
                }
            },
        )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_trending, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        val timeline = adapter.tabs[binding.pager.currentItem].timeline
        // Check if this timeline is in a tab; if not, enable the add_to_tab menu item
        val currentTabs = accountManager.activeAccount?.tabPreferences.orEmpty()
        val hideMenu = currentTabs.contains(timeline)
        menu.findItem(R.id.action_add_to_tab)?.setVisible(!hideMenu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.action_add_to_tab -> {
            val tabViewData = adapter.tabs[binding.pager.currentItem]
            val timeline = tabViewData.timeline
            accountManager.activeAccount?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    it.tabPreferences += timeline
                    accountManager.saveAccount(it)
                    eventHub.dispatch(MainTabsChangedEvent(it.tabPreferences))
                }
            }
            Toast.makeText(this, getString(R.string.action_add_to_tab_success, tabViewData.title(this)), Toast.LENGTH_LONG).show()
            menuItem.setVisible(false)
            true
        }
        else -> super.onMenuItemSelected(menuItem)
    }
}
