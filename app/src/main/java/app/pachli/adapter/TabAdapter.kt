/* Copyright 2019 Conny Duck
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
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import app.pachli.R
import app.pachli.TabViewData
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.database.model.TabData
import app.pachli.core.designsystem.R as DR
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemTabPreferenceBinding
import app.pachli.databinding.ItemTabPreferenceSmallBinding
import app.pachli.util.setDrawableTint
import com.google.android.material.chip.Chip

interface ItemInteractionListener {
    fun onTabAdded(tab: TabViewData)
    fun onTabRemoved(position: Int)
    fun onStartDelete(viewHolder: RecyclerView.ViewHolder)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    fun onActionChipClicked(tabData: TabData.Hashtag, tabPosition: Int)
    fun onChipClicked(tabData: TabData.Hashtag, tabPosition: Int, chipPosition: Int)
}

class TabAdapter(
    private var data: List<TabViewData>,
    private val small: Boolean,
    private val listener: ItemInteractionListener,
    private var removeButtonEnabled: Boolean = false,
) : RecyclerView.Adapter<BindingHolder<ViewBinding>>() {

    fun updateData(newData: List<TabViewData>) {
        this.data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ViewBinding> {
        val binding = if (small) {
            ItemTabPreferenceSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        } else {
            ItemTabPreferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ViewBinding>, position: Int) {
        val context = holder.itemView.context
        val tab = data[position]

        if (small) {
            val binding = holder.binding as ItemTabPreferenceSmallBinding

            binding.textView.setText(tab.text)

            binding.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(tab.icon, 0, 0, 0)

            binding.textView.setOnClickListener {
                listener.onTabAdded(tab)
            }
        } else {
            val binding = holder.binding as ItemTabPreferenceBinding

            if (tab.tabData is TabData.UserList) {
                binding.textView.text = tab.tabData.title
            } else {
                binding.textView.setText(tab.text)
            }

            binding.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(tab.icon, 0, 0, 0)

            binding.imageView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    listener.onStartDrag(holder)
                    true
                } else {
                    false
                }
            }
            binding.removeButton.setOnClickListener {
                listener.onTabRemoved(holder.bindingAdapterPosition)
            }
            binding.removeButton.isEnabled = removeButtonEnabled
            setDrawableTint(
                holder.itemView.context,
                binding.removeButton.drawable,
                (if (removeButtonEnabled) android.R.attr.textColorTertiary else DR.attr.textColorDisabled),
            )

            if (tab.tabData is TabData.Hashtag) {
                binding.chipGroup.show()

                /*
                 * The chip group will always contain the actionChip (it is defined in the xml layout).
                 * The other dynamic chips are inserted in front of the actionChip.
                 * This code tries to reuse already added chips to reduce the number of Views created.
                 */
                tab.tabData.tags.forEachIndexed { i, arg ->

                    val chip = binding.chipGroup.getChildAt(i).takeUnless { it.id == R.id.actionChip } as Chip?
                        ?: Chip(context).apply {
                            setCloseIconResource(R.drawable.ic_cancel_24dp)
                            isCheckable = false
                            binding.chipGroup.addView(this, binding.chipGroup.size - 1)
                        }

                    chip.text = arg

                    if (tab.tabData.tags.size <= 1) {
                        chip.isCloseIconVisible = false
                        chip.setOnClickListener(null)
                    } else {
                        chip.isCloseIconVisible = true
                        chip.setOnClickListener {
                            listener.onChipClicked(tab.tabData, holder.bindingAdapterPosition, i)
                        }
                    }
                }

                while (binding.chipGroup.size - 1 > tab.tabData.tags.size) {
                    binding.chipGroup.removeViewAt(tab.tabData.tags.size)
                }

                binding.actionChip.setOnClickListener {
                    listener.onActionChipClicked(tab.tabData, holder.bindingAdapterPosition)
                }
            } else {
                binding.chipGroup.hide()
            }
        }
    }

    override fun getItemCount() = data.size

    fun setRemoveButtonVisible(enabled: Boolean) {
        if (removeButtonEnabled != enabled) {
            removeButtonEnabled = enabled
            notifyDataSetChanged()
        }
    }
}
