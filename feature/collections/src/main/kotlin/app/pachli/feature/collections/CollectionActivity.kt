/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.collections

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.commit
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.collections.databinding.ActivityCollectionBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Displays the details for a single [app.pachli.core.model.Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionActivity : ViewUrlActivity() {
    private val binding by viewBinding(ActivityCollectionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.appBar.applyDefaultWindowInsets()
        binding.toolbar.addScrollEffect(FadeChildScrollEffect)

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle("Collection...")
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setOnClickListener {
            binding.fragmentContainer.getFragment<CollectionFragment?>()?.onReselect()
        }

        val pachliAccountId = intent.pachliAccountId
        val collectionId = CollectionActivityIntent.getCollection(intent).serverId

        val fragmentTag = CollectionFragment.fragmentTag(pachliAccountId, collectionId)

        val fragment =
            supportFragmentManager.findFragmentByTag(fragmentTag) as CollectionFragment?
                ?: CollectionFragment.newInstance(pachliAccountId, collectionId)

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }
    }
}
