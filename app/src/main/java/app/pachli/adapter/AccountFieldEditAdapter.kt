/* Copyright 2018 Conny Duck
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

package app.pachli.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.model.StringField
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemEditFieldBinding
import app.pachli.util.fixTextSelection

/**
 * @property onChange Call this whenever data in the UI fields changes
 */
class AccountFieldEditAdapter(
    val onChange: () -> Unit,
) : RecyclerView.Adapter<BindingHolder<ItemEditFieldBinding>>() {

    private val fieldData = mutableListOf<MutableStringPair>()
    private var maxNameLength: Int? = null
    private var maxValueLength: Int? = null

    fun setFields(fields: List<StringField>) {
        fieldData.clear()

        fields.forEach { field ->
            fieldData.add(MutableStringPair(field.name, field.value))
        }
        if (fieldData.isEmpty()) {
            fieldData.add(MutableStringPair("", ""))
        }

        notifyDataSetChanged()
    }

    fun setFieldLimits(maxNameLength: Int?, maxValueLength: Int?) {
        this.maxNameLength = maxNameLength
        this.maxValueLength = maxValueLength
        notifyDataSetChanged()
    }

    fun getFieldData(): List<StringField> {
        return fieldData.map {
            StringField(it.first, it.second)
        }
    }

    fun addField() {
        fieldData.add(MutableStringPair("", ""))
        notifyItemInserted(fieldData.size - 1)
    }

    override fun getItemCount() = fieldData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemEditFieldBinding> {
        val binding = ItemEditFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemEditFieldBinding>, position: Int) {
        holder.binding.accountFieldNameText.setText(fieldData[position].first)
        holder.binding.accountFieldValueText.setText(fieldData[position].second)

        holder.binding.accountFieldNameTextLayout.isCounterEnabled = maxNameLength != null
        maxNameLength?.let {
            holder.binding.accountFieldNameTextLayout.counterMaxLength = it
        }

        holder.binding.accountFieldValueTextLayout.isCounterEnabled = maxValueLength != null
        maxValueLength?.let {
            holder.binding.accountFieldValueTextLayout.counterMaxLength = it
        }

        holder.binding.accountFieldNameText.doAfterTextChanged { newText ->
            fieldData.getOrNull(holder.bindingAdapterPosition)?.first = newText.toString()
            onChange()
        }

        holder.binding.accountFieldValueText.doAfterTextChanged { newText ->
            fieldData.getOrNull(holder.bindingAdapterPosition)?.second = newText.toString()
            onChange()
        }

        // Ensure the textview contents are selectable
        holder.binding.accountFieldNameText.fixTextSelection()
        holder.binding.accountFieldValueText.fixTextSelection()
    }

    class MutableStringPair(var first: String, var second: String)
}
