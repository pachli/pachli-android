package app.pachli.components.filters

import app.pachli.core.data.model.Filter

interface FiltersListener {
    fun deleteFilter(filter: Filter)
    fun updateFilter(updatedFilter: Filter)
}
