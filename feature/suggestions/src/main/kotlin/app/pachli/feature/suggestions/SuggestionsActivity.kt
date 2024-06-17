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

package app.pachli.feature.suggestions

import android.os.Bundle
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.feature.suggestions.databinding.ActivitySuggestionsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Show the user a list of suggested accounts, and allow them to dismiss or follow
 * the suggestion.
 */
@AndroidEntryPoint
class SuggestionsActivity : BottomSheetActivity() {
    private val binding by viewBinding(ActivitySuggestionsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.title_suggestions)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setOnClickListener {
            (binding.fragmentContainer.getFragment<SuggestionsFragment>() as? ReselectableFragment)?.onReselect()
        }
    }
}
