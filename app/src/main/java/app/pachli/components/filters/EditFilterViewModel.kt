/*
 * Copyright 2024 Pachli Association
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

package app.pachli.components.filters

import app.pachli.core.network.model.Filter as NetworkFilter
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.components.filters.UiError.DeleteFilterError
import app.pachli.components.filters.UiError.SaveFilterError
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.Filter
import app.pachli.core.data.model.FilterValidationError
import app.pachli.core.data.model.NewFilterKeyword
import app.pachli.core.data.repository.FilterEdit
import app.pachli.core.data.repository.FiltersRepository
import app.pachli.core.data.repository.NewFilter
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.feature.suggestions.mapNotNull
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data to show the filter in the UI.
 */
data class FilterViewData(
    /** Filter's ID. Null if this is a new, un-saved filter. */
    val id: String? = null,
    val title: String = "",
    val contexts: Set<FilterContext> = emptySet(),
    /**
     * The number of seconds in the future the filter should expire.
     * "-1" means "use the filter's current value".
     * "0" means "filter never expires".
     */
    val expiresIn: Int = 0,
    val action: NetworkFilter.Action = NetworkFilter.Action.WARN,
    val keywords: List<FilterKeyword> = emptyList()
) {
    /**
     * @return Set of [FilterValidationError] given the current state of the
     * filter. Empty if there are no validation errors.
     */
    fun validate() = buildSet {
        if (title.isBlank()) add(FilterValidationError.NO_TITLE)
        if (keywords.isEmpty()) add(FilterValidationError.NO_KEYWORDS)
        if (contexts.isEmpty()) add(FilterValidationError.NO_CONTEXT)
    }

    /**
     * Calculates the difference between [filter] and `this`, returning an
     * [FilterEdit] that representes the differences.
     */
    fun diff(filter: Filter): FilterEdit {
        val title: String? = if (title != filter.title) title else null
        val contexts = if (contexts != filter.contexts) contexts else null
        val action = if (action != filter.action) action else null

        // Keywords to delete
        val (keywordsToAdd, existingKeywords) = keywords.partition { it.id == "" }
        val existingKeywordsMap = existingKeywords.associateBy { it.id }

        // Delete any keywords that are in the original list but are not in the existing
        // keywords here.
        val keywordsToDelete = filter.keywords.filter { !existingKeywordsMap.contains(it.id) }

        // Any keywords that are in the original filter and this one, but have different
        // values need to be modified.
        val keywordsToModify = buildList {
            val originalKeywords = filter.keywords.associateBy { it.id }
            originalKeywords.forEach {
                val originalKeyword = it.value

                existingKeywordsMap[originalKeyword.id]?.let { existingKeyword ->
                    if (existingKeyword != originalKeyword) add(existingKeyword)
                }
            }
        }

        return FilterEdit(
            id = filter.id,
            title = title,
            contexts = contexts,
            expiresIn = this.expiresIn,
            action = action,
            keywordsToDelete = keywordsToDelete.ifEmpty { null },
            keywordsToModify = keywordsToModify.ifEmpty { null },
            keywordsToAdd = keywordsToAdd.ifEmpty { null },
        )
    }

    companion object {
        fun from(filter: Filter) = FilterViewData(
            id = filter.id,
            title = filter.title,
            contexts = filter.contexts,
            expiresIn = -1,
            action = filter.action,
            keywords = filter.keywords,
        )
    }
}

fun NewFilter.Companion.from(filterViewData: FilterViewData) = NewFilter(
    title = filterViewData.title,
    contexts = filterViewData.contexts,
    expiresIn = filterViewData.expiresIn,
    action = filterViewData.action,
    keywords = filterViewData.keywords.map {
        NewFilterKeyword(
            keyword = it.keyword,
            wholeWord = it.wholeWord,
        )
    },
)

/** Successful UI operations. */
sealed interface UiSuccess {
    /** Filter was saved. */
    data object SaveFilter : UiSuccess
    /** Filter was deleted. */
    data object DeleteFilter : UiSuccess
}

/** Errors that can occur from actions the user takes in the UI. */
sealed class UiError(
    @StringRes override val resourceId: Int,
    override val formatArgs: Array<out Any>? = null,
) : PachliError {
    /**
     * Filter could not be loaded.
     *
     * @param filterId ID of the filter that could not be loaded.
     */
    data class GetFilterError(val filterId: String, override val cause: PachliError) :
        UiError(R.string.error_load_filter_failed_fmt)

    /** Filter could not be saved. */
    data class SaveFilterError(override val cause: PachliError) :
        UiError(R.string.error_save_filter_failed_fmt)

    /** Filter could not be deleted. */
    data class DeleteFilterError(override val cause: PachliError) :
        UiError(R.string.error_delete_filter_failed_fmt)
}

/** Mode the UI should operate in. */
enum class UiMode {
    /** A new filter is being created. */
    CREATE,

    /** An existing filter is being edited. */
    EDIT,
}

/**
 * Create or edit filters.
 *
 * If [filter] is non-null it is used to initialise the view model data,
 * [filterId] is ignored, and [uiMode] is [UiMode.EDIT].
 *
 * If [filterId] is non-null is is fetched from the repository, used to
 * initialise the view model, and [uiMode] is [UiMode.EDIT].
 *
 * If both [filter] and [filterId] are null an empty [FilterViewData]
 * is initialised, and [uiMode] is [UiMode.CREATE].
 *
 * @param filtersRepository
 * @param filter Filter to show
 * @param filterId ID of filter to fetch and show
 */
@HiltViewModel(assistedFactory = EditFilterViewModel.Factory::class)
class EditFilterViewModel @AssistedInject constructor(
    val filtersRepository: FiltersRepository,
    @Assisted val filter: Filter?,
    @Assisted val filterId: String?,
) : ViewModel() {
    /** The original filter before any edits (if provided via [filter] or [filterId]. */
    private var originalFilter: Filter? = null

    /** User interface mode. */
    val uiMode = if (filter == null && filterId == null) UiMode.CREATE else UiMode.EDIT

    /** True if the user has made unsaved changes to the filter */
    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    /** True if the filter is valid and can be saved */
    private val _validationErrors = MutableStateFlow(emptySet<FilterValidationError>())
    val validationErrors = _validationErrors.asStateFlow()

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    private var _filterViewData = MutableSharedFlow<Result<FilterViewData?, UiError.GetFilterError>>()
    val filterViewData = _filterViewData
        .onSubscription {
            filter?.let {
                originalFilter = it
                emit(Ok(FilterViewData.from(it)))
                return@onSubscription
            }

            emit(
                filterId?.let {
                    filtersRepository.getFilter(filterId)
                        .onSuccess {
                            originalFilter = it
                        }.mapEither(
                            { FilterViewData.from(it) },
                            { UiError.GetFilterError(filterId, it) },
                        )
                } ?: Ok(FilterViewData()),
            )
        }.onEach { it.onSuccess { it?.let { onChange(it) } } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Ok(null),
        )

    /** Reload the filter, if [filterId] is non-null. */
    fun reload() = viewModelScope.launch {
        filterId ?: return@launch _filterViewData.emit(Ok(FilterViewData()))

        _filterViewData.emit(
            filtersRepository.getFilter(filterId)
                .onSuccess { originalFilter = it }
                .mapEither(
                    { FilterViewData.from(it) },
                    { UiError.GetFilterError(filterId, it) },
                )
        )
    }

    /** Adds [keyword] to [filterViewData]. */
    fun addKeyword(keyword: FilterKeyword) = viewModelScope.launch {
        _filterViewData.emit(
            filterViewData.value.mapNotNull {
                it.copy(keywords = it.keywords + keyword)
            },
        )
    }

    /** Deletes [keyword] from [filterViewData]. */
    fun deleteKeyword(keyword: FilterKeyword) = viewModelScope.launch {
        _filterViewData.emit(
            filterViewData.value.mapNotNull {
                it.copy(keywords = it.keywords.filterNot { it == keyword })
            },
        )
    }

    /** Replaces [original] keyword in [filterViewData] with [newKeyword]. */
    fun updateKeyword(original: FilterKeyword, newKeyword: FilterKeyword) = viewModelScope.launch {
        _filterViewData.emit(
            filterViewData.value.mapNotNull {
                it.copy(keywords = it.keywords.map { if (it == original) newKeyword else it })
            },
        )
    }

    /** Replaces [filterViewData]'s [title][FilterViewData.title] with [title]. */
    fun setTitle(title: String) = viewModelScope.launch {
        _filterViewData.emit(filterViewData.value.mapNotNull { it.copy(title = title) })
    }

    /** Replaces [filterViewData]'s [expiresIn][FilterViewData.expiresIn] with [expiresIn]. */
    fun setExpiresIn(expiresIn: Int) = viewModelScope.launch {
        _filterViewData.emit(filterViewData.value.mapNotNull { it.copy(expiresIn = expiresIn) })
    }

    /** Replaces [filterViewData]'s [action][FilterViewData.action] with [action]. */
    fun setAction(action: NetworkFilter.Action) = viewModelScope.launch {
        _filterViewData.emit(filterViewData.value.mapNotNull { it.copy(action = action) })
    }

    /** Adds [filterContext] to [filterViewData]'s [contexts][FilterViewData.contexts]. */
    fun addContext(filterContext: FilterContext) = viewModelScope.launch {
        filterViewData.value.get()?.let { filter ->
            if (filter.contexts.contains(filterContext)) return@launch

            _filterViewData.emit(Ok(filter.copy(contexts = filter.contexts + filterContext)),)
        }
    }

    /** Deletes [filterContext] from [filterViewData]'s [contexts][FilterViewData.contexts]. */
    fun deleteContext(filterContext: FilterContext) = viewModelScope.launch {
        filterViewData.value.get()?.let { filter ->
            if (!filter.contexts.contains(filterContext)) return@launch

            _filterViewData.emit(Ok(filter.copy(contexts = filter.contexts - filterContext)),)
        }
    }

    /** Recalculates validity and dirty state. */
    private fun onChange(filterViewData: FilterViewData) {
        _validationErrors.update { filterViewData.validate() }

        if (filterViewData.expiresIn != -1) {
            _isDirty.value = true
            return
        }

        _isDirty.value = when {
            originalFilter?.title != filterViewData.title -> true
            originalFilter?.contexts != filterViewData.contexts -> true
            originalFilter?.action != filterViewData.action -> true
            originalFilter?.keywords?.toSet() != filterViewData.keywords.toSet() -> true
            else -> false
        }
    }

    /**
     * Saves [filterViewData], either by creating a new filter or updating the
     * existing filter.
     */
    fun saveChanges() = viewModelScope.launch {
        val filterViewData = filterViewData.value.get() ?: return@launch

        _uiResult.send(
            when (uiMode) {
                UiMode.CREATE -> createFilter(filterViewData)
                UiMode.EDIT -> updateFilter(filterViewData)
            }
                .map { UiSuccess.SaveFilter }
        )
    }

    /** Create a new filter from [filterViewData]. */
    private suspend fun createFilter(filterViewData: FilterViewData): Result<Filter, UiError> {
        return filtersRepository.createFilter(NewFilter.from(filterViewData))
            .mapError { SaveFilterError(it) }
    }

    /** Persists the changes to [filterViewData]. */
    private suspend fun updateFilter(filterViewData: FilterViewData): Result<Filter, UiError> {
        return filtersRepository.updateFilter(originalFilter!!, filterViewData.diff(originalFilter!!))
            .mapError { SaveFilterError(it) }
    }

    /** Delete [filterViewData]. */
    fun deleteFilter() = viewModelScope.launch {
        val filterViewData = filterViewData.value.get() ?: return@launch

        // TODO: Check for non-null, or have a type that makes this impossible.
        filtersRepository.deleteFilter(filterViewData.id!!)
            .onSuccess { _uiResult.send(Ok(UiSuccess.DeleteFilter)) }
            .onFailure { _uiResult.send(Err(DeleteFilterError(it))) }
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [EditFilterViewModel], passing optional [filter] and
         * [filterId] parameters.
         *
         * @see EditFilterViewModel
         */
        fun create(filter: Filter?, filterId: String?): EditFilterViewModel
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
