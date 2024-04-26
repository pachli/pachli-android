package app.pachli.components.filters

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.appstore.EventHub
import app.pachli.appstore.FilterChangedEvent
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.retrofit.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@HiltViewModel
class EditFilterViewModel @Inject constructor(val api: MastodonApi, val eventHub: EventHub) : ViewModel() {
    private lateinit var originalFilter: Filter
    val title = MutableStateFlow("")
    val keywords = MutableStateFlow(listOf<FilterKeyword>())
    val action = MutableStateFlow(Filter.Action.WARN)
    val duration = MutableStateFlow(0)
    val contexts = MutableStateFlow(listOf<FilterContext>())

    /** Track whether the duration has been modified, for use in [onChange] */
    // TODO: Rethink how duration is shown in the UI.
    // Could show the actual end time with the date/time widget to set the duration,
    // along with dropdown for quick settings (1h, etc).
    private var durationIsDirty = false

    private val _isDirty = MutableStateFlow(false)

    /** True if the user has made unsaved changes to the filter */
    val isDirty = _isDirty.asStateFlow()

    private val _validationErrors = MutableStateFlow(emptySet<FilterValidationError>())

    /** True if the filter is valid and can be saved */
    val validationErrors = _validationErrors.asStateFlow()

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
        onChange()
    }

    fun deleteKeyword(keyword: FilterKeyword) {
        keywords.value = keywords.value.filterNot { it == keyword }
        onChange()
    }

    fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword) {
        val index = keywords.value.indexOf(original)
        if (index >= 0) {
            keywords.value = keywords.value.toMutableList().apply {
                set(index, updated)
            }
            onChange()
        }
    }

    fun setTitle(title: String) {
        this.title.value = title
        onChange()
    }

    fun setDuration(index: Int) {
        if (!durationIsDirty && duration.value != index) durationIsDirty = true

        duration.value = index
        onChange()
    }

    fun setAction(action: Filter.Action) {
        this.action.value = action
        onChange()
    }

    fun addContext(filterContext: FilterContext) {
        if (!contexts.value.contains(filterContext)) {
            contexts.value += filterContext
            onChange()
        }
    }

    fun removeContext(filterContext: FilterContext) {
        contexts.value = contexts.value.filter { it != filterContext }
        onChange()
    }

    private fun validate() {
        _validationErrors.value = buildSet {
            if (title.value.isBlank()) add(FilterValidationError.NO_TITLE)
            if (keywords.value.isEmpty()) add(FilterValidationError.NO_KEYWORDS)
            if (contexts.value.isEmpty()) add(FilterValidationError.NO_CONTEXT)
        }
    }

    /**
     * Call when the contents of the filter change; recalculates validity
     * and dirty state.
     */
    private fun onChange() {
        validate()

        if (durationIsDirty) {
            _isDirty.value = true
            return
        }

        _isDirty.value = when {
            originalFilter.title != title.value -> true
            originalFilter.contexts != contexts.value -> true
            originalFilter.action != action.value -> true
            originalFilter.keywords.toSet() != keywords.value.toSet() -> true
            else -> false
        }
    }

    suspend fun saveChanges(context: Context): Boolean {
        val contexts = contexts.value
        val title = title.value
        val durationIndex = duration.value
        val action = action.value

        return withContext(viewModelScope.coroutineContext) {
            val success = if (originalFilter.id == "") {
                createFilter(title, contexts, action, durationIndex, context)
            } else {
                updateFilter(originalFilter, title, contexts, action, durationIndex, context)
            }

            // Send FilterChangedEvent for old and new contexts, to ensure that
            // e.g., removing a filter from "home" still notifies anything showing
            // the home timeline, so the timeline can be refreshed.
            if (success) {
                val originalContexts = originalFilter.contexts
                val newFilterContexts = contexts
                (originalContexts + newFilterContexts).distinct().forEach {
                    eventHub.dispatch(FilterChangedEvent(it))
                }
            }
            return@withContext success
        }
    }

    private suspend fun createFilter(title: String, contexts: List<FilterContext>, action: Filter.Action, durationIndex: Int, context: Context): Boolean {
        val expiresInSeconds = getSecondsForDurationIndex(durationIndex, context)
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

    private suspend fun updateFilter(originalFilter: Filter, title: String, contexts: List<FilterContext>, action: Filter.Action, durationIndex: Int, context: Context): Boolean {
        val expiresInSeconds = getSecondsForDurationIndex(durationIndex, context)
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

    private suspend fun createFilterV1(contexts: List<FilterContext>, expiresInSeconds: String?): Boolean {
        return keywords.value.map { keyword ->
            api.createFilterV1(keyword.keyword, contexts, false, keyword.wholeWord, expiresInSeconds)
        }.none { it.isFailure }
    }

    private suspend fun updateFilterV1(contexts: List<FilterContext>, expiresInSeconds: String?): Boolean {
        val results = keywords.value.map { keyword ->
            if (originalFilter.id == "") {
                api.createFilterV1(
                    phrase = keyword.keyword,
                    context = contexts,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds,
                )
            } else {
                api.updateFilterV1(
                    id = originalFilter.id,
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

    companion object {
        /**
         * Mastodon *stores* the absolute date in the filter,
         * but create/edit take a number of seconds (relative to the time the operation is posted)
         */
        fun getSecondsForDurationIndex(index: Int, context: Context?, default: Date? = null): String? {
            return when (index) {
                -1 -> default?.let { ((default.time - System.currentTimeMillis()) / 1000).toString() }
                0 -> ""
                else -> context?.resources?.getStringArray(R.array.filter_duration_values)?.get(index)
            }
        }
    }
}
