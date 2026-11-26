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
    /** Playback is muted. */
    data object Muted : AudioPlaybackState

    /** Playback is unmuted. */
    data object Unmuted : AudioPlaybackState

    /**
     * Toggle's the [AudioPlaybackState] from [muted][AudioPlaybackState.Muted]
     * to [unmuted][AudioPlaybackState.Unmuted], and vice-versa.
     *
     * @return The new [AudioPlaybackState].
     */
    fun toggle(): AudioPlaybackState = when (this) {
        is Muted -> Unmuted
        is Unmuted -> Muted
    }
}

@HiltViewModel
class ViewMediaViewModel @Inject constructor(
    sharedPreferencesRepository: SharedPreferencesRepository,
) : ViewModel() {
    private val _appBarVisibility = MutableStateFlow(true)

    /** Emits Toolbar visibility changes */
    val appBarVisibility: StateFlow<Boolean> get() = _appBarVisibility.asStateFlow()

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
    val isAppBarVisible: Boolean
        get() = appBarVisibility.value

    /**
     * Toggle the current state of the toolbar's visibility.
     *
     * @return The new visibility
     */
    fun toggleAppBarVisibility() = _appBarVisibility.updateAndGet { !it }

    fun onToolbarMenuInteraction() {
        _toolbarMenuInteraction.tryEmit(Unit)
    }

    /** @see audioPlaybackState */
    private val _audioPlaybackState = MutableStateFlow(
        when (sharedPreferencesRepository.defaultAudioPlayback) {
            DefaultAudioPlayback.UNMUTED -> AudioPlaybackState.Unmuted
            DefaultAudioPlayback.MUTED -> AudioPlaybackState.Muted
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
