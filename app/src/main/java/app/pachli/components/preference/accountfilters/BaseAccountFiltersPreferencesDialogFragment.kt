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

package app.pachli.components.preference.accountfilters

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.core.model.AccountFilterReason
import app.pachli.core.model.FilterAction
import app.pachli.databinding.PrefAccountFiltersBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.properties.Delegates
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** String resource to use as the "title" for the [FilterAction]. */
@get:StringRes
val FilterAction.titleStringResource: Int
    get() = when (this) {
        FilterAction.NONE -> R.string.filter_action_none
        FilterAction.WARN -> R.string.filter_action_warn
        FilterAction.HIDE -> R.string.filter_action_hide
    }

/** String resource to use as the description for the [FilterAction]. */
@get:StringRes
val FilterAction.descrStringResource: Int
    get() = when (this) {
        FilterAction.NONE -> R.string.filter_description_none
        FilterAction.WARN -> R.string.filter_description_warn
        FilterAction.HIDE -> R.string.filter_description_hide
    }

/**
 * Returns a flow of [FilterAction] that the user has chosen from the spinner.
 */
private fun AppCompatSpinner.filterActionFlow(): Flow<FilterAction> = callbackFlow {
    val listener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            trySend(adapter.getItem(position) as FilterAction)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            parent?.setSelection(0)
        }
    }

    onItemSelectedListener = listener

    awaitClose {
        onItemSelectedListener = null
    }
}

/**
 * Adapter that shows [FilterAction] in an [AppCompatSpinner].
 *
 * In the resting state the [titleStringResource][FilterAction.titleStringResource] is
 * shown in a single line.
 *
 * In the drop-down state a two-line view is shown with the
 * [titleStringResource][FilterAction.titleStringResource] on the first line and the
 * [descrStringResource][FilterAction.descrStringResource] on the second line.
 */
class FilterActionAdapter(context: Context) : ArrayAdapter<FilterAction>(
    context,
    android.R.layout.simple_spinner_dropdown_item,
    android.R.id.text1,
) {
    init {
        addAll(listOf(FilterAction.NONE, FilterAction.WARN, FilterAction.HIDE))
        setDropDownViewResource(android.R.layout.simple_list_item_2)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        getItem(position)?.let { item ->
            (view as? TextView)?.setText(item.titleStringResource)
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)
        getItem(position)?.let { item ->
            text1.setText(item.titleStringResource)
            text2.setText(item.descrStringResource)
        }
        return view
    }
}

/**
 * Shows a dialog that allows the user to choose the timeline filtering options
 * for:
 *
 * - accounts that are not followed
 * - accounts younger than 30 days
 * - accounts limited by the server
 *
 * @param timeline The [AccountFilterTimeline] affected by the filters.
 * @param dialogTitleId String resource to use as the dialog's title.
 * @param dialogSubtitleId String resource to use as the dialog's subtitle.
 */
@AndroidEntryPoint
abstract class BaseAccountFiltersPreferencesDialogFragment(
    private val timeline: AccountFilterTimeline,
    @StringRes private val dialogTitleId: Int,
    @StringRes private val dialogSubtitleId: Int,
) : DialogFragment(R.layout.pref_account_filters) {
    private val viewModel: AccountFiltersPreferenceViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AccountFiltersPreferenceViewModel.Factory> { factory ->
                factory.create(
                    requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID),
                    timeline,
                )
            }
        },
    )

    private lateinit var binding: PrefAccountFiltersBinding

    private lateinit var adapter: ArrayAdapter<FilterAction>

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PrefAccountFiltersBinding.inflate(layoutInflater)
        binding.title.text = getString(dialogSubtitleId)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitleId)
            .setView(binding.root)
        return builder.create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FilterActionAdapter(requireContext())

        binding.menuFilterNotFollowing.adapter = adapter
        binding.menuFilterYounger30d.adapter = adapter
        binding.menuFilterLimitedByServer.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiState.collect(::bind) }

                launch {
                    binding.menuFilterNotFollowing.filterActionFlow()
                        .map {
                            InfallibleUiAction.SetAccountFilter(
                                pachliAccountId,
                                reason = AccountFilterReason.NOT_FOLLOWING,
                                action = it,
                            )
                        }
                        .collect { viewModel.accept(it) }
                }

                launch {
                    binding.menuFilterYounger30d.filterActionFlow()
                        .map {
                            InfallibleUiAction.SetAccountFilter(
                                pachliAccountId,
                                reason = AccountFilterReason.YOUNGER_30D,
                                action = it,
                            )
                        }
                        .collect { viewModel.accept(it) }
                }

                launch {
                    binding.menuFilterLimitedByServer.filterActionFlow()
                        .map {
                            InfallibleUiAction.SetAccountFilter(
                                pachliAccountId,
                                reason = AccountFilterReason.LIMITED_BY_SERVER,
                                action = it,
                            )
                        }
                        .collect { viewModel.accept(it) }
                }
            }
        }
    }

    private fun bind(uiState: UiState) {
        with(binding) {
            menuFilterNotFollowing.setSelection(adapter.getPosition(uiState.filterNotFollowing))
            menuFilterYounger30d.setSelection(adapter.getPosition(uiState.filterYounger30d))
            menuFilterLimitedByServer.setSelection(adapter.getPosition(uiState.filterLimitedByServer))
        }
    }

    companion object {
        protected const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"
    }
}
