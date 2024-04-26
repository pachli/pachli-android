package app.pachli.components.filters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.network.model.Filter
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemRemovableBinding
import app.pachli.util.getRelativeTimeSpanString
import com.google.android.material.color.MaterialColors

class FiltersAdapter(val listener: FiltersListener, val filters: List<Filter>) :
    RecyclerView.Adapter<BindingHolder<ItemRemovableBinding>>() {

    override fun getItemCount(): Int = filters.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemRemovableBinding> {
        return BindingHolder(ItemRemovableBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemRemovableBinding>, position: Int) {
        val binding = holder.binding
        val resources = binding.root.resources
        val actions = resources.getStringArray(R.array.filter_actions)
        val filterContextNames = resources.getStringArray(R.array.filter_contexts)

        val filter = filters[position]
        val context = binding.root.context
        binding.textPrimary.text = filter.expiresAt?.let {
            context.getString(
                R.string.filter_expiration_format,
                filter.title,
                getRelativeTimeSpanString(binding.root.context, it.time, System.currentTimeMillis()),
            )
        } ?: filter.title

        // Secondary row shows filter actions and contexts, or errors if the filter is invalid
        val errors = filter.validate()
        val secondaryText: String
        val secondaryTextColor: Int

        if (errors.isEmpty()) {
            secondaryText = context.getString(
                R.string.filter_description_format,
                actions.getOrNull(filter.action.ordinal - 1),
                filter.contexts.map { filterContextNames.getOrNull(it.ordinal) }.joinToString("/"),
            )
            secondaryTextColor = android.R.attr.textColorTertiary
        } else {
            secondaryText = context.getString(errors.first().stringResource())
            secondaryTextColor = androidx.appcompat.R.attr.colorError
        }

        binding.textSecondary.text = secondaryText
        binding.textSecondary.setTextColor(MaterialColors.getColor(binding.textSecondary, secondaryTextColor))

        binding.delete.setOnClickListener {
            listener.deleteFilter(filter)
        }

        binding.root.setOnClickListener {
            listener.updateFilter(filter)
        }
    }
}
