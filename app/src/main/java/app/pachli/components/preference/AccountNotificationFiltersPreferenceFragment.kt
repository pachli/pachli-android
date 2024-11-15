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

package app.pachli.components.preference

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
import app.pachli.core.model.FilterAction
import app.pachli.databinding.PrefAccountNotificationFiltersBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.properties.Delegates
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
 * Shows a dialog that allows the user to choose the notification filtering options
 * for:
 *
 * - accounts that are not followed
 * - accounts younger than 30 days
 * - accounts limited by the server
 *
 * The user's choices are persisted to the account preferences for the Pachli
 * account ID passed to
 * [newInstance][AccountNotificationFiltersPreferencesDialogFragment.newInstance].
 */
@AndroidEntryPoint
class AccountNotificationFiltersPreferencesDialogFragment : DialogFragment(R.layout.pref_account_notification_filters) {
    private val viewModel: AccountNotificationFiltersPreferenceViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AccountNotificationFiltersPreferenceViewModel.Factory> { factory ->
                factory.create(requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID))
            }
        },
    )

    private lateinit var binding: PrefAccountNotificationFiltersBinding

    private lateinit var adapter: ArrayAdapter<FilterAction>

    private var pachliAccountId by Delegates.notNull<Long>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PrefAccountNotificationFiltersBinding.inflate(layoutInflater)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_title_account_notification_filters)
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
                        .collect { viewModel.setNotificationAccountFilterNotFollowing(it) }
                }

                launch {
                    binding.menuFilterYounger30d.filterActionFlow()
                        .collect { viewModel.setNotificationAccountFilterYounger30d(it) }
                }

                launch {
                    binding.menuFilterLimitedByServer.filterActionFlow()
                        .collect { viewModel.setNotificationAccountFilterLimitedByServer(it) }
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
        private const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        fun newInstance(
            pachliAccountId: Long,
        ): AccountNotificationFiltersPreferencesDialogFragment {
            val fragment = AccountNotificationFiltersPreferencesDialogFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
