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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.pachli.R
import app.pachli.components.timeline.TimelineFragment
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.network.model.TimelineKind
import app.pachli.databinding.ActivityTrendingBinding
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.util.reduceSwipeSensitivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrendingActivity : BottomSheetActivity(), AppBarLayoutHost, MenuProvider {
    private val binding: ActivityTrendingBinding by viewBinding(ActivityTrendingBinding::inflate)

    override val appBarLayout: AppBarLayout
        get() = binding.appBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        supportActionBar?.run {
            setTitle(R.string.title_public_trending)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val adapter = TrendingFragmentAdapter(this)
        binding.pager.adapter = adapter
        binding.pager.reduceSwipeSensitivity()

        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = adapter.title(position)
        }.attach()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                @SuppressLint("SyntheticAccessor")
                override fun handleOnBackPressed() {
                    if (binding.pager.currentItem != 0) binding.pager.currentItem = 0 else finish()
                }
            },
        )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_trending, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return super.onOptionsItemSelected(menuItem)
    }
}

class TrendingFragmentAdapter(val activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TrendingTagsFragment.newInstance()
            1 -> TrendingLinksFragment.newInstance()
            2 -> TimelineFragment.newInstance(TimelineKind.TrendingStatuses)
            else -> throw IllegalStateException()
        }
    }

    fun title(position: Int): CharSequence {
        return when (position) {
            0 -> activity.getString(R.string.title_tab_public_trending_hashtags)
            1 -> activity.getString(R.string.title_tab_public_trending_links)
            2 -> activity.getString(R.string.title_tab_public_trending_statuses)
            else -> throw IllegalStateException()
        }
    }
}
