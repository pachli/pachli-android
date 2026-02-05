/* Copyright 2017 Andrew Dawson
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

package app.pachli.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import app.pachli.AudioPlaybackState
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.model.Attachment
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.databinding.FragmentViewVideoBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Plays a video, showing media description if available.
 *
 * UI behaviour:
 *
 * - Fragment starts, media description is visible at top of screen, video starts playing
 * - Media description + toolbar disappears after [ViewMediaFragment.CONTROLS_TIMEOUT]
 * - Tapping shows controls + media description + toolbar, which fade after [ViewMediaFragment.CONTROLS_TIMEOUT]
 * - Tapping pause, or the media description, pauses the video and the controls + media description
 *   remain visible
 */
@UnstableApi
@AndroidEntryPoint
class ViewVideoFragment : ViewMediaFragment() {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val binding by viewBinding(FragmentViewVideoBinding::bind)

    private lateinit var toolbar: View

    private lateinit var mediaPlayerListener: Player.Listener

    private val isAudio: Boolean by unsafeLazy { attachment.type == Attachment.Type.AUDIO }

    private var player: ExoPlayer? = null

    /** The saved seek position, if the fragment is being resumed */
    private var savedSeekPosition: Long = 0

    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    @Volatile
    private var startedTransition = false

    /** The current [AudioPlaybackState]. */
    private val audioPlaybackState: AudioPlaybackState
        get() = viewModel.audioPlaybackState.value

    /** Button to toggle the [audioPlaybackState] state.*/
    private lateinit var toggleMuteButton: ImageButton

    /** Drawable for the "mute" icon. */
    private val drawableMute by unsafeLazy { AppCompatResources.getDrawable(requireContext(), R.drawable.ic_mute_24dp) }

    /** Drawable for the "unmute" icon. */
    private val drawableUnmute by unsafeLazy { AppCompatResources.getDrawable(requireContext(), R.drawable.ic_unmute_24dp) }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)))
    }

    @SuppressLint("PrivateResource", "MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = mediaActivity.toolbar
        val layout = inflater.inflate(R.layout.fragment_view_video, container, false)

        toggleMuteButton = layout.findViewById(R.id.pachli_exo_mute_toggle)

        return layout
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Activity has enabled immersive mode, so just dodge the display cutout.
        binding.mediaDescription.applyWindowInsets(
            left = InsetType.MARGIN,
            top = InsetType.MARGIN,
            right = InsetType.MARGIN,
            bottom = InsetType.MARGIN,
            typeMask = WindowInsetsCompat.Type.displayCutout(),
        )
        binding.videoView.applyWindowInsets(
            left = InsetType.PADDING,
            top = InsetType.PADDING,
            right = InsetType.PADDING,
            bottom = InsetType.PADDING,
            typeMask = WindowInsetsCompat.Type.displayCutout(),
        )

        binding.videoView.controllerShowTimeoutMs = CONTROLS_TIMEOUT.inWholeMilliseconds.toInt()
        binding.videoView.setShowSubtitleButton(true)
        binding.videoView.setRepeatToggleModes(
            RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or
                RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL,
        )

        toggleMuteButton.setOnClickListener {
            player?.let { viewModel.setAudioPlaybackState(audioPlaybackState.toggle(it.volume)) }
        }

        /** Handle single taps, flings, and dragging */
        val touchListener = object : View.OnTouchListener {
            var lastY = 0f

            /** True if the player was playing when the user started touch interactions */
            var wasPlaying: Boolean? = null

            /** Handle taps and flings */
            val simpleGestureDetector = GestureDetector(
                requireContext(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true

                    /** A single tap should show/hide the media description */
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        mediaActivity.onMediaTap()
                        return false
                    }

                    /** A fling up/down should dismiss the fragment */
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (abs(velocityY) > abs(velocityX)) {
                            mediaActionsListener.onMediaDismiss()
                            return true
                        }
                        return false
                    }
                },
            )

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                // Track movement, and scale / translate the video display accordingly
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastY = event.rawY
                } else if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_MOVE) {
                    val diff = event.rawY - lastY
                    if (binding.videoView.translationY != 0f || abs(diff) > 40) {
                        // Save the playing state, if not already saved
                        if (wasPlaying == null) {
                            wasPlaying = player?.isPlaying
                            player?.pause()
                        }

                        binding.videoView.translationY += diff
                        val scale = (-abs(binding.videoView.translationY) / 720 + 1).coerceAtLeast(0.5f)
                        binding.videoView.scaleY = scale
                        binding.videoView.scaleX = scale
                        lastY = event.rawY
                    }
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    if (abs(binding.videoView.translationY) > 180) {
                        wasPlaying = null
                        mediaActionsListener.onMediaDismiss()
                    } else {
                        binding.videoView.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
                        if (wasPlaying == true) player?.play()
                        wasPlaying = null
                    }
                }

                simpleGestureDetector.onTouchEvent(event)

                // Allow the player's normal onTouch handler to run as well (e.g., to show the
                // player controls on tap)
                return false
            }
        }

        mediaPlayerListener = object : Player.Listener {
            @SuppressLint("ClickableViewAccessibility")
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Wait until the media is loaded before accepting taps as we don't want toolbar to
                        // be hidden until then.
                        binding.videoView.setOnTouchListener(touchListener)

                        if (shouldCallMediaReady && !startedTransition) {
                            startedTransition = true
                            mediaActionsListener.onMediaReady()
                        }

                        transitionComplete?.let {
                            viewLifecycleOwner.lifecycleScope.launch {
                                it.await()
                                // Work-around visual glitch by only setting useController here instead
                                // of in the layout.  Otherwise a "blank" controller (just playback
                                // buttons) is displayed while media is loading / preparing, and is then
                                // replaced with the final controller.
                                binding.videoView.useController = true
                                player?.play()
                                // Show controller *after* playback starts, otherwise the show timeout
                                // is ignored.
                                binding.videoView.showController()
                            }
                        }
                    }
                    else -> { /* do nothing */ }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.videoView.keepScreenOn = isPlaying

                if (isAudio) return
                if (isPlaying) {
                    hideToolbarAfterDelay()
                } else {
                    cancelToolbarHide()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = getString(
                    R.string.error_media_playback,
                    error.message ?: error.cause?.message,
                )
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction(app.pachli.core.ui.R.string.action_retry) { player?.prepare() }
                    .show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.audioPlaybackState.collect { playbackState ->
                        when (playbackState) {
                            is AudioPlaybackState.Muted -> player?.volume = 0f
                            is AudioPlaybackState.Unmuted -> player?.volume = playbackState.volume
                        }
                        setToggleMuteButtonContent(toggleMuteButton, playbackState)
                    }
                }
            }
        }

        savedSeekPosition = savedInstanceState?.getLong(SEEK_POSITION) ?: 0
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
            binding.videoView.onResume()
        }
    }

    override fun onResume() {
        super.onResume()

        setToggleMuteButtonContent(toggleMuteButton, audioPlaybackState)

        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (viewModel.isAppBarVisible && !isAudio) {
                hideToolbarAfterDelay()
            }
            binding.videoView.onResume()
        }
    }

    private fun releasePlayer() {
        player?.let {
            savedSeekPosition = it.currentPosition
            if (it.isCommandAvailable(Player.COMMAND_RELEASE)) it.release()
            player = null
            binding.videoView.player = null
        }
    }

    override fun onDetachedFromWindow() {
        player?.pause()
    }

    override fun onAudioBecomingNoisy() {
        player?.pause()
    }

    override fun onPause() {
        super.onPause()

        // If <= API 23 then multi-window mode is not available, so this is a good time to
        // pause everything
        if (Build.VERSION.SDK_INT <= 23) {
            binding.videoView.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()

        // If > API 23 then this might be multi-window, and definitely wasn't paused in onPause,
        // so pause everything now.
        if (Build.VERSION.SDK_INT > 23) {
            binding.videoView.onPause()
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SEEK_POSITION, savedSeekPosition)
    }

    private fun initializePlayer() {
        ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger("${javaClass.simpleName}:ExoPlayer"))
                setMediaItem(MediaItem.fromUri(attachment.url))
                addListener(mediaPlayerListener)
                repeatMode = Player.REPEAT_MODE_ONE

                // Playback is automatically started in onPlaybackStateChanged when
                // any transitions have completed.
                playWhenReady = false
                seekTo(savedSeekPosition)
                if (this@ViewVideoFragment.audioPlaybackState is AudioPlaybackState.Muted) volume = 0f
                prepare()
                player = this
            }

        binding.videoView.player = player

        // Audio-only files might have a preview image. If they do, set it as the artwork
        if (isAudio) {
            attachment.previewUrl?.let { url ->
                Glide.with(this).load(url).into(
                    object : CustomViewTarget<PlayerView, Drawable>(binding.videoView) {
                        override fun onLoadFailed(p0: Drawable?) {
                            /* Do nothing. */
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?,
                        ) {
                            view ?: return
                            view.defaultArtwork = resource
                        }

                        override fun onResourceCleared(p0: Drawable?) {
                            view.defaultArtwork = null
                        }
                    },
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(showingDescription: Boolean) {
        startedTransition = false

        binding.mediaDescription.text = attachment.description
        binding.mediaDescription.visible(showingDescription)
        binding.mediaDescription.movementMethod = ScrollingMovementMethod()

        // Ensure the description is visible over the video
        binding.mediaDescription.elevation = binding.videoView.elevation + 1

        binding.videoView.transitionName = attachment.url

        if (!startedTransition && shouldCallMediaReady) {
            startedTransition = true
            mediaActionsListener.onMediaReady()
        }

        // Clicking the description should play/pause the video
        binding.mediaDescription.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
        }

        binding.videoView.requestFocus()
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        super.onToolbarVisibilityChange(visible)

        if (!userVisibleHint) return
        view ?: return

        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        if (isDescriptionVisible) {
            // If to be visible, need to make visible immediately and animate alpha
            binding.mediaDescription.alpha = 0.0f
            binding.mediaDescription.visible(isDescriptionVisible)
        }

        binding.mediaDescription.animate().alpha(alpha)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animation.removeListener(this)
                        view ?: return
                        binding.mediaDescription.visible(isDescriptionVisible)
                    }
                },
            )
            .start()
    }

    override fun shouldScheduleToolbarHide(): Boolean {
        return (player?.isPlaying == true) && !isAudio
    }

    /**
     * Sets the content ([drawable][ImageButton.drawable] and
     * [contentDescription][ImageButton.contentDescription]) of [button]
     * based on [currentAudioPlaybackState].
     *
     * Note: the icons show the button's **current state**, not the state
     * to change to if the button is clicked.
     */
    private fun setToggleMuteButtonContent(button: ImageButton, currentAudioPlaybackState: AudioPlaybackState) {
        with(button) {
            when (currentAudioPlaybackState) {
                is AudioPlaybackState.Muted -> {
                    setImageDrawable(drawableMute)
                    contentDescription = getString(R.string.action_unmute)
                }

                is AudioPlaybackState.Unmuted -> {
                    setImageDrawable(drawableUnmute)
                    contentDescription = getString(R.string.action_mute)
                }
            }
        }
    }

    companion object {
        private const val SEEK_POSITION = "app.pachli.KEY_SEEK_POSITION"
    }
}
