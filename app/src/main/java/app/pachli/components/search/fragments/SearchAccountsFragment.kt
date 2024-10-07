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
import app.pachli.components.search.adapter.SearchAccountsAdapter
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SharedPreferencesRepository
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class SearchAccountsFragment : SearchFragment<TimelineAccount>() {
    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.searchRecyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )
    }

    override fun createAdapter(): PagingDataAdapter<TimelineAccount, *> {
        return SearchAccountsAdapter(
            this,
            sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            sharedPreferencesRepository.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
        )
    }

    override val data: Flow<PagingData<TimelineAccount>>
        get() = viewModel.accountsFlow

    companion object {
        fun newInstance(pachliAccountId: Long): SearchAccountsFragment {
            return SearchFragment.newInstance(pachliAccountId)
        }
    }
}
