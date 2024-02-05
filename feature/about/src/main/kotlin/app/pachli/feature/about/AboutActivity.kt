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

package app.pachli.feature.about

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.designsystem.R as DR
import app.pachli.core.ui.reduceSwipeSensitivity
import app.pachli.feature.about.databinding.ActivityAboutBinding
import com.bumptech.glide.request.target.FixedSizeDrawable
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.aboutlibraries.LibsBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BottomSheetActivity(), MenuProvider {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.run {
            val navIconSize = resources.getDimensionPixelSize(DR.dimen.avatar_toolbar_nav_icon_size)
            navigationIcon = FixedSizeDrawable(
                AppCompatResources.getDrawable(this@AboutActivity, DR.mipmap.ic_launcher),
                navIconSize,
                navIconSize,
            )
        }
        supportActionBar?.run {
            setTitle(R.string.app_name)
            setDisplayShowHomeEnabled(true)
        }

        val adapter = AboutFragmentAdapter(this)
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
}

class AboutFragmentAdapter(val activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AboutFragment.newInstance()
            1 -> LibsBuilder().supportFragment()
            2 -> PrivacyPolicyFragment.newInstance()
            else -> throw IllegalStateException()
        }
    }

    fun title(position: Int): CharSequence {
        return when (position) {
            0 -> "About"
            1 -> activity.getString(R.string.title_licenses)
            2 -> activity.getString(R.string.about_privacy_policy)
            else -> throw IllegalStateException()
        }
    }
}
