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

package app.pachli.components.notifications

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import app.pachli.R
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.databinding.ActivityNotificationsBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.util.unsafeLazy
import com.google.android.material.appbar.AppBarLayout

class NotificationsActivity : BottomSheetActivity(), ActionButtonActivity, AppBarLayoutHost, MenuProvider {
    private val binding by viewBinding(ActivityNotificationsBinding::inflate)

    override val actionButton by unsafeLazy { binding.composeButton }

    override val appBarLayout: AppBarLayout
        get() = binding.includedToolbar.appbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.run {
            setTitle(R.string.title_notifications)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.commit {
                val fragment = NotificationsFragment.newInstance()
                replace(R.id.fragmentContainer, fragment)
            }
        }

        binding.composeButton.setOnClickListener {
            val composeIntent = ComposeActivityIntent(applicationContext)
            startActivity(composeIntent)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_trending, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return super.onOptionsItemSelected(menuItem)
    }
}
