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

package app.pachli.components.report

import android.os.Bundle
import androidx.activity.viewModels
import app.pachli.R
import app.pachli.components.report.adapter.ReportPagerAdapter
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.ReportActivityIntent
import app.pachli.databinding.ActivityReportBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Report a status or user.
 */
@AndroidEntryPoint
class ReportActivity : BottomSheetActivity() {
    private val viewModel: ReportViewModel by viewModels()

    private val binding by viewBinding(ActivityReportBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = ReportActivityIntent.getAccountId(intent)
        val accountUserName = ReportActivityIntent.getAccountUserName(intent)
        if (accountId.isBlank() || accountUserName.isBlank()) {
            throw IllegalStateException("accountId ($accountId) or accountUserName ($accountUserName) is blank")
        }

        viewModel.init(accountId, accountUserName, ReportActivityIntent.getStatusId(intent))

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.apply {
            title = getString(R.string.report_username_format, viewModel.accountUserName)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(app.pachli.core.ui.R.drawable.ic_close_24dp)
        }

        initViewPager()
        if (savedInstanceState == null) {
            viewModel.navigateTo(Screen.Statuses)
        }
        subscribeObservables()
    }

    private fun initViewPager() {
        binding.wizard.isUserInputEnabled = false

        // Odd workaround for text field losing focus on first focus
        //   (unfixed old bug: https://github.com/material-components/material-components-android/issues/500)
        binding.wizard.offscreenPageLimit = 1

        binding.wizard.adapter = ReportPagerAdapter(this)
    }

    private fun subscribeObservables() {
        viewModel.navigation.observe(this) { screen ->
            if (screen != null) {
                viewModel.navigated()
                when (screen) {
                    Screen.Statuses -> showStatusesPage()
                    Screen.Note -> showNotesPage()
                    Screen.Done -> showDonePage()
                    Screen.Back -> showPreviousScreen()
                    Screen.Finish -> closeScreen()
                }
            }
        }

        viewModel.checkUrl.observe(this) {
            if (!it.isNullOrBlank()) {
                viewModel.urlChecked()
                viewUrl(it)
            }
        }
    }

    private fun showPreviousScreen() {
        when (binding.wizard.currentItem) {
            0 -> closeScreen()
            1 -> showStatusesPage()
        }
    }

    private fun showDonePage() {
        binding.wizard.currentItem = 2
    }

    private fun showNotesPage() {
        binding.wizard.currentItem = 1
    }

    private fun closeScreen() {
        finish()
    }

    private fun showStatusesPage() {
        binding.wizard.currentItem = 0
    }
}
