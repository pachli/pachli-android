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
import androidx.activity.viewModels
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.collections.databinding.ActivityCollectionBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Displays the details for a single [app.pachli.core.model.Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionActivity : ViewUrlActivity() {
    private val binding by viewBinding(ActivityCollectionBinding::inflate)

    private val viewModel by viewModels<CollectionViewModel>()

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

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.collectionViewData.collectLatest(::bindCollection)
            }
        }
    }

    private fun bindCollection(result: Result<Loadable<ICollectionViewModel.CollectionViewData>, ICollectionViewModel.UiError.GetCollection>) {
        // Only update on success
        val collectionViewData = result.get()?.getOrNull() ?: return

        supportActionBar?.title = collectionViewData.collection.name
        supportActionBar?.subtitle = collectionViewData.owner?.account?.username ?: ""
    }
}
