package app.pachli.components.filters

import app.pachli.core.network.model.Filter

interface FiltersListener {
    fun deleteFilter(filter: Filter)
    fun updateFilter(updatedFilter: Filter)
}
