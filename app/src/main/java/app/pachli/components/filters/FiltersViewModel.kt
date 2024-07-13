package app.pachli.components.filters

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.model.Filter
import app.pachli.core.data.repository.FiltersRepository
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
class FiltersViewModel @Inject constructor(
    private val filtersRepository: FiltersRepository,
) : ViewModel() {

    enum class LoadingState {
        INITIAL,
        LOADING,
        LOADED,
        ERROR_NETWORK,
        ERROR_OTHER,
    }

    data class State(val filters: List<Filter>, val loadingState: LoadingState)

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(State(emptyList(), LoadingState.INITIAL))

    fun load() {
        this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.LOADING)

        viewModelScope.launch {
            filtersRepository.filters.collect { result ->
                result.onSuccess { filters ->
                    this@FiltersViewModel._state.update { State(filters?.filters.orEmpty(), LoadingState.LOADED) }
                }
                    .onFailure {
                        // TODO: There's an ERROR_NETWORK state to maybe consider here. Or get rid of
                        // that and do proper error handling.
                        this@FiltersViewModel._state.update {
                            it.copy(loadingState = LoadingState.ERROR_OTHER)
                        }
                    }
            }
        }
    }

    fun deleteFilter(filter: Filter, parent: View) {
        viewModelScope.launch {
            filtersRepository.deleteFilter(filter.id)
                .onSuccess {
                    this@FiltersViewModel._state.value = State(this@FiltersViewModel._state.value.filters.filter { it.id != filter.id }, LoadingState.LOADED)
                }
                .onFailure {
                    Snackbar.make(parent, "Error deleting filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                }
        }
    }
}
