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

package app.pachli.core.ui.accessibility

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import app.pachli.core.ui.R
import app.pachli.core.ui.databinding.SimpleListItem1CopyButtonBinding
import app.pachli.core.ui.di.UseCaseEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * An [ArrayAdapter] that shows a "copy" button next to each item.
 *
 * If the "copy" button is clicked the text of the item is copied to the clipboard
 * and a toast is shown (if appropriate).
 *
 * If the item is clicked then [listener] is called with the position of the
 * clicked item.
 */
class ArrayAdapterWithCopyButton<T : CharSequence>(
    context: Context,
    items: List<T>,
    private val listener: OnClickListener,
) : ArrayAdapter<T>(context, R.layout.simple_list_item_1_copy_button, android.R.id.text1, items) {
    fun interface OnClickListener {
        /** @param position Index of the item the user clicked. */
        fun onClick(position: Int)
    }

    private val useCaseEntryPoint = EntryPointAccessors.fromApplication<UseCaseEntryPoint>(context.applicationContext)
    private val clipboard = useCaseEntryPoint.clipboardUseCase

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            SimpleListItem1CopyButtonBinding.inflate(LayoutInflater.from(context), parent, false).apply {
                text1.setOnClickListener { listener.onClick(position) }

                copy.setOnClickListener {
                    getItem(position)?.let { clipboard.copyTextTo(it) }
                }
            }
        } else {
            SimpleListItem1CopyButtonBinding.bind(convertView)
        }

        getItem(position)?.let { binding.text1.text = it }

        return binding.root
    }
}
