package app.pachli.core.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class BindingHolder<T : ViewBinding>(
    val binding: T,
) : RecyclerView.ViewHolder(binding.root)
