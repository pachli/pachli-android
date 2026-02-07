/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.feature.about.databinding.FragmentWorkersBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WorkersFragment : Fragment(R.layout.fragment_workers) {
    private val viewModel: WorkersFragmentViewModel by viewModels()

    private val binding by viewBinding(FragmentWorkersBinding::bind)

    private val pruneCachedMediaWorkerWorkInfoAdapter = WorkInfoAdapter()

    private val pruneCachedMediaWorkerLogEntryAdapter = LogEntryAdapter()

    private val pruneCacheWorkerWorkInfoAdapter = WorkInfoAdapter()

    private val pruneCacheWorkerLogEntryAdapter = LogEntryAdapter()

    private val pruneLogEntryEntityWorkInfoAdapter = WorkInfoAdapter()

    private val pruneLogEntryEntityWorkerLogEntryAdapter = LogEntryAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.pruneCachedMediaWorkInfoRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneCachedMediaWorkerWorkInfoAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        with(binding.pruneCachedMediaWorkerLogEntryRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneCachedMediaWorkerLogEntryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        with(binding.pruneCacheWorkInfoRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneCacheWorkerWorkInfoAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        with(binding.pruneCacheWorkerLogEntryRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneCacheWorkerLogEntryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        with(binding.pruneLogEntryWorkInfoRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneLogEntryEntityWorkInfoAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        with(binding.pruneLogEntryWorkerLogEntryRecyclerView) {
            applyDefaultWindowInsets()
            adapter = pruneLogEntryEntityWorkerLogEntryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pruneCachedMediaWorkerWorkInfo.collect {
                        pruneCachedMediaWorkerWorkInfoAdapter.submitList(it)
                    }
                }

                launch {
                    viewModel.pruneCachedMediaWorkerLogEntry.collect {
                        pruneCachedMediaWorkerLogEntryAdapter.submitList(it)
                    }
                }

                launch {
                    viewModel.pruneCacheWorkerWorkInfo.collect {
                        pruneCacheWorkerWorkInfoAdapter.submitList(it)
                    }
                }

                launch {
                    viewModel.pruneCacheWorkerLogEntry.collect {
                        pruneCacheWorkerLogEntryAdapter.submitList(it)
                    }
                }

                launch {
                    viewModel.pruneLogEntryEntityWorkerWorkInfo.collect {
                        pruneLogEntryEntityWorkInfoAdapter.submitList(it)
                    }
                }

                launch {
                    viewModel.pruneLogEntryEntityWorkerLogEntry.collect {
                        pruneLogEntryEntityWorkerLogEntryAdapter.submitList(it)
                    }
                }

                launch {
                    while (true) {
                        delay(10.seconds)
                        pruneCachedMediaWorkerWorkInfoAdapter.notifyItemRangeChanged(0, pruneCachedMediaWorkerWorkInfoAdapter.itemCount)
                        pruneCacheWorkerWorkInfoAdapter.notifyItemRangeChanged(0, pruneCacheWorkerWorkInfoAdapter.itemCount)
                        pruneLogEntryEntityWorkInfoAdapter.notifyItemRangeChanged(0, pruneLogEntryEntityWorkInfoAdapter.itemCount)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance() = WorkersFragment()
    }
}
