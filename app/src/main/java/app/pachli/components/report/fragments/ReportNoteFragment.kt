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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.pachli.R
import app.pachli.components.report.ReportViewModel
import app.pachli.components.report.Screen
import app.pachli.databinding.FragmentReportNoteBinding
import app.pachli.di.Injectable
import app.pachli.di.ViewModelFactory
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Success
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.viewBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class ReportNoteFragment : Fragment(R.layout.fragment_report_note), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ReportViewModel by activityViewModels { viewModelFactory }

    private val binding by viewBinding(FragmentReportNoteBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fillViews()
        handleChanges()
        handleClicks()
        subscribeObservables()
    }

    private fun handleChanges() {
        binding.editNote.doAfterTextChanged {
            viewModel.reportNote = it?.toString().orEmpty()
        }
        binding.checkIsNotifyRemote.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isRemoteNotify = isChecked
        }
    }

    private fun fillViews() {
        binding.editNote.setText(viewModel.reportNote)

        if (viewModel.isRemoteAccount) {
            binding.checkIsNotifyRemote.show()
            binding.reportDescriptionRemoteInstance.show()
        } else {
            binding.checkIsNotifyRemote.hide()
            binding.reportDescriptionRemoteInstance.hide()
        }

        if (viewModel.isRemoteAccount) {
            binding.checkIsNotifyRemote.text = getString(R.string.report_remote_instance, viewModel.remoteServer)
        }
        binding.checkIsNotifyRemote.isChecked = viewModel.isRemoteNotify
    }

    private fun subscribeObservables() {
        viewModel.reportingState.observe(viewLifecycleOwner) {
            when (it) {
                is Success -> viewModel.navigateTo(Screen.Done)
                is Loading -> showLoading()
                is Error -> showError(it.cause)
            }
        }
    }

    private fun showError(error: Throwable?) {
        binding.editNote.isEnabled = true
        binding.checkIsNotifyRemote.isEnabled = true
        binding.buttonReport.isEnabled = true
        binding.buttonBack.isEnabled = true
        binding.progressBar.hide()

        Snackbar.make(binding.buttonBack, if (error is IOException) R.string.error_network else R.string.error_generic, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) {
                sendReport()
            }
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
            viewModel.navigateTo(Screen.Back)
        }

        binding.buttonReport.setOnClickListener {
            sendReport()
        }
    }

    companion object {
        fun newInstance() = ReportNoteFragment()
    }
}
