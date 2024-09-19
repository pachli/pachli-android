package app.pachli.components.filters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.model.ContentFilter
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemRemovableBinding
import app.pachli.util.getRelativeTimeSpanString
import com.google.android.material.color.MaterialColors

class FiltersAdapter(val listener: ContentFiltersListener, val contentFilters: List<ContentFilter>) :
    RecyclerView.Adapter<BindingHolder<ItemRemovableBinding>>() {

    override fun getItemCount(): Int = contentFilters.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemRemovableBinding> {
        return BindingHolder(ItemRemovableBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemRemovableBinding>, position: Int) {
        val binding = holder.binding
        val resources = binding.root.resources
        val actions = resources.getStringArray(R.array.filter_actions)
        val filterContextNames = resources.getStringArray(R.array.filter_contexts)

        val filter = contentFilters[position]
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
                actions.getOrNull(filter.filterAction.ordinal - 1),
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
            listener.deleteContentFilter(filter)
        }

        binding.root.setOnClickListener {
            listener.updateContentFilter(filter)
        }
    }
}

/** Reasons why a filter might be invalid */
enum class ContentFilterValidationError {
    /** Filter title is empty or blank */
    NO_TITLE,

    /** Filter has no keywords */
    NO_KEYWORDS,

    /** Filter has no contexts */
    NO_CONTEXT,
}

/**
 * @return String resource containing an error message for this
 *   validation error.
 */
@StringRes
fun ContentFilterValidationError.stringResource() = when (this) {
    ContentFilterValidationError.NO_TITLE -> R.string.error_filter_missing_title
    ContentFilterValidationError.NO_KEYWORDS -> R.string.error_filter_missing_keyword
    ContentFilterValidationError.NO_CONTEXT -> R.string.error_filter_missing_context
}

/**
 * @return Set of [ContentFilterValidationError] given the current state of the
 * filter. Empty if there are no validation errors.
 */
fun ContentFilter.validate() = buildSet {
    if (title.isBlank()) add(ContentFilterValidationError.NO_TITLE)
    if (keywords.isEmpty()) add(ContentFilterValidationError.NO_KEYWORDS)
    if (contexts.isEmpty()) add(ContentFilterValidationError.NO_CONTEXT)
}
