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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.components.report.AccountType
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.repository.Loadable
import app.pachli.databinding.FragmentReportNoteBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Allows the user to enter final comments and decide whether a copy of the
 * report should be forwarded to the remote admins (if appropriate).
 */
@AndroidEntryPoint
class ReportNoteFragment : Fragment(R.layout.fragment_report_note) {

    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportNoteBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handleChanges()
        handleClicks()
        bind()
    }

    private fun handleChanges() {
        binding.editNote.doAfterTextChanged {
            viewModel.reportNote = it?.toString().orEmpty()
        }
        binding.checkIsNotifyRemote.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isRemoteNotify = isChecked
        }
    }

    private fun bind() {
        binding.editNote.setText(viewModel.reportNote)

        binding.checkIsNotifyRemote.visible(viewModel.reportedAccountType is AccountType.Remote)
        binding.reportDescriptionRemoteInstance.visible(viewModel.reportedAccountType is AccountType.Remote)

        if (viewModel.reportedAccountType is AccountType.Remote) {
            binding.checkIsNotifyRemote.text = getString(R.string.report_remote_instance, (viewModel.reportedAccountType as AccountType.Remote).server)
        }
        binding.checkIsNotifyRemote.isChecked = viewModel.isRemoteNotify

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.reportingState.collectLatest(::bindReportingState) }
            }
        }
    }

    private fun bindReportingState(reportingState: Result<Loadable<Unit>, PachliError>) {
        reportingState
            .onSuccess {
                when (it) {
                    is Loadable.Loading -> showLoading()
                    is Loadable.Loaded<*> -> viewModel.navigateTo(Screen.Done)
                }
            }
            .onFailure { showError(it) }
    }

    private fun showError(error: PachliError) {
        binding.editNote.isEnabled = true
        binding.checkIsNotifyRemote.isEnabled = true
        binding.buttonReport.isEnabled = true
        binding.buttonBack.isEnabled = true
        binding.progressBar.hide()

        Snackbar.make(binding.buttonBack, error.fmt(requireContext()), Snackbar.LENGTH_INDEFINITE)
            .setAction(app.pachli.core.ui.R.string.action_retry) { sendReport() }
            .show()
    }

    private fun sendReport() {
        viewModel.doReport()
    }

    private fun showLoading() {
        binding.buttonReport.isEnabled = false
        binding.buttonBack.isEnabled = false
        binding.editNote.isEnabled = false
        binding.checkIsNotifyRemote.isEnabled = false
        binding.progressBar.show()
    }

    private fun handleClicks() {
        binding.buttonBack.setOnClickListener {
            viewModel.navigateBack()
        }

        binding.buttonReport.setOnClickListener {
            sendReport()
        }
    }

    companion object {
        fun newInstance() = ReportNoteFragment()
    }
}
