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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.components.report.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.pachli.R
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.databinding.FragmentReportDoneBinding
import app.pachli.di.Injectable
import app.pachli.di.ViewModelFactory
import app.pachli.util.Loading
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReportDoneFragment : Fragment(R.layout.fragment_report_done), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ReportViewModel by activityViewModels { viewModelFactory }

    private val binding by viewBinding(FragmentReportDoneBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textReported.text = getString(R.string.report_sent_success, viewModel.accountUserName)
        handleClicks()
        subscribeObservables()
    }

    private fun subscribeObservables() {
        viewModel.muteState.observe(viewLifecycleOwner) {
            if (it !is Loading) {
                binding.buttonMute.show()
                binding.progressMute.show()
            } else {
                binding.buttonMute.hide()
                binding.progressMute.hide()
            }

            binding.buttonMute.setText(
                when (it.data) {
                    true -> R.string.action_unmute
                    else -> R.string.action_mute
                },
            )
        }

        viewModel.blockState.observe(viewLifecycleOwner) {
            if (it !is Loading) {
                binding.buttonBlock.show()
                binding.progressBlock.show()
            } else {
                binding.buttonBlock.hide()
                binding.progressBlock.hide()
            }
            binding.buttonBlock.setText(
                when (it.data) {
                    true -> R.string.action_unblock
                    else -> R.string.action_block
                },
            )
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
