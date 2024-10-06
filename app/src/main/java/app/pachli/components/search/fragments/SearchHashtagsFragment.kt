/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.search.fragments

import android.os.Bundle
import android.view.View
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import app.pachli.components.search.adapter.SearchHashtagsAdapter
import app.pachli.core.network.model.HashTag
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class SearchHashtagsFragment : SearchFragment<HashTag>() {

    override val data: Flow<PagingData<HashTag>>
        get() = viewModel.hashtagsFlow

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.searchRecyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )
    }

    override fun createAdapter(): PagingDataAdapter<HashTag, *> = SearchHashtagsAdapter(this)

    companion object {
        fun newInstance(pachliAccountId: Long): SearchHashtagsFragment {
            return SearchFragment.newInstance(pachliAccountId)
        }
    }
}
