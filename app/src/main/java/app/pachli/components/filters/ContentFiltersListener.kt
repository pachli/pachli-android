package app.pachli.components.filters

import app.pachli.core.data.model.ContentFilter

interface ContentFiltersListener {
    fun deleteContentFilter(contentFilter: ContentFilter)
    fun updateContentFilter(updatedContentFilter: ContentFilter)
}
