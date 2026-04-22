/* Copyright 2020 Tusky Contributors
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

package app.pachli.feature.drafts

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.drafts.databinding.ActivityDraftsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DraftsActivity : BaseActivity() {
    private val binding by viewBinding(ActivityDraftsBinding::inflate)

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_drafts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        addMenuProvider(this)

        binding.includedToolbar.toolbar.setOnClickListener {
            (binding.fragmentContainer.getFragment<DraftsFragment>() as? ReselectableFragment)?.onReselect()
        }

        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG + pachliAccountId) as DraftsFragment?
            ?: DraftsFragment.newInstance(pachliAccountId)

        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, fragment, FRAGMENT_TAG + pachliAccountId)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(app.pachli.core.ui.R.menu.action_add_to_tab, menu)
        return super.onCreateMenu(menu, menuInflater)
    }

    override fun onPrepareMenu(menu: Menu) {
        val currentTabs = accountManager.activeAccount?.tabPreferences.orEmpty()
        val hideMenu = currentTabs.contains(Timeline.Drafts)
        menu.findItem(app.pachli.core.ui.R.id.action_add_to_tab)?.isVisible = !hideMenu
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            app.pachli.core.ui.R.id.action_add_to_tab -> {
                addToTab()
                Toast.makeText(this, getString(app.pachli.core.ui.R.string.action_add_to_tab_success, supportActionBar?.title), Toast.LENGTH_LONG).show()
                menuItem.isVisible = false
                true
            }

            else -> super.onMenuItemSelected(menuItem)
        }
    }

    private fun addToTab() {
        accountManager.activeAccount?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                accountManager.setTabPreferences(it.id, it.tabPreferences + Timeline.Drafts)
            }
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "DraftsFragment_"
    }
}
