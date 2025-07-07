package app.pachli

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.preferences.DefaultAudioPlayback
import app.pachli.core.preferences.SharedPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * State of playback volume.
 *
 * @see Muted
 * @see Unmuted
 */
internal sealed interface AudioPlaybackState {
    /**
     * Playback is muted.
     *
     * @property previous The volume before muting.
     */
    data class Muted(val previous: Float) : AudioPlaybackState

    /**
     * Playback is unmuted.
     *
     * @property volume The current volume.
     */
    data class Unmuted(val volume: Float) : AudioPlaybackState

    /**
     * Toggle's the [AudioPlaybackState] from [muted][AudioPlaybackState.Muted]
     * to [unmuted][AudioPlaybackState.Unmuted], and vice-versa.
     *
     * @param currentVolume The current volume, saved when muting, restored when unmuting.
     * @return The new [AudioPlaybackState].
     */
    fun toggle(currentVolume: Float): AudioPlaybackState {
        val newAudioPlaybackState = when (this) {
            is Muted -> Unmuted(previous)
            is Unmuted -> Muted(previous = currentVolume)
        }
        return newAudioPlaybackState
    }
}

@HiltViewModel
class ViewMediaViewModel @Inject constructor(
    private val sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
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

    /** @see audioPlaybackState */
    private val _audioPlaybackState = MutableStateFlow(
        when (sharedPreferencesRepository.defaultAudioPlayback) {
            DefaultAudioPlayback.UNMUTED -> AudioPlaybackState.Unmuted(1f)
            DefaultAudioPlayback.MUTED -> AudioPlaybackState.Muted(1f)
        },
    )

    /**
     * Flow of changes to [AudioPlaybackState]. Starts with the user's preferred
     * state, and updates on calls to [setAudioPlaybackState].
     */
    internal val audioPlaybackState = _audioPlaybackState.asStateFlow()

    /** Sets a new [AudioPlaybackState]. */
    internal fun setAudioPlaybackState(playback: AudioPlaybackState) = viewModelScope.launch { _audioPlaybackState.value = playback }
}
