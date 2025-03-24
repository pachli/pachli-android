package app.pachli.core.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * [RecyclerView.ViewHolder] with a reference to a [ViewBinding].
 *
 * Use this if you need a simple view holder where all the logic can be in the
 * adapter's onBindViewHolder method. If the view holder has more complex logic
 * across multiple methods it is better to put that in a dedicated [ViewHolder]
 * class to avoid complicating the adapter code.
 */
class BindingHolder<T : ViewBinding>(
    val binding: T,
) : RecyclerView.ViewHolder(binding.root)
