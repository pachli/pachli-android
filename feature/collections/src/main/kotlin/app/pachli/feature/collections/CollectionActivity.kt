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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.CollectionsRepository
import app.pachli.core.data.repository.Loadable
import app.pachli.core.model.ICollection
import app.pachli.core.model.TimelineAccount
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.collections.databinding.ActivityCollectionBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Displays the details for a single [app.pachli.core.model.Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionActivity : BaseActivity() {
    private val binding by viewBinding(ActivityCollectionBinding::inflate)

    private val viewModel by viewModels<CollectionViewModel>()

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        // TODO: Probably doesn't need toolbar here (do it all in Compose?).
        // Does need a functioning back button
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)

        setContentView(binding.root)

        viewModel.loadCollection(
            pachliAccountId,
            CollectionActivityIntent.getCollection(intent).serverId,
        )
    }
}

internal data class CollectionViewData(
    val collection: ICollection,
    val accounts: ImmutableList<TimelineAccount>,
)

@HiltViewModel
internal class CollectionViewModel @Inject constructor(
    private val collectionsRepository: CollectionsRepository,
) : ViewModel() {
    internal val collectionViewData = MutableStateFlow<Loadable<CollectionViewData>>(Loadable.Loading)

    fun loadCollection(pachliAccountId: Long, collectionId: String) {
//        Timber.d("loading collection: $collection")
        viewModelScope.launch {
            collectionsRepository.getCollection(pachliAccountId, collectionId).filterNotNull().collect { (collection, accounts) ->
                collectionViewData.value = Loadable.Loaded(
                    CollectionViewData(
                        collection = collection,
                        accounts = accounts.toImmutableList(),
                    ),
                )
            }
        }
    }
}
