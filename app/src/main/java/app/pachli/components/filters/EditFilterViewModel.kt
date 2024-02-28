package app.pachli.components.filters

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.appstore.EventHub
import app.pachli.appstore.FilterChangedEvent
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@HiltViewModel
class EditFilterViewModel @Inject constructor(val api: MastodonApi, val eventHub: EventHub) : ViewModel() {
    private var originalFilter: Filter? = null
    val title = MutableStateFlow("")
    val keywords = MutableStateFlow(listOf<FilterKeyword>())
    val action = MutableStateFlow(Filter.Action.WARN)
    val duration = MutableStateFlow(0)
    val contexts = MutableStateFlow(listOf<FilterContext>())

    fun load(filter: Filter) {
        originalFilter = filter
        title.value = filter.title
        keywords.value = filter.keywords
        action.value = filter.action
        duration.value = if (filter.expiresAt == null) {
            0
        } else {
            -1
        }
        contexts.value = filter.contexts
    }

    fun addKeyword(keyword: FilterKeyword) {
        keywords.value += keyword
    }

    fun deleteKeyword(keyword: FilterKeyword) {
        keywords.value = keywords.value.filterNot { it == keyword }
    }

    fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword) {
        val index = keywords.value.indexOf(original)
        if (index >= 0) {
            keywords.value = keywords.value.toMutableList().apply {
                set(index, updated)
            }
        }
    }

    fun setTitle(title: String) {
        this.title.value = title
    }

    fun setDuration(index: Int) {
        duration.value = index
    }

    fun setAction(action: Filter.Action) {
        this.action.value = action
    }

    fun addContext(filterContext: FilterContext) {
        if (!contexts.value.contains(filterContext)) {
            contexts.value += filterContext
        }
    }

    fun removeContext(filterContext: FilterContext) {
        contexts.value = contexts.value.filter { it != filterContext }
    }

    fun validate(): Boolean {
        return title.value.isNotBlank() &&
            keywords.value.isNotEmpty() &&
            contexts.value.isNotEmpty()
    }

    suspend fun saveChanges(context: Context): Boolean {
        val contexts = contexts.value
        val title = title.value
        val durationIndex = duration.value
        val action = action.value.action

        return withContext(viewModelScope.coroutineContext) {
            val success = originalFilter?.let { filter ->
                updateFilter(filter, title, contexts, action, durationIndex, context)
            } ?: createFilter(title, contexts, action, durationIndex, context)

            // Send FilterChangedEvent for old and new contexts, to ensure that
            // e.g., removing a filter from "home" still notifies anything showing
            // the home timeline, so the timeline can be refreshed.
            if (success) {
                val originalContexts = originalFilter?.contexts ?: emptyList()
                val newFilterContexts = contexts
                (originalContexts + newFilterContexts).distinct().forEach {
                    eventHub.dispatch(FilterChangedEvent(it))
                }
            }
            return@withContext success
        }
    }

    private suspend fun createFilter(title: String, contexts: List<FilterContext>, action: String, durationIndex: Int, context: Context): Boolean {
        val expiresInSeconds = EditFilterActivity.getSecondsForDurationIndex(durationIndex, context)
        api.createFilter(
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds,
        ).fold(
            { newFilter ->
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                return keywords.value.map { keyword ->
                    api.addFilterKeyword(filterId = newFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                }.none { it.isFailure }
            },
            { throwable ->
                return (
                    throwable is HttpException && throwable.code() == 404 &&
                        // Endpoint not found, fall back to v1 api
                        createFilterV1(contexts, expiresInSeconds)
                    )
            },
        )
    }

    private suspend fun updateFilter(originalFilter: Filter, title: String, contexts: List<FilterContext>, action: String, durationIndex: Int, context: Context): Boolean {
        val expiresInSeconds = EditFilterActivity.getSecondsForDurationIndex(durationIndex, context)
        api.updateFilter(
            id = originalFilter.id,
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds,
        ).fold(
            {
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                val results = keywords.value.map { keyword ->
                    if (keyword.id.isEmpty()) {
                        api.addFilterKeyword(filterId = originalFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    } else {
                        api.updateFilterKeyword(keywordId = keyword.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    }
                } + originalFilter.keywords.filter { keyword ->
                    // Deleted keywords
                    keywords.value.none { it.id == keyword.id }
                }.map { api.deleteFilterKeyword(it.id) }

                return results.none { it.isFailure }
            },
            { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
                    // Endpoint not found, fall back to v1 api
                    if (updateFilterV1(contexts, expiresInSeconds)) {
                        return true
                    }
                }
                return false
            },
        )
    }

    private suspend fun createFilterV1(contexts: List<FilterContext>, expiresInSeconds: Int?): Boolean {
        return keywords.value.map { keyword ->
            api.createFilterV1(keyword.keyword, contexts, false, keyword.wholeWord, expiresInSeconds)
        }.none { it.isFailure }
    }

    private suspend fun updateFilterV1(contexts: List<FilterContext>, expiresInSeconds: Int?): Boolean {
        val results = keywords.value.map { keyword ->
            if (originalFilter == null) {
                api.createFilterV1(
                    phrase = keyword.keyword,
                    context = contexts,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds,
                )
            } else {
                api.updateFilterV1(
                    id = originalFilter!!.id,
                    phrase = keyword.keyword,
                    context = contexts,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds,
                )
            }
        }
        // Don't handle deleted keywords here because there's only one keyword per v1 filter anyway

        return results.none { it.isFailure }
    }
}
