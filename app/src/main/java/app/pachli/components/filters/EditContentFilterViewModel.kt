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

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.R
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.mapIfNotNull
import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import app.pachli.core.model.NewContentFilter
import app.pachli.core.model.NewContentFilterKeyword
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
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
 * Data to show the content filter in the UI.
 */
data class ContentFilterViewData(
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
    val filterAction: FilterAction = FilterAction.WARN,
    val keywords: List<FilterKeyword> = emptyList(),
) {
    /**
     * @return Set of [ContentFilterValidationError] given the current state of the
     * filter. Empty if there are no validation errors.
     */
    fun validate() = buildSet {
        if (title.isBlank()) add(ContentFilterValidationError.NO_TITLE)
        if (keywords.isEmpty()) add(ContentFilterValidationError.NO_KEYWORDS)
        if (contexts.isEmpty()) add(ContentFilterValidationError.NO_CONTEXT)
    }

    /**
     * Calculates the difference between [contentFilter] and `this`, returning an
     * [ContentFilterEdit] that representes the differences.
     */
    fun diff(contentFilter: ContentFilter): ContentFilterEdit {
        val title: String? = if (title != contentFilter.title) title else null
        val contexts = if (contexts != contentFilter.contexts) contexts else null
        val action = if (filterAction != contentFilter.filterAction) filterAction else null

        // Keywords to delete
        val (keywordsToAdd, existingKeywords) = keywords.partition { it.id == "" }
        val existingKeywordsMap = existingKeywords.associateBy { it.id }

        // Delete any keywords that are in the original list but are not in the existing
        // keywords here.
        val keywordsToDelete = contentFilter.keywords.filter { !existingKeywordsMap.contains(it.id) }

        // Any keywords that are in the original filter and this one, but have different
        // values need to be modified.
        val keywordsToModify = buildList {
            val originalKeywords = contentFilter.keywords.associateBy { it.id }
            originalKeywords.forEach {
                val originalKeyword = it.value

                existingKeywordsMap[originalKeyword.id]?.let { existingKeyword ->
                    if (existingKeyword != originalKeyword) add(existingKeyword)
                }
            }
        }

        return ContentFilterEdit(
            id = contentFilter.id,
            title = title,
            contexts = contexts,
            expiresIn = this.expiresIn,
            filterAction = action,
            keywordsToDelete = keywordsToDelete.ifEmpty { null },
            keywordsToModify = keywordsToModify.ifEmpty { null },
            keywordsToAdd = keywordsToAdd.ifEmpty { null },
        )
    }

    companion object {
        fun from(contentFilter: ContentFilter) = ContentFilterViewData(
            id = contentFilter.id,
            title = contentFilter.title,
            contexts = contentFilter.contexts,
            expiresIn = -1,
            filterAction = contentFilter.filterAction,
            keywords = contentFilter.keywords,
        )
    }
}

fun NewContentFilter.Companion.from(contentFilterViewData: ContentFilterViewData) = NewContentFilter(
    title = contentFilterViewData.title,
    contexts = contentFilterViewData.contexts,
    expiresIn = contentFilterViewData.expiresIn,
    filterAction = contentFilterViewData.filterAction,
    keywords = contentFilterViewData.keywords.map {
        NewContentFilterKeyword(
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
     * @param contentFilterId ID of the filter that could not be loaded.
     */
    data class GetContentFilterError(val contentFilterId: String, override val cause: PachliError) :
        UiError(R.string.error_load_filter_failed_fmt)

    /** Filter could not be saved. */
    data class SaveContentFilterError(override val cause: PachliError) :
        UiError(R.string.error_save_filter_failed_fmt)

    /** Filter could not be deleted. */
    data class DeleteContentFilterError(override val cause: PachliError) :
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
 * If [contentFilter] is non-null it is used to initialise the view model data,
 * [contentFilterId] is ignored, and [uiMode] is [UiMode.EDIT].
 *
 * If [contentFilterId] is non-null is is fetched from the repository, used to
 * initialise the view model, and [uiMode] is [UiMode.EDIT].
 *
 * If both [contentFilter] and [contentFilterId] are null an empty [ContentFilterViewData]
 * is initialised, and [uiMode] is [UiMode.CREATE].
 *
 * @param contentFiltersRepository
 * @param pachliAccountId ID of the account owning the filters
 * @param contentFilter Filter to show
 * @param contentFilterId ID of filter to fetch and show
 */
@HiltViewModel(assistedFactory = EditContentFilterViewModel.Factory::class)
class EditContentFilterViewModel @AssistedInject constructor(
    private val contentFiltersRepository: ContentFiltersRepository,
    @Assisted val pachliAccountId: Long,
    @Assisted val contentFilter: ContentFilter?,
    @Assisted val contentFilterId: String?,
) : ViewModel() {
    /** The original filter before any edits (if provided via [contentFilter] or [contentFilterId]. */
    private var originalContentFilter: ContentFilter? = null

    /** User interface mode. */
    val uiMode = if (contentFilter == null && contentFilterId == null) UiMode.CREATE else UiMode.EDIT

    /** True if the user has made unsaved changes to the filter */
    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    /** True if the filter is valid and can be saved */
    private val _validationErrors = MutableStateFlow(emptySet<ContentFilterValidationError>())
    val validationErrors = _validationErrors.asStateFlow()

    private val _uiResult = Channel<Result<UiSuccess, UiError>>()
    val uiResult = _uiResult.receiveAsFlow()

    private var _contentFilterViewData = MutableSharedFlow<Result<ContentFilterViewData?, UiError.GetContentFilterError>>()
    val contentFilterViewData = _contentFilterViewData
        .onSubscription {
            contentFilter?.let {
                originalContentFilter = it
                emit(Ok(ContentFilterViewData.from(it)))
                return@onSubscription
            }

            emit(
                contentFilterId?.let {
                    originalContentFilter = contentFiltersRepository.getContentFilter(pachliAccountId, contentFilterId)
                    originalContentFilter?.let { Ok(ContentFilterViewData.from(it)) } ?: Ok(ContentFilterViewData())
                } ?: Ok(ContentFilterViewData()),
            )
        }.onEach { it.onSuccess { it?.let { onChange(it) } } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Ok(null),
        )

    /** Reload the filter, if [contentFilterId] is non-null. */
    fun reload() = viewModelScope.launch {
        contentFilterId ?: return@launch _contentFilterViewData.emit(Ok(ContentFilterViewData()))

        originalContentFilter = contentFiltersRepository.getContentFilter(pachliAccountId, contentFilterId)

        _contentFilterViewData.emit(
            originalContentFilter?.let {
                Ok(ContentFilterViewData.from(it))
            } ?: Ok(ContentFilterViewData()),
        )
    }

    /** Adds [keyword] to [contentFilterViewData]. */
    fun addKeyword(keyword: FilterKeyword) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(keywords = it.keywords + keyword)
            },
        )
    }

    /** Deletes [keyword] from [contentFilterViewData]. */
    fun deleteKeyword(keyword: FilterKeyword) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(keywords = it.keywords.filterNot { it == keyword })
            },
        )
    }

    /** Replaces [original] keyword in [contentFilterViewData] with [newKeyword]. */
    fun updateKeyword(original: FilterKeyword, newKeyword: FilterKeyword) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(
                    keywords = it.keywords.map {
                        if (it == original) newKeyword else it
                    },
                )
            },
        )
    }

    /** Replaces [contentFilterViewData]'s [title][ContentFilterViewData.title] with [title]. */
    fun setTitle(title: String) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(title = title)
            },
        )
    }

    /** Replaces [contentFilterViewData]'s [expiresIn][ContentFilterViewData.expiresIn] with [expiresIn]. */
    fun setExpiresIn(expiresIn: Int) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(expiresIn = expiresIn)
            },
        )
    }

    /** Replaces [contentFilterViewData]'s [action][ContentFilterViewData.filterAction] with [filterAction]. */
    fun setAction(filterAction: FilterAction) = viewModelScope.launch {
        _contentFilterViewData.emit(
            contentFilterViewData.value.mapIfNotNull {
                it.copy(filterAction = filterAction)
            },
        )
    }

    /** Adds [filterContext] to [contentFilterViewData]'s [contexts][ContentFilterViewData.contexts]. */
    fun addContext(filterContext: FilterContext) = viewModelScope.launch {
        contentFilterViewData.value.get()?.let { filter ->
            if (filter.contexts.contains(filterContext)) return@launch

            _contentFilterViewData.emit(Ok(filter.copy(contexts = filter.contexts + filterContext)))
        }
    }

    /** Deletes [filterContext] from [contentFilterViewData]'s [contexts][ContentFilterViewData.contexts]. */
    fun deleteContext(filterContext: FilterContext) = viewModelScope.launch {
        contentFilterViewData.value.get()?.let { filter ->
            if (!filter.contexts.contains(filterContext)) return@launch

            _contentFilterViewData.emit(Ok(filter.copy(contexts = filter.contexts - filterContext)))
        }
    }

    /** Recalculates validity and dirty state. */
    private fun onChange(contentFilterViewData: ContentFilterViewData) {
        _validationErrors.update { contentFilterViewData.validate() }

        if (contentFilterViewData.expiresIn != -1) {
            _isDirty.value = true
            return
        }

        _isDirty.value = when {
            originalContentFilter?.title != contentFilterViewData.title -> true
            originalContentFilter?.contexts != contentFilterViewData.contexts -> true
            originalContentFilter?.filterAction != contentFilterViewData.filterAction -> true
            originalContentFilter?.keywords?.toSet() != contentFilterViewData.keywords.toSet() -> true
            else -> false
        }
    }

    /**
     * Saves [contentFilterViewData], either by creating a new filter or updating the
     * existing filter.
     */
    fun saveChanges() = viewModelScope.launch {
        val contentFilterViewData = contentFilterViewData.value.get() ?: return@launch

        _uiResult.send(
            when (uiMode) {
                UiMode.CREATE -> createContentFilter(contentFilterViewData)
                UiMode.EDIT -> updateContentFilter(contentFilterViewData)
            }
                .map { UiSuccess.SaveFilter },
        )
    }

    /** Create a new filter from [contentFilterViewData]. */
    private suspend fun createContentFilter(contentFilterViewData: ContentFilterViewData): Result<ContentFilter, UiError> {
        return contentFiltersRepository.createContentFilter(pachliAccountId, NewContentFilter.from(contentFilterViewData))
            .mapError { UiError.SaveContentFilterError(it) }
    }

    /** Persists the changes to [contentFilterViewData]. */
    private suspend fun updateContentFilter(contentFilterViewData: ContentFilterViewData): Result<ContentFilter, UiError> {
        return contentFiltersRepository.updateContentFilter(pachliAccountId, originalContentFilter!!, contentFilterViewData.diff(originalContentFilter!!))
            .mapError { UiError.SaveContentFilterError(it) }
    }

    /** Delete [contentFilterViewData]. */
    fun deleteContentFilter() = viewModelScope.launch {
        val filterViewData = contentFilterViewData.value.get() ?: return@launch

        // TODO: Check for non-null, or have a type that makes this impossible.
        contentFiltersRepository.deleteContentFilter(pachliAccountId, filterViewData.id!!)
            .onSuccess { _uiResult.send(Ok(UiSuccess.DeleteFilter)) }
            .onFailure { _uiResult.send(Err(UiError.DeleteContentFilterError(it))) }
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates [EditContentFilterViewModel] for [pachliAccountId], passing optional [contentFilter] and
         * [contentFilterId] parameters.
         *
         * @see EditContentFilterViewModel
         */
        fun create(
            pachliAccountId: Long,
            contentFilter: ContentFilter?,
            contentFilterId: String?,
        ): EditContentFilterViewModel
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
