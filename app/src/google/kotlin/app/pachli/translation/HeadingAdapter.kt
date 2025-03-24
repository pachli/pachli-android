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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemHeadingBinding

/**
 * Adapter that manages a single text item, suitable for use in a collection of
 * adapters managed by [androidx.recyclerview.widget.ConcatAdapter].
 *
 * @param text String resource to show as the heading.
 */
internal class HeadingAdapter(@StringRes private val text: Int) : RecyclerView.Adapter<BindingHolder<ItemHeadingBinding>>() {
    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemHeadingBinding> {
        return BindingHolder(
            ItemHeadingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemHeadingBinding>, position: Int) {
        holder.binding.text1.text = holder.binding.text1.context.getString(text)
    }
}
