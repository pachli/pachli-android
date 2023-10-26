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
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import app.pachli.BuildConfig
import app.pachli.R
import app.pachli.ViewMediaActivity
import app.pachli.core.network.model.Attachment
import app.pachli.databinding.FragmentViewVideoBinding
import app.pachli.util.hide
import app.pachli.util.viewBinding
import app.pachli.util.visible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.math.abs

/**
 * Plays a video, showing media description if available.
 *
 * UI behaviour:
 *
 * - Fragment starts, media description is visible at top of screen, video starts playing
 * - Media description + toolbar disappears after CONTROLS_TIMEOUT_MS
 * - Tapping shows controls + media description + toolbar, which fade after CONTROLS_TIMEOUT_MS
 * - Tapping pause, or the media description, pauses the video and the controls + media description
 *   remain visible
 */
@UnstableApi
@AndroidEntryPoint
class ViewVideoFragment : ViewMediaFragment() {
    interface VideoActionsListener {
        fun onDismiss()
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val binding by viewBinding(FragmentViewVideoBinding::bind)

    private lateinit var videoActionsListener: VideoActionsListener
    private lateinit var toolbar: View
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
    }
    private lateinit var mediaActivity: ViewMediaActivity
    private lateinit var mediaPlayerListener: Player.Listener
    private var isAudio = false

    private lateinit var mediaAttachment: Attachment

    private var player: ExoPlayer? = null

    /** The saved seek position, if the fragment is being resumed */
    private var savedSeekPosition: Long = 0

    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)))

        videoActionsListener = context as VideoActionsListener
    }

    @SuppressLint("PrivateResource", "MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mediaActivity = activity as ViewMediaActivity
        toolbar = mediaActivity.toolbar
        val rootView = inflater.inflate(R.layout.fragment_view_video, container, false)

        // Move the controls to the bottom of the screen, with enough bottom margin to clear the seekbar
        val controls = rootView.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_center_controls)
        val layoutParams = controls.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        layoutParams.bottomMargin = rootView.context.resources.getDimension(androidx.media3.ui.R.dimen.exo_styled_bottom_bar_height)
            .toInt()
        controls.layoutParams = layoutParams

        return rootView
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.videoView.controllerShowTimeoutMs = CONTROLS_TIMEOUT_MS

        isAudio = attachment.type == Attachment.Type.AUDIO

        /**
         * Handle single taps, flings, and dragging
         */
        val touchListener = object : View.OnTouchListener {
            var lastY = 0f

            /** The view that contains the playing content */
            // binding.videoView is fullscreen, and includes the controls, so don't use that
            // when scaling in response to the user dragging on the screen
            val contentFrame = binding.videoView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)

            /** Handle taps and flings */
            val simpleGestureDetector = GestureDetectorCompat(
                requireContext(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true

                    /** A single tap should show/hide the media description */
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        mediaActivity.onPhotoTap()
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
                            videoActionsListener.onDismiss()
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
                    if (contentFrame.translationY != 0f || abs(diff) > 40) {
                        contentFrame.translationY += diff
                        val scale = (-abs(contentFrame.translationY) / 720 + 1).coerceAtLeast(0.5f)
                        contentFrame.scaleY = scale
                        contentFrame.scaleX = scale
                        lastY = event.rawY
                    }
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    if (abs(contentFrame.translationY) > 180) {
                        videoActionsListener.onDismiss()
                    } else {
                        contentFrame.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
                    }
                }

                simpleGestureDetector.onTouchEvent(event)

                // Allow the player's normal onTouch handler to run as well (e.g., to show the
                // player controls on tap)
                return false
            }
        }

        mediaPlayerListener = object : Player.Listener {
            @SuppressLint("ClickableViewAccessibility", "SyntheticAccessor")
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Wait until the media is loaded before accepting taps as we don't want toolbar to
                        // be hidden until then.
                        binding.videoView.setOnTouchListener(touchListener)

                        binding.progressBar.hide()
                        binding.videoView.useController = true
                    }
                    else -> { /* do nothing */ }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isAudio) return
                if (isPlaying) {
                    hideToolbarAfterDelay()
                } else {
                    handler.removeCallbacks(hideToolbar)
                }
            }

            @SuppressLint("SyntheticAccessor")
            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.hide()
                val message = getString(
                    R.string.error_media_playback,
                    error.cause?.message ?: error.message,
                )
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    .setTextMaxLines(10)
                    .setAction(R.string.action_retry) { player?.prepare() }
                    .show()
            }
        }

        savedSeekPosition = savedInstanceState?.getLong(SEEK_POSITION) ?: 0

        mediaAttachment = attachment
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

        finalizeViewSetup()

        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (mediaActivity.isToolbarVisible && !isAudio) {
                hideToolbarAfterDelay()
            }
            binding.videoView.onResume()
        }
    }

    private fun releasePlayer() {
        player?.let {
            savedSeekPosition = it.currentPosition
            it.release()
            player = null
            binding.videoView.player = null
        }
    }

    override fun onPause() {
        super.onPause()

        // If <= API 23 then multi-window mode is not available, so this is a good time to
        // pause everything
        if (Build.VERSION.SDK_INT <= 23) {
            binding.videoView.onPause()
            releasePlayer()
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onStop() {
        super.onStop()

        // If > API 23 then this might be multi-window, and definitely wasn't paused in onPause,
        // so pause everything now.
        if (Build.VERSION.SDK_INT > 23) {
            binding.videoView.onPause()
            releasePlayer()
            handler.removeCallbacks(hideToolbar)
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
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger("$TAG:ExoPlayer"))
                setMediaItem(MediaItem.fromUri(mediaAttachment.url))
                addListener(mediaPlayerListener)
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                seekTo(savedSeekPosition)
                prepare()
                player = this
            }

        binding.videoView.player = player

        // Audio-only files might have a preview image. If they do, set it as the artwork
        if (isAudio) {
            mediaAttachment.previewUrl?.let { url ->
                Glide.with(this).load(url).into(
                    object : CustomTarget<Drawable>() {
                        @SuppressLint("SyntheticAccessor")
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?,
                        ) {
                            view ?: return
                            binding.videoView.defaultArtwork = resource
                        }

                        @SuppressLint("SyntheticAccessor")
                        override fun onLoadCleared(placeholder: Drawable?) {
                            view ?: return
                            binding.videoView.defaultArtwork = null
                        }
                    },
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(
        showingDescription: Boolean,
    ) {
        binding.mediaDescription.text = attachment.description
        binding.mediaDescription.visible(showingDescription)
        binding.mediaDescription.movementMethod = ScrollingMovementMethod()

        // Ensure the description is visible over the video
        binding.mediaDescription.elevation = binding.videoView.elevation + 1

        binding.videoView.transitionName = attachment.url

        // Clicking the description should play/pause the video
        binding.mediaDescription.setOnClickListener {
            if (binding.videoView.player?.isPlaying == true) {
                binding.videoView.player?.pause()
            } else {
                binding.videoView.player?.play()
            }
        }

        binding.videoView.requestFocus()

        if (requireArguments().getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay() {
        handler.postDelayed(hideToolbar, CONTROLS_TIMEOUT_MS.toLong())
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (!userVisibleHint) {
            return
        }

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
                    @SuppressLint("SyntheticAccessor")
                    override fun onAnimationEnd(animation: Animator) {
                        view ?: return
                        binding.mediaDescription.visible(isDescriptionVisible)
                        animation.removeListener(this)
                    }
                },
            )
            .start()

        if (visible && (binding.videoView.player?.isPlaying == true) && !isAudio) {
            hideToolbarAfterDelay()
        } else {
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onTransitionEnd() { }

    companion object {
        private const val TAG = "ViewVideoFragment"
        private const val CONTROLS_TIMEOUT_MS = 2000 // Consistent with YouTube player
        private const val SEEK_POSITION = "seekPosition"
    }
}
