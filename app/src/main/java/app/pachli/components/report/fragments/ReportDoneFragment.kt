/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.Loadable
import app.pachli.databinding.FragmentReportDoneBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Confirms the report has been sent, and allows the user to optionally
 * mute or block the account they just reported.
 */
@AndroidEntryPoint
class ReportDoneFragment : Fragment(R.layout.fragment_report_done) {

    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportDoneBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textReported.text = getString(R.string.report_sent_success, viewModel.reportedAccountUsername)
        handleClicks()
        bind()
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.muting.collect(::bindMute) }
                launch { viewModel.blocking.collect(::bindBlock) }
            }
        }
    }

    private fun bindMute(result: Result<Loadable<Boolean>, PachliError>) {
        result.onSuccess { loadable ->
            when (loadable) {
                is Loadable.Loading -> {
                    binding.buttonMute.isEnabled = false
                    binding.progressMute.show()
                }

                is Loadable.Loaded<Boolean> -> {
                    binding.buttonMute.setText(
                        when (loadable.data) {
                            true -> R.string.action_unmute
                            false -> R.string.action_mute
                        },
                    )
                    binding.buttonMute.isEnabled = true
                    binding.progressMute.hide()
                }
            }
        }.onFailure {
            binding.buttonMute.isEnabled = false
            binding.progressMute.hide()

            Snackbar.make(binding.root, it.fmt(requireContext()), Snackbar.LENGTH_INDEFINITE)
                .setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.reloadRelationship() }
                .show()
        }
    }

    private fun bindBlock(result: Result<Loadable<Boolean>, PachliError>) {
        result.onSuccess { loadable ->
            when (loadable) {
                is Loadable.Loading -> {
                    binding.buttonBlock.isEnabled = false
                    binding.progressBlock.show()
                }

                is Loadable.Loaded<Boolean> -> {
                    binding.buttonBlock.setText(
                        when (loadable.data) {
                            true -> R.string.action_unblock
                            false -> R.string.action_block
                        },
                    )
                    binding.buttonBlock.isEnabled = true
                    binding.progressBlock.hide()
                }
            }
        }.onFailure {
            binding.buttonBlock.isEnabled = false
            binding.progressBlock.hide()

            Snackbar.make(binding.root, it.fmt(requireContext()), Snackbar.LENGTH_INDEFINITE)
                .setAction(app.pachli.core.ui.R.string.action_retry) { viewModel.reloadRelationship() }
                .show()
        }
    }

    private fun handleClicks() {
        binding.buttonDone.setOnClickListener {
            viewModel.navigateTo(Screen.Finish)
        }
        binding.buttonBlock.setOnClickListener {
            viewModel.toggleBlock()
        }
        binding.buttonMute.setOnClickListener {
            viewModel.toggleMute()
        }
    }

    companion object {
        fun newInstance() = ReportDoneFragment()
    }
}
