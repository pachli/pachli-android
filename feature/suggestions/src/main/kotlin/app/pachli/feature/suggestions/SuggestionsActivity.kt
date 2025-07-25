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
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.commit
import app.pachli.core.activity.ReselectableFragment
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.suggestions.databinding.ActivitySuggestionsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Show the user a list of suggested accounts, and allow them to dismiss or follow
 * the suggestion.
 */
@AndroidEntryPoint
class SuggestionsActivity : ViewUrlActivity() {
    private val binding by viewBinding(ActivitySuggestionsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.appBar.applyDefaultWindowInsets()
        binding.toolbar.addScrollEffect(FadeChildScrollEffect)

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

        val pachliAccountId = intent.pachliAccountId

        val fragment =
            supportFragmentManager.findFragmentByTag(FRAGMENT_TAG + pachliAccountId) as SuggestionsFragment?
                ?: SuggestionsFragment.newInstance(pachliAccountId)

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG + pachliAccountId)
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "SuggestionsFragment_"
    }
}
