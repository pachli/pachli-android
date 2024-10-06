/* Copyright 2017 Andrew Dawson
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

package app.pachli.components.accountlist

import android.os.Bundle
import androidx.fragment.app.commit
import app.pachli.R
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.AccountListActivityIntent
import app.pachli.core.navigation.AccountListActivityIntent.Kind.BLOCKS
import app.pachli.core.navigation.AccountListActivityIntent.Kind.FAVOURITED
import app.pachli.core.navigation.AccountListActivityIntent.Kind.FOLLOWERS
import app.pachli.core.navigation.AccountListActivityIntent.Kind.FOLLOWS
import app.pachli.core.navigation.AccountListActivityIntent.Kind.FOLLOW_REQUESTS
import app.pachli.core.navigation.AccountListActivityIntent.Kind.MUTES
import app.pachli.core.navigation.AccountListActivityIntent.Kind.REBLOGGED
import app.pachli.core.navigation.pachliAccountId
import app.pachli.databinding.ActivityAccountListBinding
import app.pachli.interfaces.AppBarLayoutHost
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint

/**
 * Show a list of accounts of a particular kind.
 */
@AndroidEntryPoint
class AccountListActivity : BottomSheetActivity(), AppBarLayoutHost {
    private val binding: ActivityAccountListBinding by viewBinding(ActivityAccountListBinding::inflate)

    override val appBarLayout: AppBarLayout
        get() = binding.includedToolbar.appbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val kind = AccountListActivityIntent.getKind(intent)
        val id = AccountListActivityIntent.getId(intent)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            when (kind) {
                BLOCKS -> setTitle(R.string.title_blocks)
                MUTES -> setTitle(R.string.title_mutes)
                FOLLOW_REQUESTS -> setTitle(R.string.title_follow_requests)
                FOLLOWERS -> setTitle(R.string.title_followers)
                FOLLOWS -> setTitle(R.string.title_follows)
                REBLOGGED -> setTitle(R.string.title_reblogged_by)
                FAVOURITED -> setTitle(R.string.title_favourited_by)
            }
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, AccountListFragment.newInstance(intent.pachliAccountId, kind, id))
        }
    }
}
