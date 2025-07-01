/*
 * Copyright 2025 Pachli Association
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

package app.pachli.feature.manageaccounts

import android.os.Bundle
import androidx.fragment.app.commit
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.feature.manageaccounts.databinding.ActivityManageAccountsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ManageAccountsActivity : BaseActivity() {
    override fun requiresLogin() = false

    private val binding by viewBinding(ActivityManageAccountsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.title_manage_accounts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as ManageAccountsFragment?
            ?: ManageAccountsFragment.newInstance()

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG)
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "ManageAccountsFragment"
    }
}
