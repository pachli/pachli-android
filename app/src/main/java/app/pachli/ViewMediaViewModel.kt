package app.pachli

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

class ViewMediaViewModel : ViewModel() {
    private val _toolbarVisibility = MutableStateFlow(true)

    /** Emits Toolbar visibility changes */
    val toolbarVisibility: StateFlow<Boolean> get() = _toolbarVisibility.asStateFlow()

    private val _toolbarMenuInteraction = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emits whenever a Toolbar menu interaction happens (ex: open overflow menu, item action)
     * Fragments use this to determine whether the toolbar can be hidden after a delay.
     */
    val toolbarMenuInteraction: SharedFlow<Unit> get() = _toolbarMenuInteraction.asSharedFlow()

    /** Convenience getter for the current Toolbar visibility */
    val isToolbarVisible: Boolean
        get() = toolbarVisibility.value

    /**
     * Toggle the current state of the toolbar's visibility.
     *
     * @return The new visibility
     */
    fun toggleToolbarVisibility() = _toolbarVisibility.updateAndGet { !it }

    fun onToolbarMenuInteraction() {
        _toolbarMenuInteraction.tryEmit(Unit)
    }
}
