/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.translation

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pachli.R
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.ui.components.PachliTheme
import app.pachli.databinding.FragmentModelManagerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf

/**
 * Displays a list of downloaded and available translation models, and UI controls to
 * allow the user to:
 *
 * - Delete a downloaded translation model.
 * - Pre-download a translation model.
 */
@AndroidEntryPoint
class TranslationModelManagerFragment : Fragment(R.layout.fragment_model_manager) {
    private val viewModel: TranslationModelManagerViewModel by activityViewModels()

    private val binding by viewBinding(FragmentModelManagerBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val translationModels = viewModel.translationModelViewData.collectAsStateWithLifecycle(persistentListOf())

                PachliTheme {
                    TranslationModelManagerScreen(
                        translationModels = { translationModels.value },
                        onDelete = { language -> viewModel.deleteLanguage(language) },
                        onDownload = { language -> viewModel.downloadLanguage(language) },
                    ) { viewModel.canDownloadNow() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.translation_model_manager_fragment_title)
    }
}
