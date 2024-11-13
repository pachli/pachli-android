package app.pachli.components.filters

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ContentFilters
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ContentFilterVersion
import app.pachli.core.ui.OperationCounter
import com.github.michaelbull.result.onFailure
import com.google.android.material.snackbar.Snackbar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ContentFiltersViewModel.Factory::class)
class ContentFiltersViewModel @AssistedInject constructor(
    private val accountManager: AccountManager,
    private val contentFiltersRepository: ContentFiltersRepository,
    @Assisted val pachliAccountId: Long,
) : ViewModel() {

    val contentFilters = flow {
        accountManager.getPachliAccountFlow(pachliAccountId).filterNotNull()
            .distinctUntilChangedBy { it.contentFilters }
            .collect { emit(it.contentFilters) }
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ContentFilters(contentFilters = emptyList(), version = ContentFilterVersion.V1),
        )

    private val operationCounter = OperationCounter()
    val operationCount = operationCounter.count

    fun refreshContentFilters() = viewModelScope.launch {
        operationCounter {
            contentFiltersRepository.refresh(pachliAccountId)
        }
    }

    fun deleteContentFilter(contentFilter: ContentFilter, parent: View) {
        viewModelScope.launch {
            operationCounter {
                contentFiltersRepository.deleteContentFilter(pachliAccountId, contentFilter.id)
                    .onFailure {
                        Snackbar.make(parent, "Error deleting filter '${contentFilter.title}'", Snackbar.LENGTH_SHORT).show()
                    }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        /** Creates [ContentFiltersViewModel] with [pachliAccountId] as the active account. */
        fun create(pachliAccountId: Long): ContentFiltersViewModel
    }
}
