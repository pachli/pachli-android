package app.pachli.components.filters

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.ContentFiltersRepository
import app.pachli.core.model.ContentFilter
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContentFiltersViewModel @Inject constructor(
    private val contentFiltersRepository: ContentFiltersRepository,
) : ViewModel() {

    enum class LoadingState {
        INITIAL,
        LOADING,
        LOADED,
        ERROR_NETWORK,
        ERROR_OTHER,
    }

    data class State(val contentFilters: List<ContentFilter>, val loadingState: LoadingState)

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(State(emptyList(), LoadingState.INITIAL))

    fun load() {
        this@ContentFiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.LOADING)

        viewModelScope.launch {
            contentFiltersRepository.contentFilters.collect { result ->
                result.onSuccess { filters ->
                    this@ContentFiltersViewModel._state.update { State(filters?.contentFilters.orEmpty(), LoadingState.LOADED) }
                }
                    .onFailure {
                        // TODO: There's an ERROR_NETWORK state to maybe consider here. Or get rid of
                        // that and do proper error handling.
                        this@ContentFiltersViewModel._state.update {
                            it.copy(loadingState = LoadingState.ERROR_OTHER)
                        }
                    }
            }
        }
    }

    fun deleteContentFilter(contentFilter: ContentFilter, parent: View) {
        viewModelScope.launch {
            contentFiltersRepository.deleteContentFilter(contentFilter.id)
                .onSuccess {
                    this@ContentFiltersViewModel._state.value = State(this@ContentFiltersViewModel._state.value.contentFilters.filter { it.id != contentFilter.id }, LoadingState.LOADED)
                }
                .onFailure {
                    Snackbar.make(parent, "Error deleting filter '${contentFilter.title}'", Snackbar.LENGTH_SHORT).show()
                }
        }
    }
}
